/* =====================================================================
   UI 组件层: 轻提示、确认框、弹窗、表格、分页、表单工具
   全部用原生 DOM, 不引入任何框架
   ===================================================================== */
window.MF = window.MF || {};

(function () {
    var ui = {};

    ui.escape = function (value) {
        return String(value === undefined || value === null ? '' : value).replace(/[&<>"']/g, function (c) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
        });
    };

    ui.money = function (value) {
        if (value === undefined || value === null || value === '') {
            return '-';
        }
        return '¥' + Number(value).toFixed(2);
    };

    ui.tag = function (text, cls) {
        return '<span class="tag ' + (cls || 'tag-gray') + '">' + ui.escape(text) + '</span>';
    };

    ui.datetime = function (value) {
        return value ? ui.escape(String(value).replace('T', ' ').slice(0, 16)) : '-';
    };

    /** toast 轻提示 */
    ui.toast = function (message, type) {
        var node = document.getElementById('mf-toast');
        if (!node) {
            node = document.createElement('div');
            node.id = 'mf-toast';
            document.body.appendChild(node);
        }
        node.className = 'toast ' + (type || '');
        node.textContent = message;
        node.style.display = 'block';
        clearTimeout(ui.toast.timer);
        ui.toast.timer = setTimeout(function () {
            node.style.display = 'none';
        }, 2400);
    };

    /**
     * 通用弹窗
     * options: { title, body(html), okText, onSubmit(close) }
     * 返回 { close }
     */
    ui.modal = function (options) {
        var mask = document.createElement('div');
        mask.className = 'modal-mask';
        mask.innerHTML = ''
            + '<div class="modal">'
            + '  <div class="modal-head">'
            + '    <h3>' + ui.escape(options.title || '') + '</h3>'
            + '    <button class="modal-close" data-close>&times;</button>'
            + '  </div>'
            + '  <div class="modal-body">' + (options.body || '') + '</div>'
            + '  <div class="modal-foot">'
            + '    <button class="btn" data-close>' + ui.escape(options.cancelText || '取消') + '</button>'
            + '    <button class="btn btn-primary" data-ok>' + ui.escape(options.okText || '保存') + '</button>'
            + '  </div>'
            + '</div>';

        function close() {
            if (mask.parentNode) {
                mask.parentNode.removeChild(mask);
            }
        }

        mask.addEventListener('click', function (event) {
            if (event.target === mask || event.target.hasAttribute('data-close')) {
                close();
            }
            if (event.target.hasAttribute('data-ok') && options.onSubmit) {
                options.onSubmit(close, mask);
            }
        });

        document.body.appendChild(mask);
        var firstInput = mask.querySelector('input, select, textarea');
        if (firstInput) {
            firstInput.focus();
        }
        return { close: close, element: mask };
    };

    /** 确认框, 返回 Promise<boolean> */
    ui.confirm = function (message, okText) {
        return new Promise(function (resolve) {
            var mask = document.createElement('div');
            mask.className = 'modal-mask';
            mask.innerHTML = ''
                + '<div class="modal" style="width:420px">'
                + '  <div class="modal-head"><h3>确认操作</h3></div>'
                + '  <div class="modal-body"><p style="margin:0 0 8px;color:var(--text-2)">'
                + ui.escape(message) + '</p></div>'
                + '  <div class="modal-foot">'
                + '    <button class="btn" data-no>取消</button>'
                + '    <button class="btn btn-primary" data-yes>' + ui.escape(okText || '确定') + '</button>'
                + '  </div>'
                + '</div>';
            mask.addEventListener('click', function (event) {
                if (event.target.hasAttribute('data-no') || event.target === mask) {
                    document.body.removeChild(mask);
                    resolve(false);
                }
                if (event.target.hasAttribute('data-yes')) {
                    document.body.removeChild(mask);
                    resolve(true);
                }
            });
            document.body.appendChild(mask);
        });
    };

    /**
     * 表格
     * columns = [{ title, key, className, render(row) }]
     *
     * 约定: 单元格内容按"已渲染好的 HTML 片段"处理(调用方用 MF.ui.escape 包住用户数据),
     * 因为各页面会把状态标签、操作按钮、图片一起拼进单元格。
     * 早期版本在这里又转义了一次, 导致页面上直接显示出标签源码。
     */
    ui.table = function (columns, rows) {
        if (!rows || !rows.length) {
            return '<div class="empty">暂无数据</div>';
        }
        var head = columns.map(function (column) {
            return '<th class="' + (column.className || '') + '">' + ui.escape(column.title) + '</th>';
        }).join('');

        var body = rows.map(function (row) {
            var cells = columns.map(function (column) {
                var value = column.render ? column.render(row) : row[column.key];
                if (value === undefined || value === null) {
                    value = '';
                }
                return '<td class="' + (column.className || '') + '">' + value + '</td>';
            }).join('');
            return '<tr>' + cells + '</tr>';
        }).join('');

        return '<div class="table-wrap"><table class="table"><thead><tr>' + head
            + '</tr></thead><tbody>' + body + '</tbody></table></div>';
    };

    /** 分页控件 */
    ui.pager = function (total, page, pageSize, onChange) {
        var pages = Math.max(1, Math.ceil(total / pageSize));
        var html = '<div class="pager">'
            + '<span>共 ' + total + ' 条，第 ' + page + '/' + pages + ' 页</span>'
            + '<button class="btn btn-outline" data-page="' + (page - 1) + '"' + (page <= 1 ? ' disabled' : '') + '>上一页</button>'
            + '<button class="btn btn-outline" data-page="' + (page + 1) + '"' + (page >= pages ? ' disabled' : '') + '>下一页</button>'
            + '</div>';
        // 事件交给调用方在渲染后统一绑定
        ui.pager.bind = function (container) {
            container.querySelectorAll('[data-page]').forEach(function (button) {
                button.addEventListener('click', function () {
                    onChange(Number(button.getAttribute('data-page')));
                });
            });
        };
        return html;
    };

    /** 下拉选项 */
    ui.options = function (list, valueKey, labelKey, selected) {
        return list.map(function (item) {
            var value = item[valueKey];
            return '<option value="' + ui.escape(value) + '"'
                + (String(selected) === String(value) ? ' selected' : '') + '>'
                + ui.escape(item[labelKey]) + '</option>';
        }).join('');
    };

    /** datetime-local 与后端 'yyyy-MM-dd HH:mm' 互转 */
    ui.toInputTime = function (value) {
        return value ? String(value).replace(' ', 'T').slice(0, 16) : '';
    };

    ui.toApiTime = function (value) {
        return value ? String(value).replace('T', ' ').slice(0, 16) : '';
    };

    ui.formatLocalTime = function (date) {
        function pad(value) { return value < 10 ? '0' + value : String(value); }
        return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate())
            + 'T' + pad(date.getHours()) + ':' + pad(date.getMinutes());
    };

    MF.ui = ui;
})();
