/**
 * Tela da própria conta: troca de senha e ciclo de vida do 2FA (enrollment, códigos de
 * recuperação e desativação).
 */
(function () {
    'use strict';

    function showFeedback(element, message, isError) {
        element.textContent = message;
        element.classList.remove('d-none', 'alert-danger', 'alert-success');
        element.classList.add(isError ? 'alert-danger' : 'alert-success');
    }

    function hide(element) {
        element.classList.add('d-none');
    }

    /**
     * A API responde sempre em JSON, com {error: ...} nas recusas de regra de negócio (400).
     */
    function requestJson(url, method, body) {
        return fetchWithCsrf(url, {
            method: method,
            headers: { 'Content-Type': 'application/json' },
            body: body ? JSON.stringify(body) : undefined
        }).then(function (response) {
            return response.json().catch(function () { return {}; }).then(function (payload) {
                if (!response.ok) {
                    throw new Error(payload.error || 'Falha na operação (HTTP ' + response.status + ')');
                }
                return payload;
            });
        });
    }

    function renderRecoveryCodes(codes) {
        var panel = document.getElementById('recoveryCodesPanel');
        document.getElementById('recoveryCodesList').textContent = codes.join('\n');
        panel.classList.remove('d-none');
    }

    // --- Troca de senha ---------------------------------------------------
    var changePasswordForm = document.getElementById('changePasswordForm');
    if (changePasswordForm) {
        changePasswordForm.addEventListener('submit', function (event) {
            event.preventDefault();
            var feedback = document.getElementById('passwordFeedback');
            requestJson('/api/account/password', 'PUT', {
                currentPassword: document.getElementById('currentPassword').value,
                newPassword: document.getElementById('newPassword').value
            }).then(function () {
                changePasswordForm.reset();
                showFeedback(feedback, 'Senha alterada com sucesso.', false);
            }).catch(function (error) {
                showFeedback(feedback, error.message, true);
            });
        });
    }

    // --- Enrollment de 2FA ------------------------------------------------
    var beginEnrollmentBtn = document.getElementById('beginEnrollmentBtn');
    if (beginEnrollmentBtn) {
        beginEnrollmentBtn.addEventListener('click', function () {
            var feedback = document.getElementById('twoFactorFeedback');
            hide(feedback);
            requestJson('/api/account/2fa/enrollment', 'POST').then(function (payload) {
                document.getElementById('enrollmentQrCode').src = payload.qrCode;
                document.getElementById('enrollmentSecret').textContent = payload.secret;
                document.getElementById('enrollmentPanel').classList.remove('d-none');
            }).catch(function (error) {
                showFeedback(feedback, error.message, true);
            });
        });
    }

    var confirmEnrollmentForm = document.getElementById('confirmEnrollmentForm');
    if (confirmEnrollmentForm) {
        confirmEnrollmentForm.addEventListener('submit', function (event) {
            event.preventDefault();
            var feedback = document.getElementById('twoFactorFeedback');
            requestJson('/api/account/2fa', 'POST', {
                code: document.getElementById('enrollmentCode').value
            }).then(function (payload) {
                document.getElementById('twoFactorSetup').classList.add('d-none');
                showFeedback(feedback, '2FA ativado. O próximo login vai pedir o código.', false);
                renderRecoveryCodes(payload.recoveryCodes);
            }).catch(function (error) {
                showFeedback(feedback, error.message, true);
            });
        });
    }

    // --- Conta com 2FA já ativo -------------------------------------------
    var regenerateCodesBtn = document.getElementById('regenerateCodesBtn');
    if (regenerateCodesBtn) {
        regenerateCodesBtn.addEventListener('click', function () {
            var feedback = document.getElementById('twoFactorFeedback');
            requestJson('/api/account/2fa/recovery-codes', 'POST', {
                currentPassword: document.getElementById('twoFactorPassword').value
            }).then(function (payload) {
                document.getElementById('twoFactorPassword').value = '';
                document.getElementById('unusedRecoveryCodes').textContent = payload.recoveryCodes.length;
                showFeedback(feedback, 'Códigos anteriores invalidados. Guarde os novos.', false);
                renderRecoveryCodes(payload.recoveryCodes);
            }).catch(function (error) {
                showFeedback(feedback, error.message, true);
            });
        });
    }

    var disableTwoFactorBtn = document.getElementById('disableTwoFactorBtn');
    if (disableTwoFactorBtn) {
        disableTwoFactorBtn.addEventListener('click', function () {
            if (!window.confirm('Desativar o 2FA desta conta? A proteção do segundo fator será perdida.')) {
                return;
            }
            var feedback = document.getElementById('twoFactorFeedback');
            requestJson('/api/account/2fa', 'DELETE', {
                currentPassword: document.getElementById('twoFactorPassword').value
            }).then(function () {
                window.location.reload();
            }).catch(function (error) {
                showFeedback(feedback, error.message, true);
            });
        });
    }
})();
