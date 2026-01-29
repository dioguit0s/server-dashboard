/* src/main/resources/static/js/charts.js */

// Configuração comum para os gráficos (visual dark)
const commonOptions = {
    responsive: true,
    scales: {
        x: {
            display: false, // Esconde os labels de tempo para ficar mais limpo
            grid: { display: false }
        },
        y: {
            beginAtZero: true,
            grid: { color: '#333' }, // Linhas da grade escuras
            ticks: { color: '#aaa' }
        }
    },
    plugins: {
        legend: { display: false } // Esconde legenda pois o título do card já diz o que é
    },
    animation: { duration: 0 } // Desativa animação de "load" a cada update para ficar fluido
};

// Inicialização dos Gráficos
const ctxCpu = document.getElementById('cpuChart').getContext('2d');
const cpuChart = new Chart(ctxCpu, {
    type: 'line',
    data: {
        labels: [],
        datasets: [{
            label: 'CPU %',
            data: [],
            borderColor: '#0d6efd', // Bootstrap Primary Blue
            backgroundColor: 'rgba(13, 110, 253, 0.1)',
            fill: true,
            tension: 0.3
        }]
    },
    options: {
        ...commonOptions,
        scales: {
            ...commonOptions.scales,
            y: { ...commonOptions.scales.y, max: 100 } // CPU vai até 100%
        }
    }
});

const ctxRam = document.getElementById('ramChart').getContext('2d');
const ramChart = new Chart(ctxRam, {
    type: 'line',
    data: {
        labels: [],
        datasets: [{
            label: 'RAM %',
            data: [],
            borderColor: '#0dcaf0', // Bootstrap Info Cyan
            backgroundColor: 'rgba(13, 202, 240, 0.1)',
            fill: true,
            tension: 0.3
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
const tempChart = new Chart(ctxTemp, {
    type: 'line',
    data: {
        labels: [],
        datasets: [{
            label: 'Temp °C',
            data: [],
            borderColor: '#ffc107', // Bootstrap Warning Yellow
            backgroundColor: 'rgba(255, 193, 7, 0.1)',
            fill: true,
            tension: 0.3
        }]
    },
    options: commonOptions
});

// Função para adicionar dados e manter janela de tempo (ex: 60 pontos)
function addData(chart, label, data) {
    chart.data.labels.push(label);
    chart.data.datasets.forEach((dataset) => {
        dataset.data.push(data);
    });

    // Mantém apenas os últimos 60 pontos (60 segundos)
    if (chart.data.labels.length > 60) {
        chart.data.labels.shift(); // Remove o mais antigo
        chart.data.datasets.forEach((dataset) => {
            dataset.data.shift();
        });
    }
    chart.update();
}

// Conexão WebSocket (Igual ao da Home, mas chama a função de gráfico)
var socket = new SockJS('/ws');
var stompClient = Stomp.over(socket);
stompClient.debug = null;

stompClient.connect({}, function (frame) {
    stompClient.subscribe('/topic/metrics', function (message) {
        var data = JSON.parse(message.body);
        var now = new Date().toLocaleTimeString();

        // Atualiza os gráficos
        addData(cpuChart, now, data.cpuInt);
        addData(ramChart, now, data.ramInt);
        addData(tempChart, now, data.cpuTempInt);

        // Atualiza textos rápidos nos cards (opcional, para ter o valor numérico junto)
        document.getElementById('cpuValue').innerText = data.cpuPercent + '%';
        document.getElementById('ramValue').innerText = data.ramPercent + '%';
        document.getElementById('tempValue').innerText = data.cpuTemp + '°C';
    });
});