/* 订单管理 */
window.MF = window.MF || {};
MF.pages = MF.pages || {};

(function () {
    var STATUS = {
        1: { text: '待付款', cls: 'tag-amber' },
        2: { text: '待接单', cls: 'tag-accent' },
        3: { text: '待派送', cls: 'tag-accent' },
        4: { text: '派送中', cls: 'tag-accent' },
        5: { text: '已完成', cls: 'tag-green' },
        6: { text: '已取消', cls: 'tag-gray' }
    };

    var state = { page: 1, pageSize: 10, status: '', number: '', phone: '' };

    function actionButtons(row) {
        var ops = [];
        if (row.status === 2) {
            ops.push('<button class="btn btn-sm btn-primary" data-act="confirm" data-id="' + row.id + '">接单</button>');
            ops.push('<button class="btn btn-sm btn-outline" data-act="reject" data-id="' + row.id + '">拒单</button>');
        }
        if (row.status === 3) {
            ops.push('<button class="btn btn-sm btn-primary" data-act="delivery" data-id="' + row.id + '">派送</button>');
        }
        if (row.status === 4) {
            ops.push('<button class="btn btn-sm btn-primary" data-act="complete" data-id="' + row.id + '">完成</button>');
        }
        if (row.status === 1 || row.status === 2) {
            ops.push('<button class="btn btn-sm btn-danger-text" data-act="cancel" data-id="' + row.id + '">取消</button>');
        }
        ops.push('<button class="btn btn-sm btn-outline" data-act="detail" data-id="' + row.id + '">详情</button>');
        return ops.join('');
    }

    function load(root) {
        return MF.http.get('/order/conditionSearch', {
            page: state.page,
            pageSize: state.pageSize,
            status: state.status,
            number: state.number,
            phone: state.phone
        }).then(function (data) {
            var rows = (data.records || []).map(function (row) {
                var status = STATUS[row.status] || { text: row.status, cls: 'tag-gray' };
                return {
                    id: row.id,
                    number: '<div class="table-title">' + MF.ui.escape(row.number) + '</div>',
                    customer: MF.ui.escape(row.consignee || '-') + '<br><span class="muted-sm">'
                        + MF.ui.escape(row.phone || '') + '</span>',
                    dishes: '<span class="muted">' + MF.ui.escape(row.orderDishes || '-') + '</span>',
                    amount: '<span class="price">' + MF.ui.money(row.amount) + '</span>',
                    time: '<span class="muted-sm">' + MF.ui.datetime(row.orderTime) + '</span>',
                    status: MF.ui.tag(status.text, status.cls),
                    ops: actionButtons(row)
                };
            });

            root.querySelector('#orderTable').innerHTML = MF.ui.table([
                { title: '订单号', key: 'number' },
                { title: '客户', key: 'customer' },
                { title: '商品', key: 'dishes' },
                { title: '金额', key: 'amount', className: 'num' },
                { title: '下单时间', key: 'time' },
                { title: '状态', key: 'status' },
                { title: '操作', key: 'ops', className: 'ops' }
            ], rows);

            var pager = root.querySelector('#orderPager');
            pager.innerHTML = MF.ui.pager(data.total || 0, state.page, state.pageSize, function (page) {
                state.page = page;
                load(root);
            });
            MF.ui.pager.bind(pager);
        });
    }

    function showDetail(id) {
        MF.http.get('/order/details/' + id).then(function (order) {
            var items = (order.orderDetailList || []).map(function (item) {
                return '<div class="list-row"><span>' + MF.ui.escape(item.name) + ' × ' + item.number
                    + '</span><strong>' + MF.ui.money(item.amount) + '</strong></div>';
            }).join('');

            MF.ui.modal({
                title: '订单详情',
                okText: '知道了',
                cancelText: '关闭',
                body: ''
                    + '<div class="list">'
                    + '<div class="list-row"><span class="muted">订单号</span><strong>' + MF.ui.escape(order.number) + '</strong></div>'
                    + '<div class="list-row"><span class="muted">收货人</span><span>' + MF.ui.escape(order.consignee || '-')
                    + '　' + MF.ui.escape(order.phone || '') + '</span></div>'
                    + '<div class="list-row"><span class="muted">地址</span><span>' + MF.ui.escape(order.address || '-') + '</span></div>'
                    + '<div class="list-row"><span class="muted">下单时间</span><span>' + MF.ui.datetime(order.orderTime) + '</span></div>'
                    + '<div class="list-row"><span class="muted">备注</span><span>' + MF.ui.escape(order.remark || '无') + '</span></div>'
                    + '</div>'
                    + '<div class="card-head" style="margin:20px 0 6px"><h3>商品明细</h3></div>'
                    + '<div class="list">' + (items || '<div class="empty">无明细</div>') + '</div>'
                    + '<div class="card-head" style="margin:16px 0 6px"><h3>合计</h3></div>'
                    + '<div class="list"><div class="list-row"><span class="muted">实收金额</span>'
                    + '<strong class="price">' + MF.ui.money(order.amount) + '</strong></div></div>'
            });
        }).catch(function (error) {
            MF.ui.toast(error.message, 'error');
        });
    }

    function loadStatusCounts(root) {
        MF.http.get('/order/statistics').then(function (data) {
            root.querySelector('#countToBeConfirmed').textContent = data.toBeConfirmed || 0;
            root.querySelector('#countConfirmed').textContent = data.confirmed || 0;
            root.querySelector('#countDelivery').textContent = data.deliveryInProgress || 0;
        }).catch(function () {
            /* 统计失败不影响列表 */
        });
    }

    MF.pages.order = {
        title: '订单管理',
        subtitle: '接单、派送与订单查询',
        render: function (root) {
            root.innerHTML = ''
                + '<div class="grid-3">'
                + '  <div class="stat"><div class="stat-label">待接单</div><div class="stat-value accent" id="countToBeConfirmed">-</div></div>'
                + '  <div class="stat"><div class="stat-label">待派送</div><div class="stat-value" id="countConfirmed">-</div></div>'
                + '  <div class="stat"><div class="stat-label">派送中</div><div class="stat-value" id="countDelivery">-</div></div>'
                + '</div>'
                + '<div class="card" style="margin-top:20px">'
                + '  <div class="toolbar">'
                + '    <div class="toolbar-left">'
                + '      <div class="segmented" id="orderStatus">'
                + '        <button data-status="" class="active">全部</button>'
                + '        <button data-status="2">待接单</button>'
                + '        <button data-status="3">待派送</button>'
                + '        <button data-status="4">派送中</button>'
                + '        <button data-status="5">已完成</button>'
                + '        <button data-status="6">已取消</button>'
                + '      </div>'
                + '    </div>'
                + '    <div class="toolbar-right">'
                + '      <input class="input" id="orderNumber" placeholder="订单号">'
                + '      <input class="input" id="orderPhone" placeholder="手机号">'
                + '      <button class="btn btn-outline" id="orderSearch">查询</button>'
                + '    </div>'
                + '  </div>'
                + '  <div id="orderTable"><div class="loading">加载中…</div></div>'
                + '  <div id="orderPager"></div>'
                + '</div>';

            root.querySelector('#orderStatus').addEventListener('click', function (event) {
                var button = event.target.closest('button[data-status]');
                if (!button) {
                    return;
                }
                root.querySelectorAll('#orderStatus button').forEach(function (node) {
                    node.classList.remove('active');
                });
                button.classList.add('active');
                state.status = button.getAttribute('data-status');
                state.page = 1;
                load(root);
            });

            root.querySelector('#orderSearch').addEventListener('click', function () {
                state.number = root.querySelector('#orderNumber').value.trim();
                state.phone = root.querySelector('#orderPhone').value.trim();
                state.page = 1;
                load(root);
            });

            root.querySelector('#orderTable').addEventListener('click', function (event) {
                var button = event.target.closest('button[data-act]');
                if (!button) {
                    return;
                }
                var id = Number(button.getAttribute('data-id'));
                var action = button.getAttribute('data-act');

                if (action === 'detail') {
                    showDetail(id);
                    return;
                }

                if (action === 'confirm') {
                    MF.http.put('/order/confirm', { id: id }).then(function () {
                        MF.ui.toast('已接单', 'success');
                        load(root);
                        loadStatusCounts(root);
                    }).catch(function (error) { MF.ui.toast(error.message, 'error'); });
                }

                if (action === 'delivery') {
                    MF.http.put('/order/delivery/' + id, {}).then(function () {
                        MF.ui.toast('已开始派送', 'success');
                        load(root);
                        loadStatusCounts(root);
                    }).catch(function (error) { MF.ui.toast(error.message, 'error'); });
                }

                if (action === 'complete') {
                    MF.http.put('/order/complete/' + id, {}).then(function () {
                        MF.ui.toast('订单已完成', 'success');
                        load(root);
                        loadStatusCounts(root);
                    }).catch(function (error) { MF.ui.toast(error.message, 'error'); });
                }

                if (action === 'reject' || action === 'cancel') {
                    var isReject = action === 'reject';
                    var dialog = MF.ui.modal({
                        title: isReject ? '拒单' : '取消订单',
                        okText: '确定',
                        body: '<label class="field"><span class="field-label">'
                            + (isReject ? '拒单原因' : '取消原因') + '<span class="req">*</span></span>'
                            + '<input class="input" id="f-reason" placeholder="例如：'
                            + (isReject ? '菜品已售完' : '客户要求取消') + '"></label>',
                        onSubmit: function (close, mask) {
                            var reason = mask.querySelector('#f-reason').value.trim();
                            if (!reason) {
                                MF.ui.toast('请填写原因', 'error');
                                return;
                            }
                            var body = isReject
                                ? { id: id, rejectionReason: reason }
                                : { id: id, cancelReason: reason };
                            MF.http.put(isReject ? '/order/rejection' : '/order/cancel', body).then(function () {
                                MF.ui.toast(isReject ? '已拒单' : '订单已取消', 'success');
                                close();
                                load(root);
                                loadStatusCounts(root);
                            }).catch(function (error) {
                                MF.ui.toast(error.message, 'error');
                            });
                        }
                    });
                    void dialog;
                }
            });

            load(root);
            loadStatusCounts(root);
        }
    };
})();
