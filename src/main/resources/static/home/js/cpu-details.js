
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

const cpuDetailedChartContext = document.getElementById('cpuDetailedChart').getContext('2d');
const cpuDetailedChartGradient = createGradient(cpuDetailedChartContext, '#3b82f6');

const cpuDetailedChart = new Chart(cpuDetailedChartContext, {
    type: 'line',
    data: {
        labels: [],
        datasets: [{
            label: 'CPU',
            data: [],
            borderColor: '#3b82f6',
            backgroundColor: cpuDetailedChartGradient,
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

var cpuDetailsMessageCount = 0;
var cpuDetailsSource = '';

function applyCpuDetailsData(metricsData) {
    if (!metricsData) return;
    var currentTimeLabel = new Date().toLocaleTimeString();
    var cpuUsageValue = metricsData.cpuInt != null ? metricsData.cpuInt : (parseFloat(metricsData.cpuPercent) || 0);
    addDataToChart(cpuDetailedChart, currentTimeLabel, cpuUsageValue);

    var cpuDetailedValueElement = document.getElementById('cpuDetailedValue');
    if (cpuDetailedValueElement) {
        cpuDetailedValueElement.innerText = (metricsData.cpuPercent != null ? metricsData.cpuPercent : cpuUsageValue) + '%';
    }

    cpuDetailsMessageCount++;
    if (cpuDetailsMessageCount === 1 || cpuDetailsMessageCount % 30 === 0) {
        console.log('[ServerDash CPU Details]', cpuDetailsSource, '- métrica recebida #' + cpuDetailsMessageCount);
    }
}

function subscribeCpuDetails(stompClient) {
    cpuDetailsSource = 'WebSocket';
    cpuDetailsMessageCount = 0;
    console.log('[ServerDash CPU Details] Inscrito em /topic/public (WebSocket)');
    stompClient.subscribe('/topic/public', function(message) {
        applyCpuDetailsData(JSON.parse(message.body));
    });
}

function startCpuDetailsPolling() {
    cpuDetailsSource = 'Polling';
    cpuDetailsMessageCount = 0;
    console.log('[ServerDash CPU Details] Fallback: iniciando polling GET /api/metrics/public a cada 1s');
    setInterval(function() {
        fetch('/api/metrics/public')
            .then(function(response) {
                if (!response.ok) console.warn('[ServerDash CPU Details] Polling falhou:', response.status);
                return response.ok ? response.json() : null;
            })
            .then(applyCpuDetailsData)
            .catch(function(error) { console.warn('[ServerDash CPU Details] Polling erro:', error); });
    }, 1000);
}

StompReconnect.connect({
    onConnect: subscribeCpuDetails,
    onFallbackToPolling: startCpuDetailsPolling,
    maxReconnectAttempts: 5,
    heartbeat: { incoming: 10000, outgoing: 10000 }
});
