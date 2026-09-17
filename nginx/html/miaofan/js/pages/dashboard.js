/* 工作台: 经营数据 + 趋势图表 */
window.MF = window.MF || {};
MF.pages = MF.pages || {};

(function () {
    var DAYS = 30;

    function stat(label, value, unit, foot, accent) {
        return '<div class="stat">'
            + '<div class="stat-label">' + label + '</div>'
            + '<div class="stat-value' + (accent ? ' accent' : '') + '">' + value
            + (unit ? '<small>' + unit + '</small>' : '') + '</div>'
            + (foot ? '<div class="stat-foot">' + foot + '</div>' : '')
            + '</div>';
    }

    function listRow(label, value) {
        return '<div class="list-row"><span class="muted">' + label + '</span><strong>' + value + '</strong></div>';
    }

    function rate(value) {
        if (value === undefined || value === null) {
            return '-';
        }
        return (Number(value) * 100).toFixed(1) + '%';
    }

    function ymd(date) {
        function pad(value) { return value < 10 ? '0' + value : String(value); }
        return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate());
    }

    function split(text) {
        return (text === undefined || text === null || text === '') ? [] : String(text).split(',');
    }

    function numbers(text) {
        return split(text).map(function (item) { return Number(item); });
    }

    MF.pages.dashboard = {
        title: '工作台',
        subtitle: '今日经营概览与近 30 天趋势',
        render: function (root) {
            root.innerHTML = '<div class="loading">加载中…</div>';

            var end = new Date();
            var begin = new Date(Date.now() - (DAYS - 1) * 864e5);
            var range = { begin: ymd(begin), end: ymd(end) };

            Promise.all([
                MF.http.get('/workspace/businessData'),
                MF.http.get('/workspace/overviewOrders'),
                MF.http.get('/workspace/overviewDishes'),
                MF.http.get('/workspace/overviewSetmeals'),
                MF.http.get('/report/turnoverStatistics', range),
                MF.http.get('/report/ordersStatistics', range),
                MF.http.get('/report/userStatistics', range),
                MF.http.get('/report/top10', range)
            ]).then(function (result) {
                var business = result[0] || {};
                var orders = result[1] || {};
                var dishes = result[2] || {};
                var setmeals = result[3] || {};
                var turnover = result[4] || {};
                var orderReport = result[5] || {};
                var userReport = result[6] || {};
                var top10 = result[7] || {};

                var turnoverLabels = split(turnover.dateList);
                var orderLabels = split(orderReport.dateList);
                var userLabels = split(userReport.dateList);
                var topNames = split(top10.nameList);
                var topValues = numbers(top10.numberList);

                root.innerHTML = ''
                    + '<div class="toolbar" style="margin-bottom:18px">'
                    + '  <div class="toolbar-left"><span class="muted">数据统计范围：'
                    + range.begin + ' 至 ' + range.end + '</span></div>'
                    + '  <div class="toolbar-right">'
                    + '    <button class="btn btn-outline" id="exportReport">导出运营数据报表</button>'
                    + '  </div>'
                    + '</div>'
                    + '<div class="grid-4">'
                    + stat('今日营业额', MF.ui.money(business.turnover), '', '统计到当前时刻', true)
                    + stat('有效订单', business.validOrderCount || 0, '单',
                        '完成率 ' + rate(business.orderCompletionRate))
                    + stat('平均客单价', MF.ui.money(business.unitPrice), '', '按有效订单计算')
                    + stat('新增用户', business.newUsers || 0, '人', '今日新注册')
                    + '</div>'

                    + '<div class="card" style="margin-top:20px">'
                    + '  <div class="card-head"><h3>近 30 天营业额趋势</h3>'
                    + '    <span class="muted-sm">合计 ' + MF.ui.money(turnover.turnoverList
                        ? numbers(turnover.turnoverList).reduce(function (a, b) { return a + b; }, 0) : 0) + '</span>'
                    + '  </div>'
                    + MF.charts.line(turnoverLabels, numbers(turnover.turnoverList), { unit: ' 元' })
                    + '</div>'

                    + '<div class="grid-2" style="margin-top:20px">'
                    + '  <div class="card"><div class="card-head"><h3>近 30 天订单量</h3>'
                    + '    <span class="muted-sm">有效订单 ' + (orderReport.validOrderCount || 0)
                    + ' / 完成率 ' + rate(orderReport.orderCompletionRate) + '</span></div>'
                    + MF.charts.bars(orderLabels, numbers(orderReport.orderCountList), { unit: ' 单' })
                    + '  </div>'
                    + '  <div class="card"><div class="card-head"><h3>近 30 天新增用户</h3>'
                    + '    <span class="muted-sm">累计 ' + (userReport.totalUserList
                        ? numbers(userReport.totalUserList).slice(-1)[0] : 0) + ' 人</span></div>'
                    + MF.charts.line(userLabels, numbers(userReport.newUserList), { unit: ' 人' })
                    + '  </div>'
                    + '</div>'

                    + '<div class="grid-2" style="margin-top:20px">'
                    + '  <div class="card"><div class="card-head"><h3>销量 Top 10</h3>'
                    + '    <span class="muted-sm">近 30 天</span></div>'
                    + MF.charts.rank(topNames.map(function (name, index) {
                        return { name: name, value: topValues[index] || 0 };
                    }), { unit: ' 份' })
                    + '  </div>'
                    + '  <div class="card"><div class="card-head"><h3>今日概览</h3></div>'
                    + '    <div class="list">'
                    + listRow('待接单', orders.waitingOrders || 0)
                    + listRow('待派送', orders.deliveredOrders || 0)
                    + listRow('已完成', orders.completedOrders || 0)
                    + listRow('已取消', orders.cancelledOrders || 0)
                    + listRow('菜品在售 / 停售', (dishes.sold || 0) + ' / ' + (dishes.discontinued || 0))
                    + listRow('套餐在售 / 停售', (setmeals.sold || 0) + ' / ' + (setmeals.discontinued || 0))
                    + '    </div>'
                    + '  </div>'
                    + '</div>';

                // 图表渲染完成后绑定悬停提示
                MF.charts.bind(root);
                bindExport(root);
            }).catch(function (error) {
                root.innerHTML = '<div class="card"><div class="empty">' + MF.ui.escape(error.message) + '</div></div>';
            });
        }
    };

    /**
     * 导出运营数据报表(后端直接返回 xlsx 文件流)
     */
    function bindExport(root) {
        var button = root.querySelector('#exportReport');
        if (!button) {
            return;
        }
        button.addEventListener('click', function () {
            button.disabled = true;
            button.textContent = '导出中…';
            fetch('/api/report/export', { headers: { token: MF.session.token } })
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error(response.status === 401 ? '登录已过期，请重新登录' : '导出失败');
                    }
                    return response.blob();
                })
                .then(function (blob) {
                    var url = URL.createObjectURL(blob);
                    var link = document.createElement('a');
                    link.href = url;
                    link.download = '运营数据报表.xlsx';
                    document.body.appendChild(link);
                    link.click();
                    document.body.removeChild(link);
                    URL.revokeObjectURL(url);
                    MF.ui.toast('报表已开始下载', 'success');
                })
                .catch(function (error) {
                    MF.ui.toast(error.message, 'error');
                })
                .finally(function () {
                    button.disabled = false;
                    button.textContent = '导出运营数据报表';
                });
        });
    }
})();
