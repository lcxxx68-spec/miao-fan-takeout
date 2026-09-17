/* 菜品管理 */
window.MF = window.MF || {};
MF.pages = MF.pages || {};

(function () {
    var state = { page: 1, pageSize: 10, name: '', categoryId: '', status: '' };
    var categories = [];

    function loadCategories() {
        return MF.http.get('/category/list', { type: 1 }).then(function (list) {
            categories = list || [];
            return categories;
        });
    }

    function load(root) {
        return MF.http.get('/dish/page', {
            page: state.page,
            pageSize: state.pageSize,
            name: state.name,
            categoryId: state.categoryId,
            status: state.status
        }).then(function (data) {
            var rows = (data.records || []).map(function (dish) {
                return {
                    name: '<div class="cell-flex">'
                        + (dish.image ? '<img class="thumb" src="' + MF.ui.escape(dish.image) + '" alt="">' : '')
                        + '<div><div class="table-title">' + MF.ui.escape(dish.name) + '</div>'
                        + '<div class="muted-sm">' + MF.ui.escape(dish.description || '') + '</div></div></div>',
                    category: MF.ui.escape(dish.categoryName || '-'),
                    price: '<span class="price">' + MF.ui.money(dish.price) + '</span>',
                    status: dish.status === 1 ? MF.ui.tag('起售', 'tag-green') : MF.ui.tag('停售', 'tag-gray'),
                    updated: '<span class="muted-sm">' + MF.ui.datetime(dish.updateTime) + '</span>',
                    ops: ''
                        + '<button class="btn btn-sm btn-outline" data-act="toggle" data-id="' + dish.id + '" data-status="'
                        + (dish.status === 1 ? 0 : 1) + '">' + (dish.status === 1 ? '停售' : '起售') + '</button>'
                        + '<button class="btn btn-sm btn-outline" data-act="edit" data-id="' + dish.id + '">编辑</button>'
                        + '<button class="btn btn-sm btn-danger-text" data-act="del" data-id="' + dish.id + '">删除</button>'
                };
            });

            root.querySelector('#dishTable').innerHTML = MF.ui.table([
                { title: '菜品', key: 'name' },
                { title: '分类', key: 'category' },
                { title: '售价', key: 'price', className: 'num' },
                { title: '状态', key: 'status' },
                { title: '更新时间', key: 'updated' },
                { title: '操作', key: 'ops', className: 'ops' }
            ], rows);

            var pager = root.querySelector('#dishPager');
            pager.innerHTML = MF.ui.pager(data.total || 0, state.page, state.pageSize, function (page) {
                state.page = page;
                load(root);
            });
            MF.ui.pager.bind(pager);
        });
    }

    function openForm(root, dish) {
        var isEdit = !!dish;
        var options = categories.map(function (item) {
            return '<option value="' + item.id + '"'
                + (isEdit && String(dish.categoryId) === String(item.id) ? ' selected' : '') + '>'
                + MF.ui.escape(item.name) + '</option>';
        }).join('');

        MF.ui.modal({
            title: isEdit ? '编辑菜品' : '新增菜品',
            body: ''
                + '<label class="field"><span class="field-label">菜品名称<span class="req">*</span></span>'
                + '<input class="input" id="f-dish-name" maxlength="32" value="'
                + MF.ui.escape(isEdit ? dish.name : '') + '"></label>'
                + '<div class="field-row">'
                + '  <label class="field"><span class="field-label">所属分类<span class="req">*</span></span>'
                + '  <select class="select" id="f-dish-category"><option value="">请选择</option>' + options + '</select></label>'
                + '  <label class="field"><span class="field-label">售价（元）<span class="req">*</span></span>'
                + '  <input class="input" id="f-dish-price" type="number" step="0.01" min="0.01" value="'
                + (isEdit ? dish.price : '') + '"></label>'
                + '</div>'
                + '<label class="field"><span class="field-label">图片</span>'
                + '<div class="cell-flex"><img class="thumb" id="f-dish-preview" src="'
                + (isEdit && dish.image ? MF.ui.escape(dish.image) : 'assets/logo.svg') + '" alt="">'
                + '<input class="input" id="f-dish-image" type="file" accept="image/*"></div></label>'
                + '<label class="field"><span class="field-label">描述</span>'
                + '<textarea class="textarea" id="f-dish-desc" maxlength="200">'
                + MF.ui.escape(isEdit ? (dish.description || '') : '') + '</textarea></label>'
                + '  <label class="field"><span class="field-label">状态</span>'
                + '  <select class="select" id="f-dish-status">'
                + '    <option value="1"' + (!isEdit || dish.status === 1 ? ' selected' : '') + '>起售</option>'
                + '    <option value="0"' + (isEdit && dish.status === 0 ? ' selected' : '') + '>停售</option>'
                + '  </select></label>',
            onSubmit: function (close, mask) {
                var payload = {
                    id: isEdit ? dish.id : undefined,
                    name: mask.querySelector('#f-dish-name').value.trim(),
                    categoryId: Number(mask.querySelector('#f-dish-category').value) || null,
                    price: Number(mask.querySelector('#f-dish-price').value) || null,
                    description: mask.querySelector('#f-dish-desc').value.trim(),
                    status: Number(mask.querySelector('#f-dish-status').value),
                    image: isEdit ? dish.image : '',
                    flavors: isEdit ? (dish.flavors || []) : []
                };
                if (!payload.name || !payload.categoryId || !payload.price) {
                    MF.ui.toast('请填写名称、分类与售价', 'error');
                    return;
                }
                var file = mask.querySelector('#f-dish-image').files[0];
                uploadImage(file, payload.image).then(function (url) {
                    if (url) {
                        payload.image = url;
                    }
                    return isEdit ? MF.http.put('/dish', payload) : MF.http.post('/dish', payload);
                }).then(function () {
                    MF.ui.toast(isEdit ? '修改成功' : '新增成功', 'success');
                    close();
                    load(root);
                }).catch(function (error) {
                    MF.ui.toast(error.message, 'error');
                });
            }
        });
    }

    /**
     * 图片上传: 未选文件则沿用原图; 上传失败不阻断保存,
     * 否则 OSS 一旦不可用, 整条菜品数据都存不下来
     */
    function uploadImage(file, fallback) {
        if (!file) {
            return Promise.resolve(fallback || '');
        }
        return MF.http.upload(file).catch(function () {
            MF.ui.toast('图片上传失败, 已保留原图', 'error');
            return fallback || '';
        });
    }

    MF.pages.dish = {
        title: '菜品管理',
        subtitle: '菜品上下架与基础信息',
        render: function (root) {
            root.innerHTML = ''
                + '<div class="card">'
                + '  <div class="toolbar">'
                + '    <div class="toolbar-left">'
                + '      <input class="input" id="dishKeyword" placeholder="搜索菜品名称">'
                + '      <select class="select" id="dishCategory"><option value="">全部分类</option></select>'
                + '      <select class="select" id="dishStatus">'
                + '        <option value="">全部状态</option><option value="1">起售</option><option value="0">停售</option>'
                + '      </select>'
                + '      <button class="btn btn-outline" id="dishSearch">查询</button>'
                + '    </div>'
                + '    <div class="toolbar-right">'
                + '      <button class="btn btn-primary" id="dishCreate">+ 新增菜品</button>'
                + '    </div>'
                + '  </div>'
                + '  <div id="dishTable"><div class="loading">加载中…</div></div>'
                + '  <div id="dishPager"></div>'
                + '</div>';

            var categorySelect = root.querySelector('#dishCategory');

            root.querySelector('#dishSearch').addEventListener('click', function () {
                state.name = root.querySelector('#dishKeyword').value.trim();
                state.categoryId = categorySelect.value;
                state.status = root.querySelector('#dishStatus').value;
                state.page = 1;
                load(root);
            });

            root.querySelector('#dishCreate').addEventListener('click', function () {
                openForm(root, null);
            });

            root.querySelector('#dishTable').addEventListener('click', function (event) {
                var button = event.target.closest('button[data-act]');
                if (!button) {
                    return;
                }
                var id = Number(button.getAttribute('data-id'));
                var action = button.getAttribute('data-act');

                if (action === 'toggle') {
                    var status = Number(button.getAttribute('data-status'));
                    MF.http.post('/dish/status/' + status, null, { id: id }).then(function () {
                        MF.ui.toast('状态已更新', 'success');
                        load(root);
                    }).catch(function (error) { MF.ui.toast(error.message, 'error'); });
                }
                if (action === 'edit') {
                    MF.http.get('/dish/' + id).then(function (dish) {
                        openForm(root, dish);
                    }).catch(function (error) { MF.ui.toast(error.message, 'error'); });
                }
                if (action === 'del') {
                    MF.ui.confirm('确定删除该菜品吗？', '删除').then(function (ok) {
                        if (!ok) {
                            return;
                        }
                        MF.http.del('/dish', { ids: id }).then(function () {
                            MF.ui.toast('已删除', 'success');
                            load(root);
                        }).catch(function (error) { MF.ui.toast(error.message, 'error'); });
                    });
                }
            });

            loadCategories().then(function (list) {
                categorySelect.innerHTML = '<option value="">全部分类</option>' + list.map(function (item) {
                    return '<option value="' + item.id + '">' + MF.ui.escape(item.name) + '</option>';
                }).join('');
                load(root);
            });
        }
    };
})();
