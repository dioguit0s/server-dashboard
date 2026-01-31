/* src/main/resources/static/js/dashboard.js */

var socket = new SockJS('/ws');
var stompClient = Stomp.over(socket);
// Desabilita logs de debug no console para limpar a visualização
stompClient.debug = null;

stompClient.connect({}, function (frame) {
    stompClient.subscribe('/topic/metrics', function (message) {
        var data = JSON.parse(message.body);

        // Atualiza CPU
        document.getElementById('cpuPercentText').innerText = data.cpuPercent + '%';
        document.getElementById('cpuBar').style.width = data.cpuInt + '%';

        // Atualiza Temperatura
        document.getElementById('tempText').innerText = data.cpuTemp + '°C';
        document.getElementById('tempBar').style.width = data.cpuTempInt + '%';

        // Lógica visual para a temperatura (com proteção contra erro)
        var tempVal = data.cpuTempInt;
        var tempBadge = document.getElementById('tempBadge');
        var tempStatus = document.getElementById('tempStatus'); // Pode ser null se não existir no HTML

        if (tempBadge) {
            if (tempVal > 75) {
                tempBadge.className = 'badge rounded-pill text-bg-danger';
                if (tempStatus) {
                    tempStatus.innerText = 'Aquecido';
                    tempStatus.className = 'text-danger fw-bold';
                }
            } else {
                tempBadge.className = 'badge rounded-pill text-bg-primary bg-opacity-25 text-primary';
                if (tempStatus) {
                    tempStatus.innerText = 'Estável';
                    tempStatus.className = 'text-white';
                }
            }
        }

        // Atualiza RAM
        document.getElementById('ramPercentText').innerText = data.ramPercent + '%';
        document.getElementById('ramBar').style.width = data.ramInt + '%';
        document.getElementById('ramFreeText').innerText = data.ramLivre;

        // Atualiza Disco
        document.getElementById('diskPercentText').innerText = data.diskPercent + '%';
        document.getElementById('diskBar').style.width = data.diskInt + '%';
        document.getElementById('diskFreeText').innerText = data.diskFree;

        // Atualiza Uptime
        document.getElementById('uptimeText').innerText = data.uptime;

        var elNetDown = document.getElementById('netDownText');
        var elNetup = document.getElementById('netUpText');

        if(elNetDown) elNetDown.innerText = data.netDown;
        if(elNetup) elNetup.innerText = data.netUp;
    });
});