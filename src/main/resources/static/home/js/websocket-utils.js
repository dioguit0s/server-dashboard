/**
 * Utilitário de WebSocket com reconexão automática (compatível com Cloudflare Tunnel).
 * Quando a conexão cai, reconecta com backoff exponencial e re-subcreve aos tópicos.
 */
var StompReconnect = (function() {
    var PREFIX = '[ServerDash WS]';
    var reconnectDelay = 1000;
    var maxReconnectDelay = 30000;
    var heartbeat = { incoming: 10000, outgoing: 10000 };

    function log() {
        var args = Array.prototype.slice.call(arguments);
        args.unshift(PREFIX, new Date().toISOString());
        console.log.apply(console, args);
    }

    function getIndicator() {
        var el = document.getElementById('ws-reconnect-indicator');
        if (!el) {
            el = document.createElement('div');
            el.id = 'ws-reconnect-indicator';
            el.className = 'position-fixed top-0 start-50 translate-middle-x mt-3 px-3 py-2 rounded-pill bg-warning bg-opacity-90 text-dark small shadow z-1030 d-none';
            el.innerHTML = '<i class="bi bi-arrow-repeat spin me-2"></i>Reconectando...';
            document.body.appendChild(el);
        }
        return el;
    }

    function showReconnecting(show) {
        var el = getIndicator();
        el.classList.toggle('d-none', !show);
    }

    function connect(options) {
        var onConnect = options.onConnect || function() {};
        var onFallbackToPolling = options.onFallbackToPolling || null;
        var maxReconnectAttempts = options.maxReconnectAttempts || 10;
        var showIndicator = options.showReconnectingIndicator !== false;
        var hb = options.heartbeat || heartbeat;

        var reconnectScheduled = false;
        var reconnectAttempts = 0;
        var fallbackActive = false;

        function scheduleReconnect(reason) {
            if (reconnectScheduled || fallbackActive) return;
            reconnectScheduled = true;
            reconnectAttempts++;
            log('Conexão perdida:', reason || 'desconhecido', '| Tentativa de reconexão:', reconnectAttempts, '/', maxReconnectAttempts);
            if (showIndicator) showReconnecting(true);
            if (reconnectAttempts >= maxReconnectAttempts && onFallbackToPolling) {
                fallbackActive = true;
                reconnectScheduled = false;
                if (showIndicator) showReconnecting(false);
                log('Limite de reconexões atingido. Ativando fallback de polling (API REST).');
                onFallbackToPolling();
                return;
            }
            var delay = reconnectDelay;
            reconnectDelay = Math.min(reconnectDelay * 2, maxReconnectDelay);
            log('Reconectando em', delay, 'ms...');
            setTimeout(function() {
                reconnectScheduled = false;
                doConnect();
            }, delay);
        }

        function doConnect() {
            log('Iniciando conexão SockJS para /ws ...');
            var socket = new SockJS('/ws');
            var stompClient = Stomp.over(socket);
            stompClient.debug = null;

            stompClient.connect({ heartbeat: hb }, function(frame) {
                reconnectDelay = 1000;
                reconnectAttempts = 0;
                log('Conectado com sucesso. Frame:', frame ? frame.command : 'N/A');
                if (showIndicator) showReconnecting(false);
                onConnect(stompClient);
            }, function(err) {
                log('Erro na conexão STOMP:', err);
                scheduleReconnect('erro STOMP');
            });

            socket.onclose = function(ev) {
                log('Socket fechado. code:', ev ? ev.code : '?', 'reason:', ev ? ev.reason : '?', 'clean:', ev ? ev.wasClean : '?');
                scheduleReconnect('socket onclose');
            };

            socket.onerror = function(err) {
                log('Socket erro:', err);
                scheduleReconnect('socket onerror');
            };
        }

        log('StompReconnect iniciado. Host:', window.location.host, 'Protocol:', window.location.protocol);
        doConnect();
    }

    return { connect: connect };
})();
