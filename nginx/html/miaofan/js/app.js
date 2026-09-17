/* =====================================================================
   秒饭管理平台 · 应用骨架
   hash 路由 + 侧边栏 + 顶栏, 页面模块放在 js/pages/ 下
   ===================================================================== */
window.MF = window.MF || {};
MF.pages = MF.pages || {};

/**
 * 调色辅助: 在地址后面加 ?accent=色值 就能临时换主色, 不用改代码。
 * 例: /?accent=FF9500  ?accent=F97316  ?accent=EA580C  ?accent=FB923C
 * 深色态与浅色底由主色自动推算, 方便对比时保持整体协调。
 */
(function applyAccentFromQuery() {
    var value = (new URLSearchParams(window.location.search).get('accent') || '').replace('#', '').trim();
    if (!/^[0-9a-fA-F]{6}$/.test(value)) {
        return;
    }
    var r = parseInt(value.slice(0, 2), 16);
    var g = parseInt(value.slice(2, 4), 16);
    var b = parseInt(value.slice(4, 6), 16);
    function hex(channel) {
        var out = Math.max(0, Math.round(channel * 0.82)).toString(16);
        return out.length === 1 ? '0' + out : out;
    }
    var root = document.documentElement.style;
    root.setProperty('--accent', '#' + value);
    root.setProperty('--accent-strong', '#' + hex(r) + hex(g) + hex(b));
    root.setProperty('--accent-soft', 'rgba(' + r + ',' + g + ',' + b + ',.12)');
})();

(function () {
    var NAV = [
        {
            group: '经营',
            items: [
                { key: 'dashboard', title: '工作台' },
                { key: 'order', title: '订单管理' },
                { key: 'seckill', title: '限时抢购', badge: '新' }
            ]
        },
        {
            group: '商品',
            items: [
                { key: 'category', title: '分类管理' },
                { key: 'dish', title: '菜品管理' },
                { key: 'setmeal', title: '套餐管理' }
            ]
        },
        {
            group: '管理',
            items: [
                { key: 'employee', title: '员工管理' },
                { key: 'shop', title: '店铺设置' }
            ]
        }
    ];

    var DEFAULT_ROUTE = 'dashboard';

    function currentRoute() {
        var hash = window.location.hash.replace(/^#\/?/, '');
        return hash || DEFAULT_ROUTE;
    }

    function renderShell() {
        var navHtml = NAV.map(function (group) {
            return '<div class="nav-group">' + group.group + '</div>'
                + group.items.map(function (item) {
                    return '<button class="nav-item" data-route="' + item.key + '">'
                        + '<span>' + item.title + '</span>'
                        + (item.badge ? '<span class="nav-badge">' + item.badge + '</span>' : '')
                        + '</button>';
                }).join('');
        }).join('');

        document.body.innerHTML = ''
            + '<div class="layout">'
            + '  <aside class="sidebar">'
            + '    <div class="sidebar-brand">'
            + '      <img src="assets/logo.svg" alt="秒饭">'
            + '      <strong>秒饭管理平台</strong>'
            + '    </div>'
            + '    <nav class="nav" id="nav">' + navHtml + '</nav>'
            + '    <div class="sidebar-foot">'
            + '      <button class="nav-item" id="logoutBtn"><span>退出登录</span></button>'
            + '    </div>'
            + '  </aside>'
            + '  <div class="main">'
            + '    <header class="topbar">'
            + '      <div>'
            + '        <h2 id="pageTitle">工作台</h2>'
            + '        <div class="topbar-sub" id="pageSubtitle"></div>'
            + '      </div>'
            + '      <div class="topbar-right">'
            + '        <button class="topbar-status" id="shopStatusBtn" title="点击切换营业状态">'
            + '          <span class="dot"></span><span id="shopStatusText">营业状态</span></button>'
            + '        <div class="user-chip"><span class="avatar" id="userAvatar">管</span>'
            + '          <span id="userName">管理员</span></div>'
            + '      </div>'
            + '    </header>'
            + '    <main class="content" id="content"></main>'
            + '  </div>'
            + '</div>';

        var name = MF.session.name;
        document.getElementById('userName').textContent = name;
        document.getElementById('userAvatar').textContent = name.slice(0, 1);

        document.getElementById('nav').addEventListener('click', function (event) {
            var button = event.target.closest('button[data-route]');
            if (button) {
                window.location.hash = '#/' + button.getAttribute('data-route');
            }
        });

        document.getElementById('logoutBtn').addEventListener('click', function () {
            MF.ui.confirm('确定要退出登录吗？', '退出').then(function (ok) {
                if (!ok) {
                    return;
                }
                MF.http.post('/employee/logout', {}).catch(function () {
                    /* 后端退出失败也要清掉本地登录态 */
                }).finally(function () {
                    MF.session.clear();
                    window.location.hash = '#/login';
                });
            });
        });

        bindShopStatus();
    }

    /**
     * 顶栏营业状态快捷键: 显示当前状态, 点击即可切换, 不用再进店铺设置页
     */
    function bindShopStatus() {
        var button = document.getElementById('shopStatusBtn');
        var text = document.getElementById('shopStatusText');
        if (!button) {
            return;
        }

        function paint(status) {
            var open = status === 1;
            button.classList.toggle('closed', !open);
            text.textContent = open ? '营业中' : '已打烊';
            button.setAttribute('data-status', String(open ? 1 : 0));
            button.disabled = false;
        }

        MF.http.get('/shop/status').then(paint).catch(function () {
            text.textContent = '营业状态';
        });

        // 暴露给店铺设置页, 让那里切换状态时顶栏能同步
        MF.router.syncShopStatus = paint;

        button.addEventListener('click', function () {
            var current = Number(button.getAttribute('data-status'));
            var target = current === 1 ? 0 : 1;
            var question = target === 1 ? '确定开始营业吗？用户端将恢复下单。' : '确定打烊吗？打烊后用户端无法下单。';
            MF.ui.confirm(question, target === 1 ? '开始营业' : '打烊').then(function (ok) {
                if (!ok) {
                    return;
                }
                button.disabled = true;
                MF.http.put('/shop/' + target, {}).then(function () {
                    MF.ui.toast(target === 1 ? '已开始营业' : '已打烊', 'success');
                    paint(target);
                }).catch(function (error) {
                    button.disabled = false;
                    MF.ui.toast(error.message, 'error');
                });
            });
        });
    }

    var shellRendered = false;

    function render() {
        var route = currentRoute();

        if (route === 'login') {
            shellRendered = false;
            document.body.innerHTML = '<div id="content"></div>';
            MF.pages.login.render(document.getElementById('content'));
            return;
        }

        if (!MF.session.isLoggedIn()) {
            window.location.hash = '#/login';
            return;
        }

        var page = MF.pages[route];
        if (!page) {
            window.location.hash = '#/' + DEFAULT_ROUTE;
            return;
        }

        if (!shellRendered) {
            renderShell();
            shellRendered = true;
        }

        document.querySelectorAll('.nav-item[data-route]').forEach(function (node) {
            node.classList.toggle('active', node.getAttribute('data-route') === route);
        });
        document.getElementById('pageTitle').textContent = page.title || '';
        document.getElementById('pageSubtitle').textContent = page.subtitle || '';

        var content = document.getElementById('content');
        content.innerHTML = '';
        page.render(content);
    }

    MF.router = { current: currentRoute, render: render };

    window.addEventListener('hashchange', render);
    window.addEventListener('DOMContentLoaded', function () {
        if (!window.location.hash && MF.session.isLoggedIn()) {
            window.location.hash = '#/' + DEFAULT_ROUTE;
        }
        render();
    });
})();
