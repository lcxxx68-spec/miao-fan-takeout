/* 限时抢购管理 */
window.MF = window.MF || {};
MF.pages = MF.pages || {};

(function () {
    var state = { page: 1, pageSize: 10, name: '', status: '' };
    var timer = null;

    function statusTag(item) {
        if (item.status === 2) {
            return MF.ui.tag('已结束', 'tag-gray');
        }
        if (item.status === 0) {
            return MF.ui.tag('未上架', 'tag-gray');
        }
        var now = Date.now();
        var start = item.startTime ? new Date(String(item.startTime).replace(/-/g, '/')).getTime() : 0;
        var end = item.endTime ? new Date(String(item.endTime).replace(/-/g, '/')).getTime() : 0;
        if (start && now < start) {
            return MF.ui.tag('未开始', 'tag-amber');
        }
        if (end && now > end) {
            return MF.ui.tag('已结束', 'tag-gray');
        }
        return MF.ui.tag('进行中', 'tag-accent');
    }

    function remainHtml(item) {
        var remain = item.remainStock;
        if (remain === undefined || remain === null) {
            return '<span class="muted-sm">未预热</span>';
        }
        if (remain === 0) {
            return '<span class="price">0</span>';
        }
        return remain <= 10 ? '<span class="price">' + remain + '</span>' : String(remain);
    }

    function load(root) {
        return Promise.all([
            MF.http.get('/seckill/page', {
                page: state.page,
                pageSize: state.pageSize,
                name: state.name,
                status: state.status
            }),
            MF.http.get('/seckill/page', { page: 1, pageSize: 200 })
        ]).then(function (result) {
            var pageData = result[0] || {};
            var allData = result[1] || {};
            var rows = (pageData.records || []).map(function (item) {
                var ops = [];
                if (item.status === 1) {
                    ops.push('<button class="btn btn-sm btn-outline" data-act="stop" data-id="' + item.id + '">停售</button>');
                } else if (item.status === 0) {
                    ops.push('<button class="btn btn-sm btn-primary" data-act="start" data-id="' + item.id + '">上架</button>');
                }
                if (item.status !== 2) {
                    ops.push('<button class="btn btn-sm btn-outline" data-act="edit" data-id="' + item.id + '">编辑</button>');
                }
                return {
                    name: '<div class="table-title">' + MF.ui.escape(item.name) + '</div>',
                    dish: '<div class="cell-flex">'
                        + (item.dishImage ? '<img class="thumb" src="' + MF.ui.escape(item.dishImage) + '" alt="">' : '')
                        + '<span>' + MF.ui.escape(item.dishName || '-') + '</span></div>',
                    original: '<span class="strike">' + MF.ui.money(item.originalPrice) + '</span>',
                    price: '<span class="price">' + MF.ui.money(item.seckillPrice) + '</span>',
                    stock: item.totalStock,
                    sold: item.soldStock,
                    remain: remainHtml(item),
                    time: '<span class="muted-sm">' + MF.ui.datetime(item.startTime) + '<br>'
                        + MF.ui.datetime(item.endTime) + '</span>',
                    status: statusTag(item),
                    ops: ops.join('')
                };
            });

            root.querySelector('#seckillTable').innerHTML = MF.ui.table([
                { title: '活动名称', key: 'name' },
                { title: '菜品', key: 'dish' },
                { title: '原价', key: 'original', className: 'num' },
                { title: '抢购价', key: 'price', className: 'num' },
                { title: '总库存', key: 'stock', className: 'num' },
                { title: '已售', key: 'sold', className: 'num' },
                { title: '剩余库存', key: 'remain', className: 'num' },
                { title: '活动时间', key: 'time' },
                { title: '状态', key: 'status' },
                { title: '操作', key: 'ops', className: 'ops' }
            ], rows);

            var pager = root.querySelector('#seckillPager');
            pager.innerHTML = MF.ui.pager(pageData.total || 0, state.page, state.pageSize, function (page) {
                state.page = page;
                load(root);
            });
            MF.ui.pager.bind(pager);

            var all = allData.records || [];
            var online = all.filter(function (item) { return item.status === 1; }).length;
            var stock = all.reduce(function (sum, item) { return sum + (item.totalStock || 0); }, 0);
            var sold = all.reduce(function (sum, item) { return sum + (item.soldStock || 0); }, 0);
            root.querySelector('#statTotal').textContent = allData.total || 0;
            root.querySelector('#statOnline').textContent = online;
            root.querySelector('#statStock').textContent = stock;
            root.querySelector('#statSold').textContent = sold;
        });
    }

    function loadDishOptions() {
        return MF.http.get('/dish/page', { page: 1, pageSize: 200 }).then(function (data) {
            return (data.records || []).map(function (dish) {
                return { id: dish.id, name: dish.name, price: dish.price };
            });
        });
    }

    function openForm(root, item) {
        var isEdit = !!item;
        loadDishOptions().then(function (dishes) {
            var dishOptions = dishes.map(function (dish) {
                return '<option value="' + dish.id + '" data-price="' + dish.price + '"'
                    + (isEdit && String(item.dishId) === String(dish.id) ? ' selected' : '') + '>'
                    + MF.ui.escape(dish.name) + '（原价 ' + MF.ui.money(dish.price) + '）</option>';
            }).join('');

            var start = isEdit ? MF.ui.toInputTime(item.startTime) : MF.ui.formatLocalTime(new Date());
            var end = isEdit ? MF.ui.toInputTime(item.endTime)
                : MF.ui.formatLocalTime(new Date(Date.now() + 3 * 864e5));

            MF.ui.modal({
                title: isEdit ? '编辑抢购活动' : '新增抢购活动',
                okText: isEdit ? '保存修改' : '创建活动',
                body: ''
                    + '<label class="field"><span class="field-label">活动名称<span class="req">*</span></span>'
                    + '<input class="input" id="f-name" maxlength="64" placeholder="例如：老坛酸菜鱼 限时抢" value="'
                    + MF.ui.escape(isEdit ? item.name : '') + '"></label>'
                    + '<label class="field"><span class="field-label">选择菜品<span class="req">*</span></span>'
                    + '<select class="select" id="f-dish"><option value="">请选择菜品</option>' + dishOptions + '</select></label>'
                    + '<div class="field-row">'
                    + '  <label class="field"><span class="field-label">抢购价（元）<span class="req">*</span></span>'
                    + '  <input class="input" id="f-price" type="number" step="0.01" min="0.01" value="'
                    + (isEdit ? item.seckillPrice : '') + '"></label>'
                    + '  <label class="field"><span class="field-label">活动总库存<span class="req">*</span></span>'
                    + '  <input class="input" id="f-stock" type="number" min="1" step="1" value="'
                    + (isEdit ? item.totalStock : '') + '"' + (isEdit ? ' disabled' : '') + '></label>'
                    + '  <label class="field"><span class="field-label">每人限购</span>'
                    + '  <input class="input" id="f-limit" type="number" min="1" step="1" value="'
                    + (isEdit ? item.perUserLimit : 1) + '"></label>'
                    + '</div>'
                    + '<div class="field-row">'
                    + '  <label class="field"><span class="field-label">开始时间<span class="req">*</span></span>'
                    + '  <input class="input" id="f-start" type="datetime-local" value="' + start + '"></label>'
                    + '  <label class="field"><span class="field-label">结束时间<span class="req">*</span></span>'
                    + '  <input class="input" id="f-end" type="datetime-local" value="' + end + '"></label>'
                    + '</div>'
                    + '<div class="hint-box">活动上架后，库存会预热到 Redis，抢购请求只走 Redis 预扣再异步落库。'
                    + (isEdit ? '已上架活动如需调整总库存，请先在页面停售。' : '') + '</div>',
                onSubmit: function (close, mask) {
                    var payload = {
                        id: isEdit ? item.id : undefined,
                        name: mask.querySelector('#f-name').value.trim(),
                        dishId: Number(mask.querySelector('#f-dish').value) || null,
                        seckillPrice: Number(mask.querySelector('#f-price').value) || null,
                        totalStock: Number(mask.querySelector('#f-stock').value) || null,
                        perUserLimit: Number(mask.querySelector('#f-limit').value) || 1,
                        startTime: MF.ui.toApiTime(mask.querySelector('#f-start').value),
                        endTime: MF.ui.toApiTime(mask.querySelector('#f-end').value)
                    };
                    if (!payload.name || !payload.dishId || !payload.seckillPrice || !payload.totalStock
                        || !payload.startTime || !payload.endTime) {
                        MF.ui.toast('请把活动信息填写完整', 'error');
                        return;
                    }
                    if (payload.startTime >= payload.endTime) {
                        MF.ui.toast('开始时间必须早于结束时间', 'error');
                        return;
                    }
                    var action = isEdit ? MF.http.put('/seckill', payload) : MF.http.post('/seckill', payload);
                    action.then(function () {
                        MF.ui.toast(isEdit ? '修改成功' : '创建成功', 'success');
                        close();
                        load(root);
                    }).catch(function (error) {
                        MF.ui.toast(error.message, 'error');
                    });
                }
            });

            var dishSelect = document.querySelector('#f-dish');
            var priceInput = document.querySelector('#f-price');
            if (dishSelect && priceInput) {
                dishSelect.addEventListener('change', function () {
                    var option = dishSelect.options[dishSelect.selectedIndex];
                    var price = option ? option.getAttribute('data-price') : '';
                    if (price && !priceInput.value) {
                        priceInput.value = (Number(price) * 0.7).toFixed(2);
                    }
                });
            }
        });
    }

    MF.pages.seckill = {
        title: '限时抢购',
        subtitle: '活动维护与库存监控',
        render: function (root) {
            root.innerHTML = ''
                + '<div class="grid-4">'
                + '  <div class="stat"><div class="stat-label">活动总数</div><div class="stat-value" id="statTotal">-</div></div>'
                + '  <div class="stat"><div class="stat-label">进行中</div><div class="stat-value accent" id="statOnline">-</div></div>'
                + '  <div class="stat"><div class="stat-label">总库存</div><div class="stat-value" id="statStock">-</div></div>'
                + '  <div class="stat"><div class="stat-label">已售出</div><div class="stat-value" id="statSold">-</div></div>'
                + '</div>'
                + '<div class="card" style="margin-top:20px">'
                + '  <div class="toolbar">'
                + '    <div class="toolbar-left">'
                + '      <input class="input" id="skKeyword" placeholder="搜索活动名称">'
                + '      <select class="select" id="skStatus">'
                + '        <option value="">全部状态</option>'
                + '        <option value="1">已上架</option>'
                + '        <option value="0">未上架</option>'
                + '        <option value="2">已结束</option>'
                + '      </select>'
                + '      <button class="btn btn-outline" id="skSearch">查询</button>'
                + '    </div>'
                + '    <div class="toolbar-right">'
                + '      <span class="muted-sm">每 5 秒自动刷新库存</span>'
                + '      <button class="btn btn-outline" id="skRefresh">刷新</button>'
                + '      <button class="btn btn-primary" id="skCreate">+ 新增活动</button>'
                + '    </div>'
                + '  </div>'
                + '  <div id="seckillTable"><div class="loading">加载中…</div></div>'
                + '  <div id="seckillPager"></div>'
                + '</div>';

            root.querySelector('#skSearch').addEventListener('click', function () {
                state.name = root.querySelector('#skKeyword').value.trim();
                state.status = root.querySelector('#skStatus').value;
                state.page = 1;
                load(root);
            });
            root.querySelector('#skRefresh').addEventListener('click', function () {
                load(root);
                MF.ui.toast('已刷新', 'success');
            });
            root.querySelector('#skCreate').addEventListener('click', function () {
                openForm(root, null);
            });

            root.querySelector('#seckillTable').addEventListener('click', function (event) {
                var button = event.target.closest('button[data-act]');
                if (!button) {
                    return;
                }
                var id = Number(button.getAttribute('data-id'));
                var action = button.getAttribute('data-act');

                if (action === 'start' || action === 'stop') {
                    var status = action === 'start' ? 1 : 0;
                    MF.http.post('/seckill/status/' + status, null, { id: id }).then(function () {
                        MF.ui.toast(status === 1 ? '已上架，库存已预热' : '已停售，缓存已清理', 'success');
                        load(root);
                    }).catch(function (error) {
                        MF.ui.toast(error.message, 'error');
                    });
                }
                if (action === 'edit') {
                    MF.http.get('/seckill/' + id).then(function (item) {
                        openForm(root, item);
                    }).catch(function (error) {
                        MF.ui.toast(error.message, 'error');
                    });
                }
            });

            load(root);
            clearInterval(timer);
            timer = setInterval(function () {
                if (MF.router.current() === 'seckill') {
                    load(root);
                } else {
                    clearInterval(timer);
                }
            }, 5000);
        }
    };
})();
