var webSocketConnection = new SockJS('/ws');
var stompClientConnection = Stomp.over(webSocketConnection);
stompClientConnection.debug = null;

function renderDockerContainers(containerList) {
    var tableBodyElement = document.getElementById('containersTableBody');
    tableBodyElement.innerHTML = '';

    if (!containerList || containerList.length === 0) {
        tableBodyElement.innerHTML = '<tr><td colspan="5" class="text-center text-white-50 py-5">Nenhum container encontrado ou Docker indisponível.</td></tr>';
        return;
    }

    var htmlContentString = '';
    for (var indexNumber = 0; indexNumber < containerList.length; indexNumber++) {
        var currentContainer = containerList[indexNumber];
        var isRunningBoolean = currentContainer.containerState.toLowerCase() === 'running';
        var stateClassString = isRunningBoolean ? 'text-success' : 'text-danger';
        var stateIconString = isRunningBoolean ? 'bi-play-circle-fill' : 'bi-stop-circle-fill';

        htmlContentString += '<tr class="align-middle">' +
            '<td class="ps-4 py-2"><span class="text-white fw-bold">' + currentContainer.containerName + '</span><br><small class="text-white-50 font-monospace">' + currentContainer.containerIdentifier + '</small></td>' +
            '<td class="py-2"><span class="' + stateClassString + ' fw-semibold"><i class="bi ' + stateIconString + ' me-1"></i>' + currentContainer.containerState + '</span><br><small class="text-white-50">' + currentContainer.containerStatus + '</small></td>' +
            '<td class="py-2 text-end font-monospace text-white-50">' + currentContainer.cpuPercentage + '</td>' +
            '<td class="py-2 text-end font-monospace text-white-50">' + currentContainer.memoryPercentage + '</td>' +
            '<td class="py-2 text-end pe-4">' +
            '<button class="btn btn-sm btn-outline-success me-1 container-action-button" data-action="start" data-identifier="' + currentContainer.containerIdentifier + '" title="Iniciar"><i class="bi bi-play-fill"></i></button>' +
            '<button class="btn btn-sm btn-outline-warning me-1 container-action-button" data-action="restart" data-identifier="' + currentContainer.containerIdentifier + '" title="Reiniciar"><i class="bi bi-arrow-clockwise"></i></button>' +
            '<button class="btn btn-sm btn-outline-danger container-action-button" data-action="stop" data-identifier="' + currentContainer.containerIdentifier + '" title="Parar"><i class="bi bi-stop-fill"></i></button>' +
            '</td>' +
            '</tr>';
    }
    tableBodyElement.innerHTML = htmlContentString;

    var actionButtonElements = document.querySelectorAll('.container-action-button');
    for (var buttonIndex = 0; buttonIndex < actionButtonElements.length; buttonIndex++) {
        var currentButton = actionButtonElements[buttonIndex];
        currentButton.addEventListener('click', function(eventObject) {
            var buttonTarget = eventObject.currentTarget;
            var actionString = buttonTarget.getAttribute('data-action');
            var identifierString = buttonTarget.getAttribute('data-identifier');
            executeContainerAction(actionString, identifierString);
        });
    }
}

function executeContainerAction(actionString, identifierString) {
    if (!confirm('Deseja executar a ação "' + actionString + '" no container ' + identifierString + '?')) {
        return;
    }

    fetch('/api/docker/' + actionString + '/' + identifierString, {
        method: 'POST'
    }).then(function(responseObject) {
        if (responseObject.ok) {
            // A interface será atualizada no próximo pulso do WebSocket
        } else {
            alert('Falha ao executar a ação no container.');
        }
    }).catch(function(errorObject) {
        alert('Erro de comunicação com o servidor: ' + errorObject);
    });
}

stompClientConnection.connect({}, function (frameObject) {
    stompClientConnection.subscribe('/topic/docker', function (messageObject) {
        var parsedDataObject = JSON.parse(messageObject.body);
        renderDockerContainers(parsedDataObject);
    });
});