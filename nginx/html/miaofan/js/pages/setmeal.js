/* 套餐管理: 列表 + 新增 / 编辑(含菜品选择) */
window.MF = window.MF || {};
MF.pages = MF.pages || {};

(function () {
    var state = { page: 1, pageSize: 10, name: '', categoryId: '', status: '' };
    var setmealCategories = [];
    var dishCategories = [];

    function load(root) {
        return MF.http.get('/setmeal/page', {
            page: state.page,
            pageSize: state.pageSize,
            name: state.name,
            categoryId: state.categoryId,
            status: state.status
        }).then(function (data) {
            var rows = (data.records || []).map(function (setmeal) {
                return {
                    name: '<div class="cell-flex">'
                        + (setmeal.image ? '<img class="thumb" src="' + MF.ui.escape(setmeal.image) + '" alt="">' : '')
                        + '<div><div class="table-title">' + MF.ui.escape(setmeal.name) + '</div>'
                        + '<div class="muted-sm">' + MF.ui.escape(setmeal.description || '') + '</div></div></div>',
                    category: MF.ui.escape(setmeal.categoryName || '-'),
                    price: '<span class="price">' + MF.ui.money(setmeal.price) + '</span>',
                    status: setmeal.status === 1 ? MF.ui.tag('起售', 'tag-green') : MF.ui.tag('停售', 'tag-gray'),
                    ops: ''
                        + '<button class="btn btn-sm btn-outline" data-act="toggle" data-id="' + setmeal.id + '" data-status="'
                        + (setmeal.status === 1 ? 0 : 1) + '">' + (setmeal.status === 1 ? '停售' : '起售') + '</button>'
                        + '<button class="btn btn-sm btn-outline" data-act="edit" data-id="' + setmeal.id + '">编辑</button>'
                        + '<button class="btn btn-sm btn-danger-text" data-act="del" data-id="' + setmeal.id + '">删除</button>'
                };
            });

            root.querySelector('#setmealTable').innerHTML = MF.ui.table([
                { title: '套餐', key: 'name' },
                { title: '分类', key: 'category' },
                { title: '售价', key: 'price', className: 'num' },
                { title: '状态', key: 'status' },
                { title: '操作', key: 'ops', className: 'ops' }
            ], rows);

            var pager = root.querySelector('#setmealPager');
            pager.innerHTML = MF.ui.pager(data.total || 0, state.page, state.pageSize, function (page) {
                state.page = page;
                load(root);
            });
            MF.ui.pager.bind(pager);
        });
    }

    /** 加载菜品分类及其菜品, 供套餐选菜使用 */
    function loadDishGroups() {
        return MF.http.get('/category/list', { type: 1 }).then(function (categories) {
            dishCategories = categories || [];
            return Promise.all(dishCategories.map(function (category) {
                return MF.http.get('/dish/list', { categoryId: category.id }).then(function (dishes) {
                    return { category: category, dishes: dishes || [] };
                });
            }));
        });
    }

    function openForm(root, setmeal) {
        var isEdit = !!setmeal;

        Promise.all([loadDishGroups(), MF.http.get('/category/list', { type: 2 })])
            .then(function (result) {
                var groups = result[0] || [];
                setmealCategories = result[1] || [];

                var picked = {};
                if (isEdit) {
                    (setmeal.setmealDishes || []).forEach(function (item) {
                        picked[item.dishId] = item.copies || 1;
                    });
                }

                var categoryOptions = setmealCategories.map(function (item) {
                    return '<option value="' + item.id + '"'
                        + (isEdit && String(setmeal.categoryId) === String(item.id) ? ' selected' : '') + '>'
                        + MF.ui.escape(item.name) + '</option>';
                }).join('');

                var dishGroupsHtml = groups.map(function (group) {
                    if (!group.dishes.length) {
                        return '';
                    }
                    return '<div class="pick-group">'
                        + '<div class="pick-title">' + MF.ui.escape(group.category.name) + '</div>'
                        + group.dishes.map(function (dish) {
                            var checked = picked[dish.id] ? ' checked' : '';
                            var copies = picked[dish.id] || 1;
                            return '<label class="pick-row">'
                                + '<input type="checkbox" value="' + dish.id + '" data-name="'
                                + MF.ui.escape(dish.name) + '" data-price="' + dish.price + '"' + checked + '>'
                                + '<span class="pick-name">' + MF.ui.escape(dish.name) + '</span>'
                                + '<span class="pick-price">' + MF.ui.money(dish.price) + '</span>'
                                + '<input class="pick-copies" type="number" min="1" value="' + copies + '" title="份数">'
                                + '</label>';
                        }).join('')
                        + '</div>';
                }).join('');

                MF.ui.modal({
                    title: isEdit ? '编辑套餐' : '新增套餐',
                    okText: isEdit ? '保存修改' : '创建套餐',
                    body: ''
                        + '<label class="field"><span class="field-label">套餐名称<span class="req">*</span></span>'
                        + '<input class="input" id="f-sm-name" maxlength="32" value="'
                        + MF.ui.escape(isEdit ? setmeal.name : '') + '"></label>'
                        + '<div class="field-row">'
                        + '  <label class="field"><span class="field-label">所属分类<span class="req">*</span></span>'
                        + '  <select class="select" id="f-sm-category"><option value="">请选择</option>'
                        + categoryOptions + '</select></label>'
                        + '  <label class="field"><span class="field-label">套餐价格（元）<span class="req">*</span></span>'
                        + '  <input class="input" id="f-sm-price" type="number" step="0.01" min="0.01" value="'
                        + (isEdit ? setmeal.price : '') + '"></label>'
                        + '</div>'
                        + '<label class="field"><span class="field-label">图片</span>'
                        + '  <div class="cell-flex"><img class="thumb" id="f-sm-preview" src="'
                        + (isEdit && setmeal.image ? MF.ui.escape(setmeal.image) : 'assets/logo.svg') + '" alt="">'
                        + '  <input class="input" id="f-sm-image" type="file" accept="image/*"></div></label>'
                        + '<label class="field"><span class="field-label">描述</span>'
                        + '<textarea class="textarea" id="f-sm-desc" maxlength="200">'
                        + MF.ui.escape(isEdit ? (setmeal.description || '') : '') + '</textarea></label>'
                        + '<label class="field"><span class="field-label">状态</span>'
                        + '<select class="select" id="f-sm-status">'
                        + '<option value="1"' + (!isEdit || setmeal.status === 1 ? ' selected' : '') + '>起售</option>'
                        + '<option value="0"' + (isEdit && setmeal.status === 0 ? ' selected' : '') + '>停售</option>'
                        + '</select></label>'
                        + '<div class="field"><span class="field-label">包含菜品<span class="req">*</span>'
                        + '（勾选后可调整份数）</span>'
                        + '<div class="pick-box">' + (dishGroupsHtml || '<div class="empty">暂无菜品</div>') + '</div></div>',
                    onSubmit: function (close, mask) {
                        var dishes = [];
                        mask.querySelectorAll('.pick-row input[type=checkbox]:checked').forEach(function (box) {
                            var copiesInput = box.parentNode.querySelector('.pick-copies');
                            dishes.push({
                                dishId: Number(box.value),
                                name: box.getAttribute('data-name'),
                                price: Number(box.getAttribute('data-price')),
                                copies: Number(copiesInput && copiesInput.value) || 1
                            });
                        });

                        var payload = {
                            id: isEdit ? setmeal.id : undefined,
                            name: mask.querySelector('#f-sm-name').value.trim(),
                            categoryId: Number(mask.querySelector('#f-sm-category').value) || null,
                            price: Number(mask.querySelector('#f-sm-price').value) || null,
                            description: mask.querySelector('#f-sm-desc').value.trim(),
                            status: Number(mask.querySelector('#f-sm-status').value),
                            image: isEdit ? setmeal.image : '',
                            setmealDishes: dishes
                        };

                        if (!payload.name || !payload.categoryId || !payload.price) {
                            MF.ui.toast('请填写名称、分类与价格', 'error');
                            return;
                        }
                        if (!dishes.length) {
                            MF.ui.toast('请至少选择一个菜品', 'error');
                            return;
                        }

                        var file = mask.querySelector('#f-sm-image').files[0];
                        uploadImage(file, payload.image)
                            .then(function (url) {
                                if (url) {
                                    payload.image = url;
                                }
                                return isEdit ? MF.http.put('/setmeal', payload) : MF.http.post('/setmeal', payload);
                            })
                            .then(function () {
                                MF.ui.toast(isEdit ? '修改成功' : '创建成功', 'success');
                                close();
                                load(root);
                            })
                            .catch(function (error) {
                                MF.ui.toast(error.message, 'error');
                            });
                    }
                });
            });
    }

    /**
     * 图片上传: 没选文件就沿用原图; 上传失败也不阻断保存, 只提示一次
     * (上传走的是自己的 OSS, 网络或配置异常时不应该导致整条数据存不下来)
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

    MF.pages.setmeal = {
        title: '套餐管理',
        subtitle: '套餐组合与上下架',
        render: function (root) {
            root.innerHTML = ''
                + '<div class="card">'
                + '  <div class="toolbar">'
                + '    <div class="toolbar-left">'
                + '      <input class="input" id="setmealKeyword" placeholder="搜索套餐名称">'
                + '      <select class="select" id="setmealCategory"><option value="">全部分类</option></select>'
                + '      <select class="select" id="setmealStatus">'
                + '        <option value="">全部状态</option><option value="1">起售</option><option value="0">停售</option>'
                + '      </select>'
                + '      <button class="btn btn-outline" id="setmealSearch">查询</button>'
                + '    </div>'
                + '    <div class="toolbar-right">'
                + '      <button class="btn btn-primary" id="setmealCreate">+ 新增套餐</button>'
                + '    </div>'
                + '  </div>'
                + '  <div id="setmealTable"><div class="loading">加载中…</div></div>'
                + '  <div id="setmealPager"></div>'
                + '</div>';

            var categorySelect = root.querySelector('#setmealCategory');

            root.querySelector('#setmealSearch').addEventListener('click', function () {
                state.name = root.querySelector('#setmealKeyword').value.trim();
                state.categoryId = categorySelect.value;
                state.status = root.querySelector('#setmealStatus').value;
                state.page = 1;
                load(root);
            });

            root.querySelector('#setmealCreate').addEventListener('click', function () {
                openForm(root, null);
            });

            root.querySelector('#setmealTable').addEventListener('click', function (event) {
                var button = event.target.closest('button[data-act]');
                if (!button) {
                    return;
                }
                var id = Number(button.getAttribute('data-id'));
                var action = button.getAttribute('data-act');

                if (action === 'edit') {
                    MF.http.get('/setmeal/' + id).then(function (setmeal) {
                        openForm(root, setmeal);
                    }).catch(function (error) {
                        MF.ui.toast(error.message, 'error');
                    });
                }
                if (action === 'toggle') {
                    var status = Number(button.getAttribute('data-status'));
                    MF.http.post('/setmeal/status/' + status, null, { id: id }).then(function () {
                        MF.ui.toast('状态已更新', 'success');
                        load(root);
                    }).catch(function (error) { MF.ui.toast(error.message, 'error'); });
                }
                if (action === 'del') {
                    MF.ui.confirm('确定删除该套餐吗？', '删除').then(function (ok) {
                        if (!ok) {
                            return;
                        }
                        MF.http.del('/setmeal', { ids: id }).then(function () {
                            MF.ui.toast('已删除', 'success');
                            load(root);
                        }).catch(function (error) { MF.ui.toast(error.message, 'error'); });
                    });
                }
            });

            MF.http.get('/category/list', { type: 2 }).then(function (list) {
                setmealCategories = list || [];
                categorySelect.innerHTML = '<option value="">全部分类</option>' + setmealCategories.map(function (item) {
                    return '<option value="' + item.id + '">' + MF.ui.escape(item.name) + '</option>';
                }).join('');
                load(root);
            });
        }
    };
})();
