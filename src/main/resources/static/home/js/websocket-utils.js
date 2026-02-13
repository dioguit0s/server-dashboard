/**
 * Utilitário de WebSocket com reconexão automática (compatível com Cloudflare Tunnel).
 * Quando a conexão cai, reconecta com backoff exponencial e re-subcreve aos tópicos.
 */
var StompReconnect = (function() {
    var reconnectDelay = 1000;
    var maxReconnectDelay = 30000;
    var heartbeat = { incoming: 10000, outgoing: 10000 };

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

        function scheduleReconnect() {
            if (reconnectScheduled || fallbackActive) return;
            reconnectScheduled = true;
            reconnectAttempts++;
            if (showIndicator) showReconnecting(true);
            if (reconnectAttempts >= maxReconnectAttempts && onFallbackToPolling) {
                fallbackActive = true;
                reconnectScheduled = false;
                if (showIndicator) showReconnecting(false);
                onFallbackToPolling();
                return;
            }
            setTimeout(function() {
                reconnectScheduled = false;
                reconnectDelay = Math.min(reconnectDelay * 2, maxReconnectDelay);
                doConnect();
            }, reconnectDelay);
        }

        function doConnect() {
            var socket = new SockJS('/ws');
            var stompClient = Stomp.over(socket);
            stompClient.debug = null;

            stompClient.connect({ heartbeat: hb }, function(frame) {
                reconnectDelay = 1000;
                reconnectAttempts = 0;
                if (showIndicator) showReconnecting(false);
                onConnect(stompClient);
            }, function() {
                scheduleReconnect();
            });

            socket.onclose = function() {
                scheduleReconnect();
            };

            socket.onerror = function() {
                scheduleReconnect();
            };
        }

        doConnect();
    }

    return { connect: connect };
})();
