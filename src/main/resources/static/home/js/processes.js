var socket = new SockJS('/ws');
var stompClient = Stomp.over(socket);
stompClient.debug = null;

var currentSort = 'cpu';

document.querySelectorAll('input[name="sortBy"]').forEach(function(radio) {
    radio.addEventListener('change', function() {
        currentSort = this.value;
        renderProcesses();
    });
});

function renderProcesses() {
    var processes = currentSort === 'ram' ? window.lastProcessesByRam : window.lastProcessesByCpu;
    var tbody = document.getElementById('processesTableBody');

    if (!processes || processes.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-5">Nenhum dado disponível</td></tr>';
        return;
    }

    tbody.innerHTML = processes.map(function(p, i) {
        var cpuClass = parseFloat(p.cpuPercent) > 80 ? 'text-danger' : (parseFloat(p.cpuPercent) > 50 ? 'text-warning' : 'text-white');
        var ramClass = parseFloat(p.ramPercent) > 80 ? 'text-danger' : (parseFloat(p.ramPercent) > 50 ? 'text-warning' : 'text-white');
        return '<tr class="align-middle">' +
            '<td class="ps-4 py-2 text-muted small">' + (i + 1) + '</td>' +
            '<td class="py-2"><span class="text-white" title="' + escapeHtml(p.name) + '">' + escapeHtml(truncate(p.name, 45)) + '</span></td>' +
            '<td class="py-2 font-monospace text-muted small">' + p.pid + '</td>' +
            '<td class="py-2 text-end pe-4 ' + cpuClass + ' fw-semibold">' + p.cpuPercent + '%</td>' +
            '<td class="py-2 text-end pe-4 ' + ramClass + ' fw-semibold">' + p.ramPercent + '%</td>' +
            '<td class="py-2 text-end pe-4 text-muted font-monospace small">' + p.ramFormatted + '</td>' +
            '</tr>';
    }).join('');
}

function escapeHtml(text) {
    var div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function truncate(str, len) {
    if (!str) return '';
    return str.length > len ? str.substring(0, len) + '...' : str;
}

stompClient.connect({}, function (frame) {
    stompClient.subscribe('/topic/metrics', function (message) {
        var data = JSON.parse(message.body);
        if (data.processesByCpu) window.lastProcessesByCpu = data.processesByCpu;
        if (data.processesByRam) window.lastProcessesByRam = data.processesByRam;
        renderProcesses();
    });
});
