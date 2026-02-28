
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
    gradient.addColorStop(0, colorHex + '80'); // 50% opacity
    gradient.addColorStop(1, colorHex + '00'); // 0% opacity
    return gradient;
}

const ctxCpu = document.getElementById('cpuChart').getContext('2d');
const cpuGradient = createGradient(ctxCpu, '#3b82f6'); // Blue

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
const ramGradient = createGradient(ctxRam, '#8b5cf6'); // Violet

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
const tempGradient = createGradient(ctxTemp, '#f59e0b'); // Amber

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

var _chartsMsgCount = 0;
var _chartsSource = '';

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
    stompClient.subscribe('/topic/public', function (message) {
        applyChartsData(JSON.parse(message.body));
    });
}

function startChartsPolling() {
    _chartsSource = 'Polling';
    _chartsMsgCount = 0;
    console.log('[ServerDash Charts] Fallback: iniciando polling GET /api/metrics/public a cada 1s');
    setInterval(function() {
        fetch('/api/metrics/public')
            .then(function(res) {
                if (!res.ok) console.warn('[ServerDash Charts] Polling falhou:', res.status);
                return res.ok ? res.json() : null;
            })
            .then(applyChartsData)
            .catch(function(err) { console.warn('[ServerDash Charts] Polling erro:', err); });
    }, 1000);
}

StompReconnect.connect({
    onConnect: subscribeCharts,
    onFallbackToPolling: startChartsPolling,
    maxReconnectAttempts: 5,
    heartbeat: { incoming: 10000, outgoing: 10000 }
});

let isRealTimeModeActive = true;
let webSocketSubscription = null;

function clearChartData(chartInstance) {
    chartInstance.data.labels = [];
    chartInstance.data.datasets.forEach(function(datasetItem) {
        datasetItem.data = [];
    });
    chartInstance.update();
}

function loadHistoricalData(hoursToRetrieve) {
    isRealTimeModeActive = false;

    // Desconecta o tempo real para não interferir no histórico
    if (webSocketSubscription !== null) {
        webSocketSubscription.unsubscribe();
        webSocketSubscription = null;
    }

    clearChartData(cpuChart);
    clearChartData(ramChart);
    clearChartData(tempChart);

    var url = '/api/metrics/history?hoursToRetrieve=' + hoursToRetrieve;
    console.log('[ServerDash Charts] loadHistoricalData: requisitando', url);

    fetch(url)
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
            if (metricsArray.length > 0) {
                console.log('[ServerDash Charts] loadHistoricalData: primeiro registro (amostra)', JSON.stringify(metricsArray[0]));
            }
            metricsArray.forEach(function(metricRecord) {
                // Converte a data do formato ISO para local
                let recordedTime = new Date(metricRecord.recordedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

                // Adiciona os dados contornando o limite de 60 segundos
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
            console.log('[ServerDash Charts] loadHistoricalData: graficos atualizados com', metricsArray.length, 'pontos');
        })
        .catch(function(errorObject) {
            console.error('[ServerDash Charts] loadHistoricalData: erro ao buscar historico', errorObject);
        });
}

// Configuração dos Botões
document.getElementById('buttonRealTime').addEventListener('click', function() {
    this.classList.add('active');
    document.getElementById('buttonLastHour').classList.remove('active');
    document.getElementById('buttonLastDay').classList.remove('active');

    isRealTimeModeActive = true;
    clearChartData(cpuChart);
    clearChartData(ramChart);
    clearChartData(tempChart);

    // Reconecta ao WebSocket (Você pode chamar sua função existente StompReconnect.connect)
    location.reload(); // Forma mais simples de resetar o WebSocket e as variáveis locais
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

// Atualize sua função subscribeCharts existente para salvar a inscrição
function subscribeCharts(stompClient) {
    _chartsSource = 'WebSocket';
    _chartsMsgCount = 0;
    console.log('[ServerDash Charts] Inscrito em /topic/public (WebSocket)');

    // Salva a inscrição na variável
    webSocketSubscription = stompClient.subscribe('/topic/public', function (messagePayload) {
        if (isRealTimeModeActive) {
            applyChartsData(JSON.parse(messagePayload.body));
        }
    });
}
