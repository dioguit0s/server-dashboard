/* src/main/resources/static/js/dashboard.js */

function applyPublicMetrics(data) {
    if (!data) return;
    var cpuPercentText = document.getElementById('cpuPercentText');
    if (cpuPercentText) cpuPercentText.innerText = data.cpuPercent + '%';
    var cpuBar = document.getElementById('cpuBar');
    if (cpuBar) cpuBar.style.width = data.cpuInt + '%';

    var tempText = document.getElementById('tempText');
    if (tempText) tempText.innerText = data.cpuTemp + '°C';
    var tempBar = document.getElementById('tempBar');
    if (tempBar) tempBar.style.width = data.cpuTempInt + '%';

    var tempVal = data.cpuTempInt;
    var tempBadge = document.getElementById('tempBadge');
    var tempStatus = document.getElementById('tempStatus');
    if (tempBadge) {
        if (tempVal > 75) {
            tempBadge.className = 'badge rounded-pill text-bg-danger';
            if (tempStatus) { tempStatus.innerText = 'Aquecido'; tempStatus.className = 'text-danger fw-bold'; }
        } else {
            tempBadge.className = 'badge rounded-pill text-bg-primary bg-opacity-25 text-primary';
            if (tempStatus) { tempStatus.innerText = 'Estável'; tempStatus.className = 'text-white'; }
        }
    }

    var ramPercentText = document.getElementById('ramPercentText');
    if (ramPercentText) ramPercentText.innerText = data.ramPercent + '%';
    var ramBar = document.getElementById('ramBar');
    if (ramBar) ramBar.style.width = data.ramInt + '%';
    var ramFreeText = document.getElementById('ramFreeText');
    if (ramFreeText) ramFreeText.innerText = data.ramLivre;

    var diskPercentText = document.getElementById('diskPercentText');
    if (diskPercentText) diskPercentText.innerText = data.diskPercent + '%';
    var diskBar = document.getElementById('diskBar');
    if (diskBar) diskBar.style.width = data.diskInt + '%';
    var diskFreeText = document.getElementById('diskFreeText');
    if (diskFreeText) diskFreeText.innerText = data.diskFree;

    var uptimeText = document.getElementById('uptimeText');
    if (uptimeText) uptimeText.innerText = data.uptime;

    var elNetDown = document.getElementById('netDownText');
    var elNetup = document.getElementById('netUpText');
    if (elNetDown) elNetDown.innerText = data.netDown;
    if (elNetup) elNetup.innerText = data.netUp;
}

function subscribePublicMetrics(stompClient) {
    stompClient.subscribe('/topic/public', function (message) {
        applyPublicMetrics(JSON.parse(message.body));
    });
}

function startMetricsPolling() {
    setInterval(function() {
        fetch('/api/metrics/public')
            .then(function(res) { return res.ok ? res.json() : null; })
            .then(applyPublicMetrics)
            .catch(function() {});
    }, 1000);
}

StompReconnect.connect({
    onConnect: subscribePublicMetrics,
    onFallbackToPolling: startMetricsPolling,
    maxReconnectAttempts: 5,
    heartbeat: { incoming: 10000, outgoing: 10000 }
});
