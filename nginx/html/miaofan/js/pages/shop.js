/* 店铺状态 */
window.MF = window.MF || {};
MF.pages = MF.pages || {};

(function () {
    function render(root, status) {
        var open = status === 1;
        root.innerHTML = ''
            + '<div class="card" style="max-width:620px">'
            + '  <div class="card-head"><h3>营业状态</h3>'
            + (open ? MF.ui.tag('营业中', 'tag-green') : MF.ui.tag('已打烊', 'tag-gray'))
            + '  </div>'
            + '  <p class="muted" style="margin-top:0">店铺打烊后，用户端将无法下单，管理端仍可正常操作。</p>'
            + '  <div class="field-row" style="margin-top:20px">'
            + '    <button class="btn ' + (open ? 'btn-primary' : 'btn-outline') + '" data-status="1" '
            + (open ? 'disabled' : '') + '>开始营业</button>'
            + '    <button class="btn ' + (!open ? 'btn-primary' : 'btn-outline') + '" data-status="0" '
            + (!open ? 'disabled' : '') + '>打烊休息</button>'
            + '  </div>'
            + '</div>';

        root.querySelectorAll('button[data-status]').forEach(function (button) {
            button.addEventListener('click', function () {
                var target = Number(button.getAttribute('data-status'));
                button.disabled = true;
                MF.http.put('/shop/' + target, {}).then(function () {
                    MF.ui.toast(target === 1 ? '已开始营业' : '已打烊', 'success');
                    if (MF.router.syncShopStatus) {
                        MF.router.syncShopStatus(target);
                    }
                    render(root, target);
                }).catch(function (error) {
                    button.disabled = false;
                    MF.ui.toast(error.message, 'error');
                });
            });
        });
    }

    MF.pages.shop = {
        title: '店铺设置',
        subtitle: '营业状态与店铺信息',
        render: function (root) {
            root.innerHTML = '<div class="loading">加载中…</div>';
            MF.http.get('/shop/status').then(function (status) {
                render(root, Number(status));
            }).catch(function (error) {
                root.innerHTML = '<div class="card"><div class="empty">' + MF.ui.escape(error.message) + '</div></div>';
            });
        }
    };
})();
