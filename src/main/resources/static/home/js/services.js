var socket = new SockJS('/ws');
var stompClient = Stomp.over(socket);
stompClient.debug = null;

stompClient.connect({}, function (frame) {
    stompClient.subscribe('/topic/metrics', function (message) {
        var data = JSON.parse(message.body);

        if (data.services) {
            var container = document.getElementById('servicesGrid');
            container.innerHTML = ''; // Limpa grid

            data.services.forEach(function(service) {
                var isOnline = service.online;
                var colorClass = isOnline ? 'success' : 'danger';
                var iconClass = isOnline ? 'check-circle-fill' : 'exclamation-circle-fill';
                var statusText = isOnline ? 'ONLINE' : 'OFFLINE';
                var borderClass = isOnline ? 'border-success' : 'border-danger';

                var html = `
                <div class="col-12 col-md-6 col-lg-4">
                    <div class="card card-metric h-100 border-start border-4 border-${colorClass}">
                        <div class="card-body p-4 d-flex align-items-center justify-content-between">
                            <div>
                                <h5 class="text-white mb-1 fw-bold">${service.name}</h5>
                                <div class="text-muted small">Porta: ${service.port}</div>
                            </div>
                            <div class="text-end">
                                <span class="badge rounded-pill bg-${colorClass} bg-opacity-10 text-${colorClass} border ${borderClass} border-opacity-25 px-3 py-2">
                                    <i class="bi bi-${iconClass} me-1"></i> ${statusText}
                                </span>
                            </div>
                        </div>
                    </div>
                </div>
            `;
                container.insertAdjacentHTML('beforeend', html);
            });
        }
    });
});