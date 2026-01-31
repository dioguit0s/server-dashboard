
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

function addData(chart, label, data) {
    chart.data.labels.push(label);
    chart.data.datasets.forEach((dataset) => {
        dataset.data.push(data);
    });

    if (chart.data.labels.length > 60) {
        chart.data.labels.shift();
        chart.data.datasets.forEach((dataset) => {
            dataset.data.shift();
        });
    }
    chart.update();
}

var socket = new SockJS('/ws');
var stompClient = Stomp.over(socket);
stompClient.debug = null;

stompClient.connect({}, function (frame) {
    stompClient.subscribe('/topic/metrics', function (message) {
        var data = JSON.parse(message.body);
        var now = new Date().toLocaleTimeString();

        addData(cpuChart, now, data.cpuInt);
        addData(ramChart, now, data.ramInt);
        addData(tempChart, now, data.cpuTempInt);

        if(document.getElementById('cpuValue')) document.getElementById('cpuValue').innerText = data.cpuPercent + '%';
        if(document.getElementById('ramValue')) document.getElementById('ramValue').innerText = data.ramPercent + '%';
        if(document.getElementById('tempValue')) document.getElementById('tempValue').innerText = data.cpuTemp + '°C';
    });
});