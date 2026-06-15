var currentStateFilter = 'all';
var _emptyDockerCycles = 0;
var _hasReceivedData = false;

document.querySelectorAll('input[name="stateFilter"]').forEach(function(radio) {
    radio.addEventListener('change', function() {
        currentStateFilter = this.value;
        renderDockerContainers();
    });
});

function escapeHtml(text) {
    if (text == null || text === '') return '';
    var div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

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

function normalizeContainer(container) {
    if (!container) return null;
    return {
        containerIdentifier: container.containerIdentifier || container.id || '',
        containerName: container.containerName || container.name || '',
        containerState: container.containerState || container.state || '',
        containerStatus: container.containerStatus || container.status || '',
        cpuPercentage: container.cpuPercentage || '0.00%',
        memoryPercentage: container.memoryPercentage || '0.00%'
    };
}

function applyContainers(containerList) {
    _hasReceivedData = true;
    window.lastContainers = (containerList || [])
        .map(normalizeContainer)
        .filter(function(c) { return c && c.containerIdentifier; });
    updateDockerBanner(window.lastContainers);
    renderDockerContainers();
}

function loadContainersFromApi() {
    return fetch('/api/docker/containers')
        .then(function(response) {
            return response.json().then(function(body) {
                if (!response.ok) {
                    throw new Error(body.error || 'Falha ao carregar containers');
                }
                applyContainers(body.containers);
            });
        })
        .catch(function(err) {
            console.error('[containers]', err);
            if (!_hasReceivedData) {
                _hasReceivedData = true;
                window.lastContainers = [];
                updateDockerBanner(window.lastContainers);
                renderDockerContainers();
            } else {
                showToast(err.message || 'Erro ao atualizar containers', 'danger');
            }
        });
}

function startContainersPolling() {
    loadContainersFromApi();
    StompReconnect.startPollingFallback(3000, loadContainersFromApi);
}

function updateDockerBanner(containerList) {
    var banner = document.getElementById('dockerUnavailableBanner');
    if (!containerList || containerList.length === 0) {
        _emptyDockerCycles++;
        if (_hasReceivedData && _emptyDockerCycles >= 2) {
            banner.classList.remove('d-none');
        }
    } else {
        _emptyDockerCycles = 0;
        banner.classList.add('d-none');
    }
}

function getFilteredContainers() {
    var containers = window.lastContainers || [];
    if (currentStateFilter === 'all') {
        return containers;
    }
    return containers.filter(function(c) {
        var state = (c.containerState || '').toLowerCase();
        if (currentStateFilter === 'running') {
            return state === 'running';
        }
        return state !== 'running';
    });
}

function renderDockerContainers() {
    var tableBodyElement = document.getElementById('containersTableBody');
    var filteredList = getFilteredContainers();
    var allContainers = window.lastContainers || [];

    if (!allContainers.length) {
        if (!_hasReceivedData) {
            tableBodyElement.innerHTML = '<tr><td colspan="5" class="text-center text-white-50 py-5">Aguardando telemetria do Docker...</td></tr>';
        } else {
            tableBodyElement.innerHTML = '<tr><td colspan="5" class="text-center text-white-50 py-5">Nenhum container encontrado ou Docker indisponível.</td></tr>';
        }
        return;
    }

    if (!filteredList.length) {
        tableBodyElement.innerHTML = '<tr><td colspan="5" class="text-center text-white-50 py-5">Nenhum container corresponde ao filtro selecionado.</td></tr>';
        return;
    }

    var fragment = document.createDocumentFragment();
    for (var indexNumber = 0; indexNumber < filteredList.length; indexNumber++) {
        var currentContainer = filteredList[indexNumber];
        var isRunningBoolean = (currentContainer.containerState || '').toLowerCase() === 'running';
        var stateClassString = isRunningBoolean ? 'text-disk-accent' : 'text-danger';
        var stateIconString = isRunningBoolean ? 'bi-play-circle-fill' : 'bi-stop-circle-fill';
        var identifier = currentContainer.containerIdentifier || '';

        var tr = document.createElement('tr');
        tr.className = 'align-middle';
        tr.setAttribute('data-container-id', identifier);

        var tdName = document.createElement('td');
        tdName.className = 'ps-4 py-2';
        var nameSpan = document.createElement('span');
        nameSpan.className = 'text-white fw-medium';
        nameSpan.textContent = currentContainer.containerName || '';
        tdName.appendChild(nameSpan);
        tdName.appendChild(document.createElement('br'));
        var idSmall = document.createElement('small');
        idSmall.className = 'text-white-50 font-monospace';
        idSmall.textContent = identifier;
        tdName.appendChild(idSmall);
        tr.appendChild(tdName);

        var tdState = document.createElement('td');
        tdState.className = 'py-2';
        var stateSpan = document.createElement('span');
        stateSpan.className = stateClassString + ' fw-medium';
        stateSpan.innerHTML = '<i class="bi ' + stateIconString + ' me-1"></i>' + escapeHtml(currentContainer.containerState);
        tdState.appendChild(stateSpan);
        tdState.appendChild(document.createElement('br'));
        var statusSmall = document.createElement('small');
        statusSmall.className = 'text-white-50';
        statusSmall.textContent = currentContainer.containerStatus || '';
        tdState.appendChild(statusSmall);
        tr.appendChild(tdState);

        var tdCpu = document.createElement('td');
        tdCpu.className = 'py-2 text-end font-monospace text-white-50';
        tdCpu.textContent = currentContainer.cpuPercentage || '';
        tr.appendChild(tdCpu);

        var tdMem = document.createElement('td');
        tdMem.className = 'py-2 text-end font-monospace text-white-50';
        tdMem.textContent = currentContainer.memoryPercentage || '';
        tr.appendChild(tdMem);

        var tdActions = document.createElement('td');
        tdActions.className = 'py-2 text-end pe-4';

        var logsLink = document.createElement('a');
        logsLink.href = '/logs?container=' + encodeURIComponent(identifier);
        logsLink.className = 'btn btn-sm btn-outline-info me-1';
        logsLink.title = 'Ver logs';
        var logsIcon = document.createElement('i');
        logsIcon.className = 'bi bi-journal-text';
        logsLink.appendChild(logsIcon);
        tdActions.appendChild(logsLink);

        var actionConfigs = [
            { action: 'start', btnClass: 'btn-outline-success', icon: 'bi-play-fill', title: 'Iniciar' },
            { action: 'restart', btnClass: 'btn-outline-warning', icon: 'bi-arrow-clockwise', title: 'Reiniciar' },
            { action: 'stop', btnClass: 'btn-outline-danger', icon: 'bi-stop-fill', title: 'Parar' }
        ];
        actionConfigs.forEach(function(config) {
            var btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'btn btn-sm me-1 container-action-button ' + config.btnClass;
            btn.setAttribute('data-action', config.action);
            btn.setAttribute('data-identifier', identifier);
            btn.title = config.title;
            var icon = document.createElement('i');
            icon.className = 'bi ' + config.icon;
            btn.appendChild(icon);
            tdActions.appendChild(btn);
        });
        tr.appendChild(tdActions);

        fragment.appendChild(tr);
    }
    tableBodyElement.innerHTML = '';
    tableBodyElement.appendChild(fragment);

    tableBodyElement.querySelectorAll('.container-action-button').forEach(function(btn) {
        btn.addEventListener('click', function(eventObject) {
            var buttonTarget = eventObject.currentTarget;
            executeContainerAction(
                buttonTarget.getAttribute('data-action'),
                buttonTarget.getAttribute('data-identifier'),
                buttonTarget.closest('tr')
            );
        });
    });
}

function setRowButtonsDisabled(row, disabled) {
    if (!row) return;
    row.querySelectorAll('.container-action-button').forEach(function(btn) {
        btn.disabled = disabled;
    });
}

function executeContainerAction(actionString, identifierString, rowElement) {
    if (!confirm('Deseja executar a ação "' + actionString + '" no container ' + identifierString + '?')) {
        return;
    }

    setRowButtonsDisabled(rowElement, true);

    fetchWithCsrf('/api/docker/' + encodeURIComponent(actionString) + '/' + encodeURIComponent(identifierString), {
        method: 'POST'
    }).then(function(responseObject) {
        return responseObject.json().then(function(body) {
            if (responseObject.ok) {
                showToast(body.message || 'Ação executada com sucesso', 'success');
                return;
            }
            throw new Error(body.error || 'Falha ao executar a ação no container.');
        });
    }).catch(function(errorObject) {
        showToast(errorObject.message || 'Erro de comunicação com o servidor', 'danger');
    }).finally(function() {
        setRowButtonsDisabled(rowElement, false);
    });
}

StompReconnect.connect({
    onConnect: function(stompClient) {
        loadContainersFromApi();
        stompClient.subscribe('/topic/docker', function(messageObject) {
            applyContainers(JSON.parse(messageObject.body));
        });
    },
    onFallbackToPolling: startContainersPolling,
    heartbeat: { incoming: 10000, outgoing: 10000 }
});
