
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
                    return context.parsed.y + ' %';
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
            min: 0,
            max: 100,
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

function createGradient(context, colorHex) {
    const gradient = context.createLinearGradient(0, 0, 0, 300);
    gradient.addColorStop(0, colorHex + '80');
    gradient.addColorStop(1, colorHex + '00');
    return gradient;
}

const ramDetailedChartContext = document.getElementById('ramDetailedChart').getContext('2d');
const ramDetailedChartGradient = createGradient(ramDetailedChartContext, '#f59e0b');

const ramDetailedChart = new Chart(ramDetailedChartContext, {
    type: 'line',
    data: {
        labels: [],
        datasets: [{
            label: 'RAM',
            data: [],
            borderColor: '#f59e0b',
            backgroundColor: ramDetailedChartGradient,
            fill: true
        }]
    },
    options: commonOptions
});

function addDataToChart(chart, label, value) {
    chart.data.labels.push(label);
    chart.data.datasets.forEach(function(dataset) {
        dataset.data.push(value);
    });

    if (chart.data.labels.length > 60) {
        chart.data.labels.shift();
        chart.data.datasets.forEach(function(dataset) {
            dataset.data.shift();
        });
    }
    chart.update();
}

var ramDetailsMessageCount = 0;
var ramDetailsSource = '';

function applyRamDetailsData(metricsData) {
    if (!metricsData) return;
    var currentTimeLabel = new Date().toLocaleTimeString();
    var ramUsageValue = metricsData.ramInt != null ? metricsData.ramInt : (parseFloat(metricsData.ramPercent) || 0);
    addDataToChart(ramDetailedChart, currentTimeLabel, ramUsageValue);

    var ramDetailedValueElement = document.getElementById('ramDetailedValue');
    if (ramDetailedValueElement) {
        ramDetailedValueElement.innerText = (metricsData.ramPercent != null ? metricsData.ramPercent : ramUsageValue) + '%';
    }

    var ramPercentEl = document.getElementById('ramPercentValue');
    if (ramPercentEl) {
        ramPercentEl.innerText = metricsData.ramPercent != null ? metricsData.ramPercent : ramUsageValue;
    }
    if (metricsData.ramTotal != null) {
        var ramTotalEl = document.getElementById('ramTotalValue');
        if (ramTotalEl) ramTotalEl.innerText = metricsData.ramTotal;
    }
    if (metricsData.ramUsado != null) {
        var ramUsedEl = document.getElementById('ramUsedValue');
        if (ramUsedEl) ramUsedEl.innerText = metricsData.ramUsado;
    }
    if (metricsData.ramLivre != null) {
        var ramFreeEl = document.getElementById('ramFreeValue');
        if (ramFreeEl) ramFreeEl.innerText = metricsData.ramLivre;
    }

    ramDetailsMessageCount++;
    if (ramDetailsMessageCount === 1 || ramDetailsMessageCount % 30 === 0) {
        console.log('[ServerDash RAM Details]', ramDetailsSource, '- métrica recebida #' + ramDetailsMessageCount);
    }
}

function subscribeRamDetails(stompClient) {
    ramDetailsSource = 'WebSocket';
    ramDetailsMessageCount = 0;
    console.log('[ServerDash RAM Details] Inscrito em /topic/public (WebSocket)');
    stompClient.subscribe('/topic/public', function(message) {
        applyRamDetailsData(JSON.parse(message.body));
    });
}

function startRamDetailsPolling() {
    ramDetailsSource = 'Polling';
    ramDetailsMessageCount = 0;
    console.log('[ServerDash RAM Details] Fallback: iniciando polling GET /api/metrics/public a cada 1s');
    setInterval(function() {
        fetch('/api/metrics/public')
            .then(function(response) {
                if (!response.ok) console.warn('[ServerDash RAM Details] Polling falhou:', response.status);
                return response.ok ? response.json() : null;
            })
            .then(applyRamDetailsData)
            .catch(function(error) { console.warn('[ServerDash RAM Details] Polling erro:', error); });
    }, 1000);
}

StompReconnect.connect({
    onConnect: subscribeRamDetails,
    onFallbackToPolling: startRamDetailsPolling,
    maxReconnectAttempts: 5,
    heartbeat: { incoming: 10000, outgoing: 10000 }
});
