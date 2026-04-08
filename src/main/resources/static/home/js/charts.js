
Chart.defaults.color = '#64748b';
Chart.defaults.font.family = "'Inter', sans-serif";

const commonOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
        legend: { display: false },
        tooltip: {
            backgroundColor: '#1e293b',
            titleColor: '#f1f5f9',
            bodyColor: '#cbd5e1',
            borderColor: '#334155',
            borderWidth: 1,
            padding: 10,
            displayColors: false,
            callbacks: {
                label: function(context) {
                    return context.parsed.y + (context.dataset.label.includes('Temp') ? ' °C' : ' %');
                }
            }
        }
    },
    scales: {
        x: {
            display: false,
            grid: { display: false }
        },
        y: {
            beginAtZero: true,
            grid: {
                color: '#334155',
                drawBorder: false,
                tickLength: 0
            },
            border: { display: false }
        }
    },
    elements: {
        point: {
            radius: 0,
            hitRadius: 10,
            hoverRadius: 4
        },
        line: {
            borderWidth: 2,
            tension: 0.4
        }
    },
    animation: { duration: 0 }
};

function createGradient(ctx, colorHex) {
    const gradient = ctx.createLinearGradient(0, 0, 0, 300);
    gradient.addColorStop(0, colorHex + '80');
    gradient.addColorStop(1, colorHex + '00');
    return gradient;
}

const ctxCpu = document.getElementById('cpuChart').getContext('2d');
const cpuGradient = createGradient(ctxCpu, '#3b82f6');

const cpuChart = new Chart(ctxCpu, {
    type: 'line',
    data: {
        labels: [],
        datasets: [{
            label: 'CPU',
            data: [],
            borderColor: '#3b82f6',
            backgroundColor: cpuGradient,
            fill: true
        }]
    },
    options: {
        ...commonOptions,
        scales: {
            ...commonOptions.scales,
            y: { ...commonOptions.scales.y, max: 100 }
        }
    }
});

const ctxRam = document.getElementById('ramChart').getContext('2d');
const ramGradient = createGradient(ctxRam, '#8b5cf6');

const ramChart = new Chart(ctxRam, {
    type: 'line',
    data: {
        labels: [],
        datasets: [{
            label: 'RAM',
            data: [],
            borderColor: '#8b5cf6',
            backgroundColor: ramGradient,
            fill: true
        }]
    },
    options: {
        ...commonOptions,
        scales: {
            ...commonOptions.scales,
            y: { ...commonOptions.scales.y, max: 100 }
        }
    }
});

const ctxTemp = document.getElementById('tempChart').getContext('2d');
const tempGradient = createGradient(ctxTemp, '#f59e0b');

const tempChart = new Chart(ctxTemp, {
    type: 'line',
    data: {
        labels: [],
        datasets: [{
            label: 'Temp',
            data: [],
            borderColor: '#f59e0b',
            backgroundColor: tempGradient,
            fill: true
        }]
    },
    options: commonOptions
});

let isRealTimeModeActive = true;
let webSocketSubscription = null;

var _chartsMsgCount = 0;
var _chartsSource = '';

var MAX_HISTORICAL_POINTS = 2000;

function downsampleMetrics(metricsArray, maxPoints) {
    if (!Array.isArray(metricsArray) || metricsArray.length <= maxPoints) {
        return metricsArray;
    }
    var step = Math.ceil(metricsArray.length / maxPoints);
    var out = [];
    for (var i = 0; i < metricsArray.length; i += step) {
        out.push(metricsArray[i]);
    }
    return out;
}

function addData(chartInstance, timeLabel, numericalData) {
    chartInstance.data.labels.push(timeLabel);
    chartInstance.data.datasets.forEach(function(datasetItem) {
        datasetItem.data.push(numericalData);
    });

    if (isRealTimeModeActive && chartInstance.data.labels.length > 60) {
        chartInstance.data.labels.shift();
        chartInstance.data.datasets.forEach(function(datasetItem) {
            datasetItem.data.shift();
        });
    }
    chartInstance.update();
}

function applyChartsData(data) {
    if (!data) return;
    var now = new Date().toLocaleTimeString();
    addData(cpuChart, now, data.cpuInt);
    addData(ramChart, now, data.ramInt);
    addData(tempChart, now, data.cpuTempInt);
    var cpuVal = document.getElementById('cpuValue');
    if (cpuVal) cpuVal.innerText = data.cpuPercent + '%';
    var ramVal = document.getElementById('ramValue');
    if (ramVal) ramVal.innerText = data.ramPercent + '%';
    var tempVal = document.getElementById('tempValue');
    if (tempVal) tempVal.innerText = data.cpuTemp + '°C';

    _chartsMsgCount++;
    if (_chartsMsgCount === 1 || _chartsMsgCount % 30 === 0) {
        console.log('[ServerDash Charts]', _chartsSource, '- métrica recebida #' + _chartsMsgCount);
    }
}

function subscribeCharts(stompClient) {
    _chartsSource = 'WebSocket';
    _chartsMsgCount = 0;
    console.log('[ServerDash Charts] Inscrito em /topic/public (WebSocket)');
    webSocketSubscription = stompClient.subscribe('/topic/public', function (messagePayload) {
        if (isRealTimeModeActive) {
            applyChartsData(JSON.parse(messagePayload.body));
        }
    });
}

function startChartsPolling() {
    _chartsSource = 'Polling';
    _chartsMsgCount = 0;
    console.log('[ServerDash Charts] Fallback: iniciando polling GET /api/metrics/public a cada 1s');
    StompReconnect.startPollingFallback(1000, function() {
        fetch('/api/metrics/public', { credentials: 'same-origin' })
            .then(function(res) {
                if (!res.ok) console.warn('[ServerDash Charts] Polling falhou:', res.status);
                return res.ok ? res.json() : null;
            })
            .then(applyChartsData)
            .catch(function(err) { console.warn('[ServerDash Charts] Polling erro:', err); });
    });
}

StompReconnect.connect({
    onConnect: subscribeCharts,
    onFallbackToPolling: startChartsPolling,
    maxReconnectAttempts: 5,
    heartbeat: { incoming: 10000, outgoing: 10000 }
});

function clearChartData(chartInstance) {
    chartInstance.data.labels = [];
    chartInstance.data.datasets.forEach(function(datasetItem) {
        datasetItem.data = [];
    });
    chartInstance.update();
}

function loadHistoricalData(hoursToRetrieve) {
    isRealTimeModeActive = false;

    if (webSocketSubscription !== null) {
        webSocketSubscription.unsubscribe();
        webSocketSubscription = null;
    }

    clearChartData(cpuChart);
    clearChartData(ramChart);
    clearChartData(tempChart);

    var url = '/api/metrics/history?hoursToRetrieve=' + hoursToRetrieve;
    console.log('[ServerDash Charts] loadHistoricalData: requisitando', url);

    fetch(url, { credentials: 'same-origin' })
        .then(function(responseObject) {
            console.log('[ServerDash Charts] loadHistoricalData: status=', responseObject.status, responseObject.statusText);
            if (!responseObject.ok) {
                return responseObject.text().then(function(text) {
                    throw new Error('HTTP ' + responseObject.status + ': ' + text);
                });
            }
            return responseObject.json();
        })
        .then(function(metricsArray) {
            console.log('[ServerDash Charts] loadHistoricalData: recebidos', Array.isArray(metricsArray) ? metricsArray.length : 'nao-e-array', 'registros');
            if (!Array.isArray(metricsArray)) {
                console.error('[ServerDash Charts] loadHistoricalData: resposta nao e array:', typeof metricsArray, metricsArray);
                return;
            }
            var sampled = downsampleMetrics(metricsArray, MAX_HISTORICAL_POINTS);
            if (sampled.length < metricsArray.length) {
                console.log('[ServerDash Charts] loadHistoricalData: downsampling', metricsArray.length, '->', sampled.length, 'pontos');
            }
            if (sampled.length > 0) {
                console.log('[ServerDash Charts] loadHistoricalData: primeiro registro (amostra)', JSON.stringify(sampled[0]));
            }
            sampled.forEach(function(metricRecord) {
                var recordedTime = new Date(metricRecord.recordedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

                cpuChart.data.labels.push(recordedTime);
                cpuChart.data.datasets[0].data.push(metricRecord.cpuUsagePercentage);

                ramChart.data.labels.push(recordedTime);
                ramChart.data.datasets[0].data.push(metricRecord.ramUsagePercentage);

                tempChart.data.labels.push(recordedTime);
                tempChart.data.datasets[0].data.push(metricRecord.cpuTemperature);
            });

            cpuChart.update();
            ramChart.update();
            tempChart.update();
            console.log('[ServerDash Charts] loadHistoricalData: graficos atualizados com', sampled.length, 'pontos');
        })
        .catch(function(errorObject) {
            console.error('[ServerDash Charts] loadHistoricalData: erro ao buscar historico', errorObject);
        });
}

document.getElementById('buttonRealTime').addEventListener('click', function() {
    this.classList.add('active');
    document.getElementById('buttonLastHour').classList.remove('active');
    document.getElementById('buttonLastDay').classList.remove('active');

    isRealTimeModeActive = true;
    clearChartData(cpuChart);
    clearChartData(ramChart);
    clearChartData(tempChart);

    location.reload();
});

document.getElementById('buttonLastHour').addEventListener('click', function() {
    this.classList.add('active');
    document.getElementById('buttonRealTime').classList.remove('active');
    document.getElementById('buttonLastDay').classList.remove('active');
    loadHistoricalData(1);
});

document.getElementById('buttonLastDay').addEventListener('click', function() {
    this.classList.add('active');
    document.getElementById('buttonRealTime').classList.remove('active');
    document.getElementById('buttonLastHour').classList.remove('active');
    loadHistoricalData(24);
});

document.getElementById('buttonLastWeek').addEventListener('click', function() {
    this.classList.add('active');
    document.getElementById('buttonRealTime').classList.remove('active');
    document.getElementById('buttonLastHour').classList.remove('active');
    loadHistoricalData(168);
});
