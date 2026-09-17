/* 员工管理 */
window.MF = window.MF || {};
MF.pages = MF.pages || {};

(function () {
    var state = { page: 1, pageSize: 10, name: '' };

    function load(root) {
        return MF.http.get('/employee/page', {
            page: state.page,
            pageSize: state.pageSize,
            name: state.name
        }).then(function (data) {
            var rows = (data.records || []).map(function (item) {
                return {
                    name: '<div class="cell-flex"><span class="avatar">'
                        + MF.ui.escape((item.name || '员').slice(0, 1)) + '</span>'
                        + '<div><div class="table-title">' + MF.ui.escape(item.name) + '</div>'
                        + '<div class="muted-sm">' + MF.ui.escape(item.username) + '</div></div></div>',
                    phone: MF.ui.escape(item.phone || '-'),
                    updated: '<span class="muted-sm">' + MF.ui.datetime(item.updateTime) + '</span>',
                    status: item.status === 1 ? MF.ui.tag('启用', 'tag-green') : MF.ui.tag('禁用', 'tag-gray'),
                    ops: ''
                        + '<button class="btn btn-sm btn-outline" data-act="toggle" data-id="' + item.id + '" data-status="'
                        + (item.status === 1 ? 0 : 1) + '">' + (item.status === 1 ? '禁用' : '启用') + '</button>'
                        + '<button class="btn btn-sm btn-outline" data-act="edit" data-id="' + item.id + '">编辑</button>'
                };
            });

            root.querySelector('#employeeTable').innerHTML = MF.ui.table([
                { title: '员工', key: 'name' },
                { title: '手机号', key: 'phone' },
                { title: '更新时间', key: 'updated' },
                { title: '状态', key: 'status' },
                { title: '操作', key: 'ops', className: 'ops' }
            ], rows);

            var pager = root.querySelector('#employeePager');
            pager.innerHTML = MF.ui.pager(data.total || 0, state.page, state.pageSize, function (page) {
                state.page = page;
                load(root);
            });
            MF.ui.pager.bind(pager);
        });
    }

    function openForm(root, item) {
        var isEdit = !!item;
        MF.ui.modal({
            title: isEdit ? '编辑员工' : '新增员工',
            body: ''
                + '<div class="field-row">'
                + '  <label class="field"><span class="field-label">账号<span class="req">*</span></span>'
                + '  <input class="input" id="f-emp-username" value="' + MF.ui.escape(isEdit ? item.username : '') + '"'
                + (isEdit ? ' disabled' : '') + '></label>'
                + '  <label class="field"><span class="field-label">姓名<span class="req">*</span></span>'
                + '  <input class="input" id="f-emp-name" value="' + MF.ui.escape(isEdit ? item.name : '') + '"></label>'
                + '</div>'
                + '<div class="field-row">'
                + '  <label class="field"><span class="field-label">手机号</span>'
                + '  <input class="input" id="f-emp-phone" value="' + MF.ui.escape(isEdit ? (item.phone || '') : '') + '"></label>'
                + '  <label class="field"><span class="field-label">性别</span>'
                + '  <select class="select" id="f-emp-sex">'
                + '    <option value="1"' + (isEdit && item.sex === '0' ? '' : ' selected') + '>男</option>'
                + '    <option value="0"' + (isEdit && item.sex === '0' ? ' selected' : '') + '>女</option>'
                + '  </select></label>'
                + '</div>'
                + '<label class="field"><span class="field-label">身份证号</span>'
                + '<input class="input" id="f-emp-id" value="' + MF.ui.escape(isEdit ? (item.idNumber || '') : '') + '"></label>',
            onSubmit: function (close, mask) {
                var payload = {
                    id: isEdit ? item.id : undefined,
                    username: mask.querySelector('#f-emp-username').value.trim(),
                    name: mask.querySelector('#f-emp-name').value.trim(),
                    phone: mask.querySelector('#f-emp-phone').value.trim(),
                    sex: mask.querySelector('#f-emp-sex').value,
                    idNumber: mask.querySelector('#f-emp-id').value.trim()
                };
                if (!payload.username || !payload.name) {
                    MF.ui.toast('请填写账号与姓名', 'error');
                    return;
                }
                var action = isEdit ? MF.http.put('/employee', payload) : MF.http.post('/employee', payload);
                action.then(function () {
                    MF.ui.toast(isEdit ? '修改成功' : '新增成功，初始密码 123456', 'success');
                    close();
                    load(root);
                }).catch(function (error) {
                    MF.ui.toast(error.message, 'error');
                });
            }
        });
    }

    MF.pages.employee = {
        title: '员工管理',
        subtitle: '账号与权限状态',
        render: function (root) {
            root.innerHTML = ''
                + '<div class="card">'
                + '  <div class="toolbar">'
                + '    <div class="toolbar-left">'
                + '      <input class="input" id="employeeKeyword" placeholder="搜索员工姓名">'
                + '      <button class="btn btn-outline" id="employeeSearch">查询</button>'
                + '    </div>'
                + '    <div class="toolbar-right">'
                + '      <button class="btn btn-primary" id="employeeCreate">+ 新增员工</button>'
                + '    </div>'
                + '  </div>'
                + '  <div id="employeeTable"><div class="loading">加载中…</div></div>'
                + '  <div id="employeePager"></div>'
                + '</div>';

            root.querySelector('#employeeSearch').addEventListener('click', function () {
                state.name = root.querySelector('#employeeKeyword').value.trim();
                state.page = 1;
                load(root);
            });

            root.querySelector('#employeeCreate').addEventListener('click', function () {
                openForm(root, null);
            });

            root.querySelector('#employeeTable').addEventListener('click', function (event) {
                var button = event.target.closest('button[data-act]');
                if (!button) {
                    return;
                }
                var id = Number(button.getAttribute('data-id'));
                var action = button.getAttribute('data-act');

                if (action === 'toggle') {
                    var status = Number(button.getAttribute('data-status'));
                    if (status === 0 && !confirm('禁用后该账号将无法登录，确定继续吗？')) {
                        return;
                    }
                    MF.http.post('/employee/status/' + status, null, { id: id }).then(function () {
                        MF.ui.toast('状态已更新', 'success');
                        load(root);
                    }).catch(function (error) { MF.ui.toast(error.message, 'error'); });
                }
                if (action === 'edit') {
                    MF.http.get('/employee/' + id).then(function (item) {
                        openForm(root, item);
                    }).catch(function (error) { MF.ui.toast(error.message, 'error'); });
                }
            });

            load(root);
        }
    };
})();
