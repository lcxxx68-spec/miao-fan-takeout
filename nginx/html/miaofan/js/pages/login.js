/* 登录页 */
window.MF = window.MF || {};
MF.pages = MF.pages || {};

(function () {
    MF.pages.login = {
        fullscreen: true,
        render: function (root) {
            root.innerHTML = ''
                + '<div class="login-page">'
                + '  <div class="login-card">'
                + '    <div class="login-brand">'
                + '      <img src="assets/logo.svg" alt="秒饭">'
                + '      <h1>秒饭管理平台</h1>'
                + '      <p>限时抢购 · 订单 · 会员经营</p>'
                + '    </div>'
                + '    <form class="login-form" id="loginForm">'
                + '      <label class="field"><span class="field-label">账号</span>'
                + '        <input class="input" id="username" autocomplete="username" placeholder="请输入账号"></label>'
                + '      <label class="field"><span class="field-label">密码</span>'
                + '        <input class="input" id="password" type="password" autocomplete="current-password" placeholder="请输入密码"></label>'
                + '      <button class="btn btn-primary" type="submit" id="loginBtn" style="height:42px">登录</button>'
                + '    </form>'
                + '    <p class="login-tip">默认账号 admin / 123456</p>'
                + '  </div>'
                + '</div>';

            var form = root.querySelector('#loginForm');
            var button = root.querySelector('#loginBtn');

            form.addEventListener('submit', function (event) {
                event.preventDefault();
                var username = root.querySelector('#username').value.trim();
                var password = root.querySelector('#password').value.trim();
                if (!username || !password) {
                    MF.ui.toast('请输入账号和密码', 'error');
                    return;
                }
                button.disabled = true;
                button.textContent = '登录中…';

                MF.http.post('/employee/login', { username: username, password: password })
                    .then(function (data) {
                        MF.session.save(data.token, data.name || data.userName);
                        MF.ui.toast('欢迎回来，' + (data.name || data.userName), 'success');
                        window.location.hash = '#/dashboard';
                    })
                    .catch(function (error) {
                        MF.ui.toast(error.message, 'error');
                    })
                    .finally(function () {
                        button.disabled = false;
                        button.textContent = '登录';
                    });
            });
        }
    };
})();
