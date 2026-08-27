/**
 * Lê o token CSRF do cookie (CookieCsrfTokenRepository.withHttpOnly(false)).
 * Spring Security usa por padrão o header X-XSRF-TOKEN.
 */
function getCsrfHeaders() {
    var name = 'XSRF-TOKEN=';
    var parts = document.cookie.split(';');
    for (var i = 0; i < parts.length; i++) {
        var c = parts[i].trim();
        if (c.indexOf(name) === 0) {
            var raw = c.substring(name.length);
            var token = decodeURIComponent(raw);
            return { 'X-XSRF-TOKEN': token };
        }
    }
    return {};
}

function fetchWithCsrf(url, options) {
    options = options || {};
    var headers = new Headers(options.headers || {});
    var csrf = getCsrfHeaders();
    Object.keys(csrf).forEach(function(k) {
        headers.set(k, csrf[k]);
    });
    options.headers = headers;
    if (!options.credentials) {
        options.credentials = 'same-origin';
    }
    return fetch(url, options);
}

/**
 * Indica se o usuário logado tem permissão de escrita (ROLE_ADMIN). O servidor emite a meta
 * `_can_write` apenas para admins — ver o fragmento `head-assets`.
 *
 * Serve só para não exibir controles que resultariam em 403: a autorização real acontece no
 * servidor, nas regras de rota e nos @PreAuthorize.
 */
function canWrite() {
    return document.querySelector('meta[name="_can_write"]') !== null;
}
