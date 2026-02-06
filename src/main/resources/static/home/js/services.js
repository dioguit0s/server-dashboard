var socket = new SockJS('/ws');
var stompClient = Stomp.over(socket);
stompClient.debug = null;

function renderServices(services) {
    var container = document.getElementById('servicesGrid');
    container.innerHTML = '';

    if (!services || services.length === 0) {
        container.innerHTML = '<div class="col-12 text-muted">Nenhum serviço configurado. Clique em "Adicionar Serviço" para começar.</div>';
        return;
    }

    services.forEach(function(service) {
        var isOnline = service.online;
        var colorClass = isOnline ? 'success' : 'danger';
        var iconClass = isOnline ? 'check-circle-fill' : 'exclamation-circle-fill';
        var statusText = isOnline ? 'ONLINE' : 'OFFLINE';
        var borderClass = isOnline ? 'border-success' : 'border-danger';

        var html = '<div class="col-12 col-md-6 col-lg-4">' +
            '<div class="card card-metric h-100 border-start border-4 border-' + colorClass + '">' +
            '<div class="card-body p-4 d-flex align-items-center justify-content-between gap-2">' +
            '<div class="flex-grow-1 min-width-0">' +
            '<h5 class="text-white mb-1 fw-bold text-truncate">' + escapeHtml(service.name) + '</h5>' +
            '<div class="text-muted small">Porta: ' + service.port + '</div>' +
            '</div>' +
            '<div class="d-flex align-items-center gap-2 flex-shrink-0">' +
            '<span class="badge rounded-pill bg-' + colorClass + ' bg-opacity-10 text-' + colorClass + ' border border-' + colorClass + ' border-opacity-25 px-3 py-2">' +
            '<i class="bi bi-' + iconClass + ' me-1"></i> ' + statusText + '</span>' +
            '<button type="button" class="btn btn-outline-danger btn-sm p-2 btn-remove-service" data-port="' + service.port + '" data-name="' + escapeHtml(service.name) + '" title="Remover serviço">' +
            '<i class="bi bi-trash"></i></button>' +
            '</div>' +
            '</div></div></div>';
        container.insertAdjacentHTML('beforeend', html);
    });

    container.querySelectorAll('.btn-remove-service').forEach(function(btn) {
        btn.addEventListener('click', function() {
            removeService(parseInt(btn.dataset.port, 10), btn.dataset.name || '');
        });
    });
}

function escapeHtml(text) {
    if (!text) return '';
    var div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML.replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function removeService(port, name) {
    if (!confirm('Remover o serviço "' + name + '" (porta ' + port + ')?')) return;
    fetch('/api/services/' + port, { method: 'DELETE' })
        .then(function(res) {
            if (res.ok || res.status === 204) return;
            throw new Error('Falha ao remover');
        })
        .catch(function(err) { alert('Erro: ' + (err.message || 'Falha ao remover')); });
}

document.addEventListener('DOMContentLoaded', function() {
    var addServiceForm = document.getElementById('addServiceForm');
    var addServiceBtn = document.getElementById('addServiceBtn');
    var addServiceError = document.getElementById('addServiceError');
    var serviceNameInput = document.getElementById('serviceName');
    var servicePortInput = document.getElementById('servicePort');

    addServiceBtn.addEventListener('click', function() {
        var name = serviceNameInput.value.trim();
        var portStr = servicePortInput.value.trim();
        addServiceError.classList.add('d-none');
        addServiceError.textContent = '';

        if (!name) {
            addServiceError.textContent = 'Informe o nome do serviço.';
            addServiceError.classList.remove('d-none');
            return;
        }
        var port = parseInt(portStr, 10);
        if (isNaN(port) || port < 1 || port > 65535) {
            addServiceError.textContent = 'Informe uma porta válida (1-65535).';
            addServiceError.classList.remove('d-none');
            return;
        }

        addServiceBtn.disabled = true;
        fetch('/api/services', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name: name, port: port })
        })
        .then(function(res) {
            return res.json().then(function(body) {
                if (res.ok) {
                    var modal = bootstrap.Modal.getInstance(document.getElementById('addServiceModal'));
                    if (modal) modal.hide();
                    addServiceForm.reset();
                    return;
                }
                throw new Error(body.error || 'Erro ao adicionar');
            });
        })
        .catch(function(err) {
            addServiceError.textContent = err.message;
            addServiceError.classList.remove('d-none');
        })
        .finally(function() { addServiceBtn.disabled = false; });
    });

    document.getElementById('addServiceModal').addEventListener('hidden.bs.modal', function() {
        addServiceForm.reset();
        addServiceError.classList.add('d-none');
    });
});

stompClient.connect({}, function (frame) {
    stompClient.subscribe('/topic/metrics', function (message) {
        var data = JSON.parse(message.body);
        if (data.services) renderServices(data.services);
    });
});