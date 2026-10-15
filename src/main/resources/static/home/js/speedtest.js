/* Card "Velocidade da Internet": o teste roda no servidor (SpeedTestService); aqui so' lemos o
 * estado e, para ADMIN, disparamos um teste novo. Enquanto ha teste em andamento o estado e'
 * consultado a cada 2s; parado, a cada 60s (para pegar testes agendados ou disparados em outra aba). */
(function () {
    var POLL_RUNNING_MS = 2000;
    var POLL_IDLE_MS = 60000;
    var PHASES = { latency: 'Medindo ping…', download: 'Medindo download…', upload: 'Medindo upload…' };

    var el = {
        down: document.getElementById('speedDownText'),
        up: document.getElementById('speedUpText'),
        ping: document.getElementById('speedPingText'),
        jitter: document.getElementById('speedJitterText'),
        last: document.getElementById('speedLastText'),
        phase: document.getElementById('speedPhase'),
        error: document.getElementById('speedErrorText'),
        btn: document.getElementById('speedRunBtn')
    };
    if (!el.down) return;

    var timer = null;

    function formatSpeed(mbps) {
        if (mbps == null) return '--';
        return mbps >= 100 ? Math.round(mbps).toString() : mbps.toFixed(1);
    }

    function timeAgo(epochMs) {
        var secs = Math.max(0, Math.round((Date.now() - epochMs) / 1000));
        if (secs < 60) return 'agora há pouco';
        var mins = Math.round(secs / 60);
        if (mins < 60) return 'há ' + mins + ' min';
        var hours = Math.round(mins / 60);
        if (hours < 24) return 'há ' + hours + ' h';
        return new Date(epochMs).toLocaleString('pt-BR');
    }

    function render(status) {
        var r = status.lastResult;
        el.down.textContent = r ? formatSpeed(r.downloadMbps) : '--';
        el.up.textContent = r ? formatSpeed(r.uploadMbps) : '--';
        el.ping.textContent = r ? Math.round(r.latencyMs).toString() : '--';
        el.jitter.textContent = r ? 'Jitter: ' + r.jitterMs.toFixed(1) + ' ms' : '';

        var lastText = r ? 'Último teste: ' + timeAgo(r.finishedAt) + (r.trigger === 'agendado' ? ' (agendado)' : '')
                         : 'Nenhum teste executado ainda';
        if (status.intervalMinutes > 0) {
            var every = status.intervalMinutes % 60 === 0 ? (status.intervalMinutes / 60) + ' h' : status.intervalMinutes + ' min';
            lastText += ' · automático a cada ' + every;
        }
        el.last.textContent = lastText;

        if (status.running) {
            el.phase.textContent = PHASES[status.phase] || 'Testando…';
            el.phase.classList.remove('d-none');
        } else {
            el.phase.classList.add('d-none');
        }

        var showError = status.lastError && !status.running &&
            (!r || (status.lastErrorAt && status.lastErrorAt > r.finishedAt));
        el.error.textContent = showError ? 'Falha no último teste: ' + status.lastError : '';
        el.error.classList.toggle('d-none', !showError);

        if (typeof canWrite === 'function' && canWrite()) {
            el.btn.classList.remove('d-none');
            el.btn.disabled = !!status.running;
        }
    }

    function schedule(running) {
        clearTimeout(timer);
        timer = setTimeout(refresh, running ? POLL_RUNNING_MS : POLL_IDLE_MS);
    }

    function refresh() {
        fetch('/api/speedtest', { credentials: 'same-origin' })
            .then(function (res) { return res.ok ? res.json() : null; })
            .then(function (status) {
                if (status) render(status);
                schedule(status && status.running);
            })
            .catch(function (err) {
                console.warn('[ServerDash SpeedTest] Falha ao ler estado:', err);
                schedule(false);
            });
    }

    el.btn.addEventListener('click', function () {
        el.btn.disabled = true;
        fetchWithCsrf('/api/speedtest/run', { method: 'POST' })
            .then(function (res) { return res.json().catch(function () { return null; }); })
            .then(function (status) {
                if (status) render(status);
                schedule(true);
            })
            .catch(function (err) {
                console.warn('[ServerDash SpeedTest] Falha ao iniciar teste:', err);
                el.btn.disabled = false;
            });
    });

    refresh();
})();
