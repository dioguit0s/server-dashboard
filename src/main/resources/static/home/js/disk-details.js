
Chart.defaults.color = 'rgba(255,255,255,0.5)';
Chart.defaults.font.family = "'JetBrains Mono', ui-monospace, monospace";

var initialDiskInt = 0;
var diskChartContainer = document.getElementById('diskChartContainer');
if (diskChartContainer && diskChartContainer.getAttribute('data-disk-int') != null) {
    initialDiskInt = parseInt(diskChartContainer.getAttribute('data-disk-int'), 10) || 0;
}

var usedPercent = Math.min(100, Math.max(0, initialDiskInt));
var freePercent = 100 - usedPercent;

const diskPieCtx = document.getElementById('diskPieChart').getContext('2d');
const diskPieChart = new Chart(diskPieCtx, {
    type: 'pie',
    data: {
        labels: ['Usado', 'Livre'],
        datasets: [{
            data: [usedPercent, freePercent],
            backgroundColor: ['#0089ff', 'rgba(255,255,255,0.12)'],
            borderColor: ['#000000', '#000000'],
            borderWidth: 2
        }]
    },
    options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
            legend: {
                display: true,
                position: 'bottom',
                labels: {
                    color: 'rgba(255,255,255,0.6)',
                    padding: 16
                }
            },
            tooltip: {
                backgroundColor: '#000000',
                titleColor: '#ffffff',
                bodyColor: 'rgba(255,255,255,0.6)',
                borderColor: 'rgba(255,255,255,0.1)',
                borderWidth: 1,
                padding: 10,
                callbacks: {
                    label: function(context) {
                        var value = context.parsed;
                        var total = context.dataset.data.reduce(function(a, b) { return a + b; }, 0);
                        var pct = total > 0 ? Math.round((value / total) * 100) : 0;
                        return context.label + ': ' + pct + '%';
                    }
                }
            }
        },
        animation: { duration: 300 }
    }
});

function updateDiskPie(usedPct) {
    usedPct = Math.min(100, Math.max(0, usedPct));
    diskPieChart.data.datasets[0].data = [usedPct, 100 - usedPct];
    diskPieChart.update();
}

var diskDetailsMessageCount = 0;
var diskDetailsSource = '';

function applyDiskDetailsData(metricsData) {
    if (!metricsData) return;
    var pct = metricsData.diskInt != null ? metricsData.diskInt : (parseFloat(metricsData.diskPercent) || 0);
    updateDiskPie(pct);

    var diskPercentEl = document.getElementById('diskPercentValue');
    if (diskPercentEl) {
        diskPercentEl.innerText = metricsData.diskPercent != null ? metricsData.diskPercent : pct;
    }
    if (metricsData.diskTotal != null) {
        var diskTotalEl = document.getElementById('diskTotalValue');
        if (diskTotalEl) diskTotalEl.innerText = metricsData.diskTotal;
    }
    if (metricsData.diskUsed != null) {
        var diskUsedEl = document.getElementById('diskUsedValue');
        if (diskUsedEl) diskUsedEl.innerText = metricsData.diskUsed;
    }
    if (metricsData.diskFree != null) {
        var diskFreeEl = document.getElementById('diskFreeValue');
        if (diskFreeEl) diskFreeEl.innerText = metricsData.diskFree;
    }

    diskDetailsMessageCount++;
    if (diskDetailsMessageCount === 1 || diskDetailsMessageCount % 30 === 0) {
        console.log('[ServerDash Disk Details]', diskDetailsSource, '- métrica recebida #' + diskDetailsMessageCount);
    }
}

function subscribeDiskDetails(stompClient) {
    diskDetailsSource = 'WebSocket';
    diskDetailsMessageCount = 0;
    console.log('[ServerDash Disk Details] Inscrito em /topic/public (WebSocket)');
    stompClient.subscribe('/topic/public', function(message) {
        applyDiskDetailsData(JSON.parse(message.body));
    });
}

function startDiskDetailsPolling() {
    diskDetailsSource = 'Polling';
    diskDetailsMessageCount = 0;
    console.log('[ServerDash Disk Details] Fallback: iniciando polling GET /api/metrics/public a cada 1s');
    setInterval(function() {
        fetch('/api/metrics/public')
            .then(function(response) {
                if (!response.ok) console.warn('[ServerDash Disk Details] Polling falhou:', response.status);
                return response.ok ? response.json() : null;
            })
            .then(applyDiskDetailsData)
            .catch(function(error) { console.warn('[ServerDash Disk Details] Polling erro:', error); });
    }, 1000);
}

StompReconnect.connect({
    onConnect: subscribeDiskDetails,
    onFallbackToPolling: startDiskDetailsPolling,
    maxReconnectAttempts: 5,
    heartbeat: { incoming: 10000, outgoing: 10000 }
});
