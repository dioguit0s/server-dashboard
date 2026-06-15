var autoRefreshIntervalId = null;
var isLoadingLogs = false;
var currentTab = 'docker';

var urlParams = new URLSearchParams(window.location.search);
var deepLinkContainer = urlParams.get('container');
var deepLinkUnit = urlParams.get('unit');

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

function clampTail(value) {
    var n = parseInt(value, 10);
    if (isNaN(n)) return 100;
    return Math.max(10, Math.min(500, n));
}

function getTail() {
    var input = document.getElementById('tailInput');
    var clamped = clampTail(input.value);
    input.value = clamped;
    return clamped;
}

function getActiveSource() {
    if (currentTab === 'docker') {
        var container = document.getElementById('containerSelect').value;
        if (!container) return null;
        return { type: 'docker', container: container };
    }
    var customUnit = document.getElementById('unitInput').value.trim();
    var selectedUnit = document.getElementById('unitSelect').value;
    var unit = customUnit || selectedUnit;
    if (!unit) return null;
    return { type: 'systemd', unit: unit };
}

function buildLogsUrl(source) {
    var tail = getTail();
    if (source.type === 'docker') {
        return '/api/logs/docker?container=' + encodeURIComponent(source.container) + '&tail=' + tail;
    }
    return '/api/logs/journal?unit=' + encodeURIComponent(source.unit) + '&tail=' + tail;
}

function loadLogs() {
    var source = getActiveSource();
    if (!source) {
        var message = currentTab === 'docker'
            ? 'Selecione um container.'
            : 'Selecione um serviço ou informe o nome da unit.';
        document.getElementById('logsOutput').textContent = message;
        showLogsError(message);
        return;
    }

    if (isLoadingLogs) return;
    isLoadingLogs = true;

    var refreshBtn = document.getElementById('refreshLogsBtn');
    refreshBtn.disabled = true;

    fetch(buildLogsUrl(source))
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
        autoRefreshIntervalId = setInterval(loadLogs, 5000);
    }
}

function copyLogs() {
    var text = document.getElementById('logsOutput').textContent;
    if (!text || text === 'Selecione uma fonte de logs e clique em Atualizar.') {
        showToast('Não há logs para copiar', 'danger');
        return;
    }
    navigator.clipboard.writeText(text)
        .then(function() { showToast('Logs copiados para a área de transferência', 'success'); })
        .catch(function() { showToast('Falha ao copiar logs', 'danger'); });
}

function populateContainerSelect(containers, preselectId) {
    var select = document.getElementById('containerSelect');
    select.innerHTML = '';
    if (!containers || containers.length === 0) {
        select.innerHTML = '<option value="">Nenhum container encontrado</option>';
        return;
    }
    select.innerHTML = '<option value="">Selecione um container...</option>';
    containers.forEach(function(c) {
        var option = document.createElement('option');
        option.value = c.id;
        option.textContent = c.name + ' (' + c.id.substring(0, 12) + ') — ' + c.state;
        if (preselectId && (c.id === preselectId || c.name === preselectId)) {
            option.selected = true;
        }
        select.appendChild(option);
    });
}

function populateUnitSelect(units, preselectUnit) {
    var select = document.getElementById('unitSelect');
    select.innerHTML = '';
    if (!units || units.length === 0) {
        select.innerHTML = '<option value="">Nenhum serviço em execução</option>';
        return;
    }
    select.innerHTML = '<option value="">Selecione um serviço...</option>';
    units.forEach(function(unit) {
        var option = document.createElement('option');
        option.value = unit;
        option.textContent = unit;
        if (preselectUnit && unit === preselectUnit) {
            option.selected = true;
        }
        select.appendChild(option);
    });
}

function activateTab(tabName) {
    currentTab = tabName;
    var tabBtn = document.getElementById(tabName === 'docker' ? 'dockerTabBtn' : 'systemdTabBtn');
    if (tabBtn) {
        bootstrap.Tab.getOrCreateInstance(tabBtn).show();
    }
}

function loadSources() {
    return fetch('/api/logs/sources')
        .then(function(response) {
            return response.json().then(function(body) {
                if (!response.ok) {
                    throw new Error(body.error || 'Falha ao carregar fontes de logs');
                }
                populateContainerSelect(body.containers, deepLinkContainer);
                populateUnitSelect(body.units, deepLinkUnit);

                if (!body.unitsAvailable) {
                    document.getElementById('unitsUnavailableHint').classList.remove('d-none');
                }

                if (deepLinkContainer) {
                    activateTab('docker');
                    loadLogs();
                } else if (deepLinkUnit) {
                    activateTab('systemd');
                    loadLogs();
                }
            });
        })
        .catch(function(err) {
            showLogsError(err.message || 'Erro ao carregar fontes');
            showToast(err.message || 'Erro ao carregar fontes', 'danger');
        });
}

document.addEventListener('DOMContentLoaded', function() {
    document.getElementById('refreshLogsBtn').addEventListener('click', loadLogs);
    document.getElementById('copyLogsBtn').addEventListener('click', copyLogs);
    document.getElementById('autoRefreshToggle').addEventListener('change', function() {
        setAutoRefresh(this.checked);
    });
    document.getElementById('tailInput').addEventListener('change', function() {
        getTail();
    });

    document.getElementById('dockerTabBtn').addEventListener('shown.bs.tab', function() {
        currentTab = 'docker';
        if (document.getElementById('autoRefreshToggle').checked) {
            setAutoRefresh(true);
        }
    });
    document.getElementById('systemdTabBtn').addEventListener('shown.bs.tab', function() {
        currentTab = 'systemd';
        if (document.getElementById('autoRefreshToggle').checked) {
            setAutoRefresh(true);
        }
    });

    document.getElementById('containerSelect').addEventListener('change', function() {
        if (this.value) loadLogs();
    });
    document.getElementById('unitSelect').addEventListener('change', function() {
        if (this.value && !document.getElementById('unitInput').value.trim()) {
            loadLogs();
        }
    });
    document.getElementById('unitInput').addEventListener('keydown', function(e) {
        if (e.key === 'Enter' && this.value.trim()) loadLogs();
    });

    loadSources();
});
