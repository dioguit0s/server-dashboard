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
    if (!text) return '';
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

    var htmlContentString = '';
    for (var indexNumber = 0; indexNumber < filteredList.length; indexNumber++) {
        var currentContainer = filteredList[indexNumber];
        var isRunningBoolean = (currentContainer.containerState || '').toLowerCase() === 'running';
        var stateClassString = isRunningBoolean ? 'text-success' : 'text-danger';
        var stateIconString = isRunningBoolean ? 'bi-play-circle-fill' : 'bi-stop-circle-fill';
        var identifier = currentContainer.containerIdentifier || '';

        htmlContentString += '<tr class="align-middle" data-container-id="' + escapeHtml(identifier) + '">' +
            '<td class="ps-4 py-2"><span class="text-white fw-bold">' + escapeHtml(currentContainer.containerName) + '</span><br><small class="text-white-50 font-monospace">' + escapeHtml(identifier) + '</small></td>' +
            '<td class="py-2"><span class="' + stateClassString + ' fw-semibold"><i class="bi ' + stateIconString + ' me-1"></i>' + escapeHtml(currentContainer.containerState) + '</span><br><small class="text-white-50">' + escapeHtml(currentContainer.containerStatus) + '</small></td>' +
            '<td class="py-2 text-end font-monospace text-white-50">' + escapeHtml(currentContainer.cpuPercentage) + '</td>' +
            '<td class="py-2 text-end font-monospace text-white-50">' + escapeHtml(currentContainer.memoryPercentage) + '</td>' +
            '<td class="py-2 text-end pe-4">' +
            '<a href="/logs?container=' + encodeURIComponent(identifier) + '" class="btn btn-sm btn-outline-info me-1" title="Ver logs"><i class="bi bi-journal-text"></i></a>' +
            '<button class="btn btn-sm btn-outline-success me-1 container-action-button" data-action="start" data-identifier="' + escapeHtml(identifier) + '" title="Iniciar"><i class="bi bi-play-fill"></i></button>' +
            '<button class="btn btn-sm btn-outline-warning me-1 container-action-button" data-action="restart" data-identifier="' + escapeHtml(identifier) + '" title="Reiniciar"><i class="bi bi-arrow-clockwise"></i></button>' +
            '<button class="btn btn-sm btn-outline-danger container-action-button" data-action="stop" data-identifier="' + escapeHtml(identifier) + '" title="Parar"><i class="bi bi-stop-fill"></i></button>' +
            '</td>' +
            '</tr>';
    }
    tableBodyElement.innerHTML = htmlContentString;

    tableBodyElement.querySelectorAll('.container-action-button').forEach(function(button) {
        button.addEventListener('click', function(eventObject) {
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

    var csrfToken = document.querySelector('meta[name="_csrf"]').content;
    setRowButtonsDisabled(rowElement, true);

    fetch('/api/docker/' + actionString + '/' + encodeURIComponent(identifierString), {
        method: 'POST',
        headers: { 'X-CSRF-TOKEN': csrfToken }
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
        console.log('[ServerDash Containers] Inscrito em /topic/docker (WebSocket)');
        stompClient.subscribe('/topic/docker', function(messageObject) {
            var parsedDataObject = JSON.parse(messageObject.body);
            _hasReceivedData = true;
            window.lastContainers = parsedDataObject || [];
            updateDockerBanner(window.lastContainers);
            renderDockerContainers();
        });
    },
    heartbeat: { incoming: 10000, outgoing: 10000 }
});
