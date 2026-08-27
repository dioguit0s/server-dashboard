/**
 * Tela de gestão de usuários (somente ADMIN): listagem, criação, troca de papéis,
 * habilitar/desabilitar, redefinição de senha, desligamento de 2FA e remoção.
 *
 * As recusas de regra de negócio — como remover o último admin ativo — chegam do servidor como
 * 400 com {error: ...} e são exibidas como estão.
 */
(function () {
    'use strict';

    var feedbackElement = document.getElementById('usersFeedback');

    function showFeedback(message, isError) {
        feedbackElement.textContent = message;
        feedbackElement.classList.remove('d-none', 'alert-danger', 'alert-success');
        feedbackElement.classList.add(isError ? 'alert-danger' : 'alert-success');
    }

    function requestJson(url, method, body) {
        return fetchWithCsrf(url, {
            method: method,
            headers: { 'Content-Type': 'application/json' },
            body: body ? JSON.stringify(body) : undefined
        }).then(function (response) {
            if (response.status === 204) {
                return {};
            }
            return response.json().catch(function () { return {}; }).then(function (payload) {
                if (!response.ok) {
                    throw new Error(payload.error || 'Falha na operação (HTTP ' + response.status + ')');
                }
                return payload;
            });
        });
    }

    /**
     * Monta a linha via DOM em vez de innerHTML: o nome do usuário é conteúdo controlado por quem
     * criou a conta, e não deve ser interpretado como HTML.
     */
    function buildRow(user) {
        var row = document.createElement('tr');

        var usernameCell = document.createElement('td');
        usernameCell.textContent = user.username;
        row.appendChild(usernameCell);

        var rolesCell = document.createElement('td');
        var roleSelect = document.createElement('select');
        roleSelect.className = 'form-select form-select-sm form-control-theme';
        roleSelect.style.maxWidth = '160px';
        [['VIEWER'], ['VIEWER', 'ADMIN']].forEach(function (roles) {
            var option = document.createElement('option');
            option.value = roles.join(',');
            option.textContent = roles.indexOf('ADMIN') >= 0 ? 'ADMIN' : 'VIEWER';
            option.selected = (user.roles.indexOf('ADMIN') >= 0) === (roles.indexOf('ADMIN') >= 0);
            roleSelect.appendChild(option);
        });
        roleSelect.addEventListener('change', function () {
            requestJson('/api/admin/users/' + user.identifier + '/roles', 'PUT', {
                roles: roleSelect.value.split(',')
            }).then(function () {
                showFeedback('Papéis de ' + user.username + ' atualizados.', false);
                loadUsers();
            }).catch(function (error) {
                showFeedback(error.message, true);
                loadUsers();
            });
        });
        rolesCell.appendChild(roleSelect);
        row.appendChild(rolesCell);

        var enabledCell = document.createElement('td');
        var enabledBadge = document.createElement('span');
        enabledBadge.className = user.enabled ? 'badge badge-signal' : 'badge bg-secondary';
        enabledBadge.textContent = user.enabled ? 'ativo' : 'desabilitado';
        enabledCell.appendChild(enabledBadge);
        row.appendChild(enabledCell);

        var totpCell = document.createElement('td');
        totpCell.textContent = user.totpEnabled
            ? 'ativo (' + user.unusedRecoveryCodes + ' códigos)'
            : 'inativo';
        row.appendChild(totpCell);

        var actionsCell = document.createElement('td');
        actionsCell.className = 'text-end';

        actionsCell.appendChild(buildButton(user.enabled ? 'Desabilitar' : 'Habilitar', 'btn-secondary', function () {
            requestJson('/api/admin/users/' + user.identifier + '/enabled', 'PUT', { enabled: !user.enabled })
                .then(function () {
                    showFeedback('Usuário ' + user.username + (user.enabled ? ' desabilitado.' : ' habilitado.'), false);
                    loadUsers();
                })
                .catch(function (error) { showFeedback(error.message, true); });
        }));

        actionsCell.appendChild(buildButton('Redefinir senha', 'btn-secondary', function () {
            var newPassword = window.prompt('Nova senha para ' + user.username + ' (mínimo 8 caracteres):');
            if (!newPassword) {
                return;
            }
            requestJson('/api/admin/users/' + user.identifier + '/password', 'PUT', { password: newPassword })
                .then(function () { showFeedback('Senha de ' + user.username + ' redefinida.', false); })
                .catch(function (error) { showFeedback(error.message, true); });
        }));

        if (user.totpEnabled) {
            actionsCell.appendChild(buildButton('Desligar 2FA', 'btn-outline-warning', function () {
                if (!window.confirm('Desligar o 2FA de ' + user.username + '? A conta volta a exigir só a senha.')) {
                    return;
                }
                requestJson('/api/admin/users/' + user.identifier + '/two-factor', 'DELETE')
                    .then(function () {
                        showFeedback('2FA de ' + user.username + ' desligado.', false);
                        loadUsers();
                    })
                    .catch(function (error) { showFeedback(error.message, true); });
            }));
        }

        actionsCell.appendChild(buildButton('Remover', 'btn-outline-danger', function () {
            if (!window.confirm('Remover o usuário ' + user.username + '? A ação não pode ser desfeita.')) {
                return;
            }
            requestJson('/api/admin/users/' + user.identifier, 'DELETE')
                .then(function () {
                    showFeedback('Usuário ' + user.username + ' removido.', false);
                    loadUsers();
                })
                .catch(function (error) { showFeedback(error.message, true); });
        }));

        row.appendChild(actionsCell);
        return row;
    }

    function buildButton(label, styleClass, onClick) {
        var button = document.createElement('button');
        button.type = 'button';
        button.className = 'btn btn-sm ms-1 ' + styleClass;
        button.textContent = label;
        button.addEventListener('click', onClick);
        return button;
    }

    function loadUsers() {
        requestJson('/api/admin/users', 'GET').then(function (users) {
            var tableBody = document.getElementById('usersTableBody');
            tableBody.replaceChildren();
            users.forEach(function (user) {
                tableBody.appendChild(buildRow(user));
            });
        }).catch(function (error) {
            showFeedback(error.message, true);
        });
    }

    document.getElementById('addUserBtn').addEventListener('click', function () {
        var errorElement = document.getElementById('addUserError');
        var roles = [];
        if (document.getElementById('newUserRoleViewer').checked) {
            roles.push('VIEWER');
        }
        if (document.getElementById('newUserRoleAdmin').checked) {
            roles.push('ADMIN');
        }

        requestJson('/api/admin/users', 'POST', {
            username: document.getElementById('newUsername').value,
            password: document.getElementById('newUserPassword').value,
            roles: roles
        }).then(function (created) {
            errorElement.classList.add('d-none');
            document.getElementById('addUserForm').reset();
            bootstrap.Modal.getInstance(document.getElementById('addUserModal')).hide();
            showFeedback('Usuário ' + created.username + ' criado.', false);
            loadUsers();
        }).catch(function (error) {
            errorElement.textContent = error.message;
            errorElement.classList.remove('d-none');
        });
    });

    loadUsers();
})();
