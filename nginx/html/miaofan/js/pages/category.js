/* 分类管理 */
window.MF = window.MF || {};
MF.pages = MF.pages || {};

(function () {
    var state = { type: 1, page: 1, pageSize: 10, name: '' };

    function load(root) {
        return MF.http.get('/category/page', {
            page: state.page,
            pageSize: state.pageSize,
            type: state.type,
            name: state.name
        }).then(function (data) {
            var rows = (data.records || []).map(function (item) {
                return {
                    id: item.id,
                    name: MF.ui.escape(item.name),
                    sort: item.sort,
                    status: item.status === 1
                        ? MF.ui.tag('启用', 'tag-green')
                        : MF.ui.tag('禁用', 'tag-gray'),
                    updated: MF.ui.datetime(item.updateTime),
                    ops: ''
                        + '<button class="btn btn-sm btn-outline" data-act="edit" data-id="' + item.id + '">编辑</button>'
                        + '<button class="btn btn-sm btn-outline" data-act="toggle" data-id="' + item.id
                        + '" data-status="' + (item.status === 1 ? 0 : 1) + '">'
                        + (item.status === 1 ? '禁用' : '启用') + '</button>'
                        + '<button class="btn btn-sm btn-danger-text" data-act="del" data-id="' + item.id + '">删除</button>'
                };
            });

            root.querySelector('#categoryTable').innerHTML = MF.ui.table([
                { title: '分类名称', key: 'name' },
                { title: '排序', key: 'sort', className: 'num' },
                { title: '状态', key: 'status' },
                { title: '更新时间', key: 'updated', className: 'muted' },
                { title: '操作', key: 'ops', className: 'ops' }
            ], rows);

            var pager = root.querySelector('#categoryPager');
            pager.innerHTML = MF.ui.pager(data.total, state.page, state.pageSize, function (page) {
                state.page = page;
                load(root);
            });
            MF.ui.pager.bind(pager);
        });
    }

    function openForm(root, item) {
        var isEdit = !!item;
        MF.ui.modal({
            title: isEdit ? '编辑分类' : '新增分类',
            body: ''
                + '<label class="field"><span class="field-label">分类名称<span class="req">*</span></span>'
                + '<input class="input" id="f-cat-name" maxlength="32" value="'
                + MF.ui.escape(isEdit ? item.name : '') + '" placeholder="例如：热销推荐"></label>'
                + '<label class="field"><span class="field-label">排序</span>'
                + '<input class="input" id="f-cat-sort" type="number" min="0" value="'
                + (isEdit ? item.sort : 0) + '"></label>'
                + '<label class="field"><span class="field-label">类型</span>'
                + '<select class="select" id="f-cat-type">'
                + '<option value="1"' + (state.type === 1 ? ' selected' : '') + '>菜品分类</option>'
                + '<option value="2"' + (state.type === 2 ? ' selected' : '') + '>套餐分类</option>'
                + '</select></label>',
            onSubmit: function (close, mask) {
                var payload = {
                    id: isEdit ? item.id : undefined,
                    name: mask.querySelector('#f-cat-name').value.trim(),
                    sort: Number(mask.querySelector('#f-cat-sort').value || 0),
                    type: Number(mask.querySelector('#f-cat-type').value)
                };
                if (!payload.name) {
                    MF.ui.toast('请填写分类名称', 'error');
                    return;
                }
                var action = isEdit ? MF.http.put('/category', payload) : MF.http.post('/category', payload);
                action.then(function () {
                    MF.ui.toast(isEdit ? '修改成功' : '新增成功', 'success');
                    close();
                    load(root);
                }).catch(function (error) {
                    MF.ui.toast(error.message, 'error');
                });
            }
        });
    }

    MF.pages.category = {
        title: '分类管理',
        subtitle: '菜品与套餐的分类维护',
        render: function (root) {
            root.innerHTML = ''
                + '<div class="card">'
                + '  <div class="toolbar">'
                + '    <div class="toolbar-left">'
                + '      <div class="segmented" id="typeSwitch">'
                + '        <button data-type="1" class="active">菜品分类</button>'
                + '        <button data-type="2">套餐分类</button>'
                + '      </div>'
                + '      <input class="input" id="categoryKeyword" placeholder="搜索分类名称">'
                + '      <button class="btn btn-outline" id="categorySearch">查询</button>'
                + '    </div>'
                + '    <div class="toolbar-right">'
                + '      <button class="btn btn-primary" id="categoryCreate">+ 新增分类</button>'
                + '    </div>'
                + '  </div>'
                + '  <div id="categoryTable"></div>'
                + '  <div id="categoryPager"></div>'
                + '</div>';

            root.querySelector('#typeSwitch').addEventListener('click', function (event) {
                var button = event.target.closest('button[data-type]');
                if (!button) {
                    return;
                }
                root.querySelectorAll('#typeSwitch button').forEach(function (node) {
                    node.classList.remove('active');
                });
                button.classList.add('active');
                state.type = Number(button.getAttribute('data-type'));
                state.page = 1;
                load(root);
            });

            root.querySelector('#categorySearch').addEventListener('click', function () {
                state.name = root.querySelector('#categoryKeyword').value.trim();
                state.page = 1;
                load(root);
            });

            root.querySelector('#categoryCreate').addEventListener('click', function () {
                openForm(root, null);
            });

            root.querySelector('#categoryTable').addEventListener('click', function (event) {
                var button = event.target.closest('button[data-act]');
                if (!button) {
                    return;
                }
                var id = Number(button.getAttribute('data-id'));
                var action = button.getAttribute('data-act');

                if (action === 'toggle') {
                    var status = Number(button.getAttribute('data-status'));
                    MF.http.post('/category/status/' + status, null, { id: id }).then(function () {
                        MF.ui.toast('状态已更新', 'success');
                        load(root);
                    }).catch(function (error) {
                        MF.ui.toast(error.message, 'error');
                    });
                }
                if (action === 'del') {
                    MF.ui.confirm('删除后不可恢复，确定删除该分类吗？', '删除').then(function (ok) {
                        if (!ok) {
                            return;
                        }
                        MF.http.del('/category', { id: id }).then(function () {
                            MF.ui.toast('已删除', 'success');
                            load(root);
                        }).catch(function (error) {
                            MF.ui.toast(error.message, 'error');
                        });
                    });
                }
                if (action === 'edit') {
                    MF.http.get('/category/list', { type: state.type }).then(function (list) {
                        var target = (list || []).filter(function (item) { return item.id === id; })[0];
                        openForm(root, target || { id: id, name: '', sort: 0 });
                    });
                }
            });

            load(root);
        }
    };
})();
