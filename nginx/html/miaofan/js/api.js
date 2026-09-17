/* =====================================================================
   请求层
   - 统一走 nginx 的 /api 反向代理到后端 /admin
   - 统一带上 token, 统一处理 401 与业务错误码
   ===================================================================== */
window.MF = window.MF || {};

(function () {
    var BASE = '/api';

    function readCookie(name) {
        var match = document.cookie.match(new RegExp('(?:^|;\\s*)' + name + '=([^;]*)'));
        return match ? decodeURIComponent(match[1]) : '';
    }

    function writeCookie(name, value, days) {
        var expires = new Date(Date.now() + (days || 1) * 864e5).toUTCString();
        document.cookie = name + '=' + encodeURIComponent(value) + '; expires=' + expires + '; path=/';
    }

    function clearCookie(name) {
        document.cookie = name + '=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
    }

    var session = {
        get token() { return readCookie('token'); },
        get name() { return readCookie('name') || '管理员'; },
        save: function (token, name) {
            writeCookie('token', token, 1);
            if (name) {
                writeCookie('name', name, 1);
            }
        },
        clear: function () {
            clearCookie('token');
            clearCookie('name');
        },
        isLoggedIn: function () {
            return !!readCookie('token');
        }
    };

    function buildUrl(path, params) {
        if (!params) {
            return BASE + path;
        }
        var query = Object.keys(params)
            .filter(function (key) {
                var value = params[key];
                return value !== undefined && value !== null && value !== '';
            })
            .map(function (key) {
                return encodeURIComponent(key) + '=' + encodeURIComponent(params[key]);
            })
            .join('&');
        return BASE + path + (query ? '?' + query : '');
    }

    function handle(response) {
        if (response.status === 401) {
            session.clear();
            window.location.hash = '#/login';
            throw new Error('登录已过期，请重新登录');
        }
        return response.json().then(function (data) {
            if (data && data.code !== 1) {
                throw new Error(data.msg || '请求失败');
            }
            return data ? data.data : null;
        });
    }

    function request(method, path, options) {
        options = options || {};
        var headers = {};
        var token = session.token;
        if (token) {
            headers['token'] = token;
        }
        var init = { method: method, headers: headers };
        if (options.body instanceof FormData) {
            init.body = options.body;
        } else if (options.body !== undefined) {
            headers['Content-Type'] = 'application/json';
            init.body = JSON.stringify(options.body);
        }
        return fetch(buildUrl(path, options.params), init).then(handle);
    }

    MF.session = session;
    MF.http = {
        get: function (path, params) { return request('GET', path, { params: params }); },
        post: function (path, body, params) { return request('POST', path, { body: body, params: params }); },
        put: function (path, body) { return request('PUT', path, { body: body }); },
        del: function (path, params) { return request('DELETE', path, { params: params }); },
        upload: function (file) {
            var form = new FormData();
            form.append('file', file);
            return request('POST', '/common/upload', { body: form });
        }
    };
})();
