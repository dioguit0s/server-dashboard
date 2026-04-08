function escapeHtml(text) {
    if (text == null || text === '') return '';
    var div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function renderDockerContainers(containerList) {
    var tableBodyElement = document.getElementById('containersTableBody');
    tableBodyElement.innerHTML = '';

    if (!containerList || containerList.length === 0) {
        tableBodyElement.innerHTML = '<tr><td colspan="5" class="text-center text-white-50 py-5">Nenhum container encontrado ou Docker indisponível.</td></tr>';
        return;
    }

    var fragment = document.createDocumentFragment();
    for (var indexNumber = 0; indexNumber < containerList.length; indexNumber++) {
        var currentContainer = containerList[indexNumber];
        var isRunningBoolean = currentContainer.containerState.toLowerCase() === 'running';
        var stateClassString = isRunningBoolean ? 'text-disk-accent' : 'text-danger';
        var stateIconString = isRunningBoolean ? 'bi-play-circle-fill' : 'bi-stop-circle-fill';

        var tr = document.createElement('tr');
        tr.className = 'align-middle';

        var tdName = document.createElement('td');
        tdName.className = 'ps-4 py-2';
        var nameSpan = document.createElement('span');
        nameSpan.className = 'text-white fw-medium';
        nameSpan.textContent = currentContainer.containerName || '';
        tdName.appendChild(nameSpan);
        tdName.appendChild(document.createElement('br'));
        var idSmall = document.createElement('small');
        idSmall.className = 'text-white-50 font-monospace';
        idSmall.textContent = currentContainer.containerIdentifier || '';
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
        ['start', 'restart', 'stop'].forEach(function(action) {
            var btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'btn btn-sm me-1 container-action-button ' +
                (action === 'start' ? 'btn-outline-success' : action === 'restart' ? 'btn-outline-primary' : 'btn-outline-danger');
            btn.setAttribute('data-action', action);
            btn.setAttribute('data-identifier', currentContainer.containerIdentifier || '');
            btn.title = action === 'start' ? 'Iniciar' : action === 'restart' ? 'Reiniciar' : 'Parar';
            var icon = document.createElement('i');
            icon.className = 'bi ' + (action === 'start' ? 'bi-play-fill' : action === 'restart' ? 'bi-arrow-clockwise' : 'bi-stop-fill');
            btn.appendChild(icon);
            tdActions.appendChild(btn);
        });
        tr.appendChild(tdActions);

        fragment.appendChild(tr);
    }
    tableBodyElement.appendChild(fragment);

    tableBodyElement.querySelectorAll('.container-action-button').forEach(function(btn) {
        btn.addEventListener('click', function(eventObject) {
            var buttonTarget = eventObject.currentTarget;
            var actionString = buttonTarget.getAttribute('data-action');
            var identifierString = buttonTarget.getAttribute('data-identifier');
            executeContainerAction(actionString, identifierString);
        });
    });
}

function executeContainerAction(actionString, identifierString) {
    if (!confirm('Deseja executar a ação "' + actionString + '" no container ' + identifierString + '?')) {
        return;
    }

    fetchWithCsrf('/api/docker/' + encodeURIComponent(actionString) + '/' + encodeURIComponent(identifierString), {
        method: 'POST'
    }).then(function(responseObject) {
        if (responseObject.ok) {
            // Atualização no próximo pulso do WebSocket
        } else {
            alert('Falha ao executar a ação no container.');
        }
    }).catch(function(errorObject) {
        alert('Erro de comunicação com o servidor: ' + errorObject);
    });
}

StompReconnect.connect({
    onConnect: function(stompClient) {
        stompClient.subscribe('/topic/docker', function (messageObject) {
            var parsedDataObject = JSON.parse(messageObject.body);
            renderDockerContainers(parsedDataObject);
        });
    },
    heartbeat: { incoming: 10000, outgoing: 10000 }
});
