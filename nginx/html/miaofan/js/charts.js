/* =====================================================================
   轻量图表: 手写 SVG, 不依赖任何图表库
   - 支持折线图、柱状图、横向排行榜
   - 鼠标悬停显示竖线引导 + 跟随鼠标的数值提示框
   ===================================================================== */
window.MF = window.MF || {};

(function () {
    var charts = {};

    var W = 760;
    var H = 230;
    var PAD_L = 62;
    var PAD_R = 18;
    var PAD_T = 18;
    var PAD_B = 32;

    // 已渲染图表的元数据, 悬停时用
    var registry = {};
    var seq = 0;

    function esc(text) {
        return MF.ui.escape(text);
    }

    function niceMax(max) {
        if (!max || max <= 0) {
            return 1;
        }
        var unit = Math.pow(10, Math.floor(Math.log10(max)));
        var step = unit / 2;
        var top = Math.ceil(max / step) * step;
        return top === 0 ? 1 : top;
    }

    function formatShort(value) {
        if (value >= 10000) {
            return (value / 10000).toFixed(1) + '万';
        }
        if (value >= 1000) {
            return (value / 1000).toFixed(1) + 'k';
        }
        return Number.isInteger(value) ? String(value) : value.toFixed(0);
    }

    function axis(max) {
        var out = '';
        for (var i = 0; i <= 3; i++) {
            var ratio = i / 3;
            var y = PAD_T + (H - PAD_T - PAD_B) * ratio;
            var value = max * (1 - ratio);
            out += '<line x1="' + PAD_L + '" y1="' + y + '" x2="' + (W - PAD_R) + '" y2="' + y
                + '" stroke="#EFEFF3" stroke-width="1"/>';
            out += '<text x="' + (PAD_L - 10) + '" y="' + (y + 4) + '" text-anchor="end" '
                + 'font-size="12" fill="#8E8E93">' + formatShort(value) + '</text>';
        }
        return out;
    }

    function xLabels(labels) {
        if (!labels.length) {
            return '';
        }
        var indexes = [0, Math.floor((labels.length - 1) / 2), labels.length - 1];
        var step = (W - PAD_L - PAD_R) / Math.max(1, labels.length - 1);
        return indexes.map(function (index) {
            var x = PAD_L + index * step;
            var anchor = index === 0 ? 'start' : (index === labels.length - 1 ? 'end' : 'middle');
            return '<text x="' + x + '" y="' + (H - 10) + '" text-anchor="' + anchor
                + '" font-size="12" fill="#8E8E93">' + esc(String(labels[index]).slice(5)) + '</text>';
        }).join('');
    }

    function emptyTip() {
        return '<div class="empty">暂无数据</div>';
    }

    /**
     * 把图表包一层, 供悬停提示定位使用
     */
    function wrap(svg, meta) {
        var id = 'chart-' + (++seq);
        registry[id] = meta;
        return '<div class="chart-wrap" id="' + id + '">'
            + svg
            + '<div class="chart-tip" hidden></div>'
            + '</div>';
    }

    /**
     * 折线图(带面积填充)
     */
    charts.line = function (labels, values, options) {
        options = options || {};
        if (!values || !values.length) {
            return emptyTip();
        }
        var color = options.color || '#F97316';
        var max = niceMax(Math.max.apply(null, values));
        var step = (W - PAD_L - PAD_R) / Math.max(1, values.length - 1);

        function x(i) { return PAD_L + i * step; }
        function y(v) { return PAD_T + (H - PAD_T - PAD_B) * (1 - v / max); }

        var line = values.map(function (v, i) {
            return (i === 0 ? 'M' : 'L') + x(i).toFixed(1) + ',' + y(v).toFixed(1);
        }).join(' ');
        var area = line + ' L' + x(values.length - 1).toFixed(1) + ',' + (H - PAD_B)
            + ' L' + x(0).toFixed(1) + ',' + (H - PAD_B) + ' Z';

        var dots = values.map(function (v, i) {
            return '<circle cx="' + x(i).toFixed(1) + '" cy="' + y(v).toFixed(1)
                + '" r="3" fill="#fff" stroke="' + color + '" stroke-width="2"/>';
        }).join('');

        var svg = ''
            + '<svg class="chart" viewBox="0 0 ' + W + ' ' + H + '" preserveAspectRatio="none">'
            + '  <defs><linearGradient id="mfArea' + (seq + 1) + '" x1="0" y1="0" x2="0" y2="1">'
            + '    <stop offset="0%" stop-color="' + color + '" stop-opacity="0.22"/>'
            + '    <stop offset="100%" stop-color="' + color + '" stop-opacity="0.02"/>'
            + '  </linearGradient></defs>'
            + axis(max)
            + '  <path d="' + area + '" fill="url(#mfArea' + (seq + 1) + ')"/>'
            + '  <path d="' + line + '" fill="none" stroke="' + color + '" stroke-width="2.5" '
            + 'stroke-linecap="round" stroke-linejoin="round"/>'
            + dots
            + '  <line class="chart-guide" y1="' + PAD_T + '" y2="' + (H - PAD_B)
            + '" stroke="' + color + '" stroke-width="1" stroke-dasharray="4 4" opacity="0"/>'
            + '  <circle class="chart-focus" r="5" fill="#fff" stroke="' + color
            + '" stroke-width="2.5" opacity="0"/>'
            + xLabels(labels)
            + '</svg>';

        return wrap(svg, {
            type: 'line',
            labels: labels,
            values: values,
            unit: options.unit || '',
            step: step,
            x: x,
            y: y
        });
    };

    /**
     * 柱状图
     */
    charts.bars = function (labels, values, options) {
        options = options || {};
        if (!values || !values.length) {
            return emptyTip();
        }
        var color = options.color || '#F97316';
        var max = niceMax(Math.max.apply(null, values));
        var slot = (W - PAD_L - PAD_R) / values.length;
        var barWidth = Math.max(3, Math.min(18, slot * 0.55));

        function centerX(i) { return PAD_L + slot * i + slot / 2; }
        function y(v) { return PAD_T + (H - PAD_T - PAD_B) * (1 - v / max); }

        var bars = values.map(function (v, i) {
            var height = (H - PAD_T - PAD_B) * (v / max);
            var x = PAD_L + slot * i + (slot - barWidth) / 2;
            var top = H - PAD_B - height;
            return '<rect x="' + x.toFixed(1) + '" y="' + top.toFixed(1) + '" width="' + barWidth.toFixed(1)
                + '" height="' + Math.max(1, height).toFixed(1) + '" rx="' + (barWidth / 2).toFixed(1)
                + '" fill="' + color + '" opacity="' + (0.55 + 0.45 * (v / max)).toFixed(2) + '"/>';
        }).join('');

        var svg = ''
            + '<svg class="chart" viewBox="0 0 ' + W + ' ' + H + '" preserveAspectRatio="none">'
            + axis(max) + bars
            + '  <line class="chart-guide" y1="' + PAD_T + '" y2="' + (H - PAD_B)
            + '" stroke="' + color + '" stroke-width="1" stroke-dasharray="4 4" opacity="0"/>'
            + xLabels(labels)
            + '</svg>';

        return wrap(svg, {
            type: 'bars',
            labels: labels,
            values: values,
            unit: options.unit || '',
            slot: slot,
            x: centerX,
            y: y
        });
    };

    /**
     * 横向条形榜(Top N)
     */
    charts.rank = function (items, options) {
        options = options || {};
        if (!items || !items.length) {
            return emptyTip();
        }
        var max = Math.max.apply(null, items.map(function (item) { return item.value; })) || 1;
        return '<div class="rank">' + items.map(function (item, index) {
            var width = Math.max(2, (item.value / max) * 100);
            return '<div class="rank-row" title="' + esc(item.name) + '：' + item.value + (options.unit || '') + '">'
                + '<span class="rank-index">' + (index + 1) + '</span>'
                + '<span class="rank-name">' + esc(item.name) + '</span>'
                + '<span class="rank-bar"><i style="width:' + width.toFixed(1) + '%"></i></span>'
                + '<span class="rank-value">' + item.value + (options.unit || '') + '</span>'
                + '</div>';
        }).join('') + '</div>';
    };

    /**
     * 给容器内所有图表绑定悬停提示
     * 页面渲染完图表后调用一次即可
     */
    charts.bind = function (scope) {
        var root = scope || document;
        root.querySelectorAll('.chart-wrap').forEach(function (element) {
            if (element.getAttribute('data-bound')) {
                return;
            }
            element.setAttribute('data-bound', '1');

            var meta = registry[element.id];
            var svg = element.querySelector('svg');
            var tip = element.querySelector('.chart-tip');
            var guide = element.querySelector('.chart-guide');
            var focus = element.querySelector('.chart-focus');
            if (!meta || !svg || !tip) {
                return;
            }

            element.addEventListener('mousemove', function (event) {
                var rect = svg.getBoundingClientRect();
                var viewX = (event.clientX - rect.left) / rect.width * W;
                var index = meta.type === 'bars'
                    ? Math.floor((viewX - PAD_L) / meta.slot)
                    : Math.round((viewX - PAD_L) / meta.step);
                index = Math.max(0, Math.min(meta.values.length - 1, index));

                var value = meta.values[index];
                var pointX = meta.x(index);
                var pointY = meta.y(value);

                tip.hidden = false;
                tip.innerHTML = '<b>' + esc(meta.labels[index]) + '</b>　' + value + (meta.unit || '');
                tip.style.left = (pointX / W * rect.width + PAD_L * 0) + 'px';
                tip.style.top = (pointY / H * rect.height - 12) + 'px';

                if (guide) {
                    guide.setAttribute('x1', pointX);
                    guide.setAttribute('x2', pointX);
                    guide.setAttribute('opacity', '0.55');
                }
                if (focus) {
                    focus.setAttribute('cx', pointX);
                    focus.setAttribute('cy', pointY);
                    focus.setAttribute('opacity', '1');
                }
            });

            element.addEventListener('mouseleave', function () {
                tip.hidden = true;
                if (guide) {
                    guide.setAttribute('opacity', '0');
                }
                if (focus) {
                    focus.setAttribute('opacity', '0');
                }
            });
        });
    };

    MF.charts = charts;
})();
