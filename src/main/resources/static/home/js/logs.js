var containerIdentifier = new URLSearchParams(window.location.search).get('container');
var autoRefreshIntervalId = null;
var isLoadingLogs = false;

function showToast(message, type) {
    var toastContainer = document.getElementById('toastContainer');
    var toastId = 'toast-' + Date.now();
    var bgClass = type === 'success' ? 'text-bg-success' : 'text-bg-danger';
    var html = '<div id="' + toastId + '" class="toast align-items-center ' + bgClass + ' border-0" role="alert" aria-live="assertive" aria-atomic="true">' +
        '<div class="d-flex">' +
        '<div class="toast-body">' + escapeHtml(message) + '</div>' +
        '<button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Fechar"></button>' +
        '</div></div>';
    toastContainer.insertAdjacentHTML('beforeend', html);
    var toastElement = document.getElementById(toastId);
    var toast = new bootstrap.Toast(toastElement, { delay: 4000 });
    toast.show();
    toastElement.addEventListener('hidden.bs.toast', function() {
        toastElement.remove();
    });
}

function escapeHtml(text) {
    if (!text) return '';
    var div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function showLogsError(message) {
    var errorElement = document.getElementById('logsError');
    errorElement.textContent = message;
    errorElement.classList.remove('d-none');
}

function hideLogsError() {
    document.getElementById('logsError').classList.add('d-none');
}

function loadLogs() {
    if (!containerIdentifier) {
        document.getElementById('logsOutput').textContent = 'Nenhum container especificado. Use ?container=ID na URL.';
        showLogsError('Parâmetro "container" ausente na URL.');
        return;
    }

    if (isLoadingLogs) return;
    isLoadingLogs = true;

    var refreshBtn = document.getElementById('refreshLogsBtn');
    refreshBtn.disabled = true;

    fetch('/api/docker/' + encodeURIComponent(containerIdentifier) + '/logs?tail=200')
        .then(function(response) {
            return response.json().then(function(body) {
                if (response.ok) {
                    hideLogsError();
                    document.getElementById('logsOutput').textContent = body.logs || '(sem logs)';
                    return;
                }
                throw new Error(body.error || 'Falha ao carregar logs');
            });
        })
        .catch(function(err) {
            var message = err.message || 'Erro ao carregar logs';
            showLogsError(message);
            showToast(message, 'danger');
        })
        .finally(function() {
            isLoadingLogs = false;
            refreshBtn.disabled = false;
        });
}

function setAutoRefresh(enabled) {
    if (autoRefreshIntervalId) {
        clearInterval(autoRefreshIntervalId);
        autoRefreshIntervalId = null;
    }
    if (enabled) {
        autoRefreshIntervalId = setInterval(loadLogs, 3000);
    }
}

document.addEventListener('DOMContentLoaded', function() {
    if (containerIdentifier) {
        document.getElementById('containerBadge').textContent = containerIdentifier;
    } else {
        document.getElementById('containerBadge').textContent = 'Não especificado';
    }

    document.getElementById('refreshLogsBtn').addEventListener('click', loadLogs);
    document.getElementById('autoRefreshToggle').addEventListener('change', function() {
        setAutoRefresh(this.checked);
    });

    loadLogs();
});
