/* =====================================================================
   来单提醒: 管理端与后端之间的 WebSocket 长连接

   普通 HTTP 是"前端问、后端答", 想知道有没有新订单就得不停轮询。
   WebSocket 是一条一直开着的连接, 后端随时可以往里推消息, 页面立刻能收到。

   后端只在两处推送:
     { "type": 1, "orderId": 12, "content": "订单号：xxx" }  用户支付成功 -> 来单提醒
     { "type": 2, "orderId": 12, "content": "订单号xxx" }    用户催单     -> 催单提醒

   连接地址用当前站点的 host, 走 nginx 的 /ws/ 反向代理(已配好 Upgrade 与 3600s 超时),
   所以浏览器访问 18001 就连 18001, 不用写死后端端口。
   ===================================================================== */
window.MF = window.MF || {};

(function () {
    var RECONNECT_DELAY = 3000;
    var socket = null;
    var started = false;
    var retryTimer = null;

    function wsUrl() {
        var protocol = window.location.protocol === 'https:' ? 'wss://' : 'ws://';
        return protocol + window.location.host + '/ws/admin-' + Date.now().toString(36);
    }

    /** 提示音: 用 Web Audio 现场合成"叮"两声, 不依赖任何音频文件 */
    function beep() {
        try {
            var Ctx = window.AudioContext || window.webkitAudioContext;
            if (!Ctx) {
                return;
            }
            var ctx = beep.ctx || (beep.ctx = new Ctx());
            if (ctx.state === 'suspended') {
                ctx.resume();
            }
            [0, 0.18].forEach(function (offset) {
                var osc = ctx.createOscillator();
                var gain = ctx.createGain();
                osc.type = 'sine';
                osc.frequency.value = 880;
                gain.gain.setValueAtTime(0.0001, ctx.currentTime + offset);
                gain.gain.exponentialRampToValueAtTime(0.25, ctx.currentTime + offset + 0.02);
                gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + offset + 0.16);
                osc.connect(gain);
                gain.connect(ctx.destination);
                osc.start(ctx.currentTime + offset);
                osc.stop(ctx.currentTime + offset + 0.18);
            });
        } catch (error) {
            /* 浏览器不允许放声音时忽略, 不能因为提示音失败就不提醒 */
        }
    }

    function remind(type, content) {
        beep();
        if (type === 1) {
            MF.ui.toast('来单提醒 ' + (content || ''), 'success');
        } else {
            MF.ui.toast('客户催单 ' + (content || ''), 'error');
        }
        // 正停在订单管理页时自动刷新列表, 新订单立刻就出现在表格里
        if (MF.router && MF.router.current() === 'order') {
            MF.router.render();
        }
    }

    function open() {
        socket = new WebSocket(wsUrl());

        socket.onmessage = function (event) {
            var data;
            try {
                data = JSON.parse(event.data);
            } catch (error) {
                return;
            }
            remind(Number(data.type), data.content);
        };

        socket.onclose = function () {
            socket = null;
            // 只有"还处于登录态"时才重连, 退出登录后就让连接彻底断开
            if (started) {
                retryTimer = setTimeout(open, RECONNECT_DELAY);
            }
        };

        // 连接失败由 onclose 统一处理, 这里不用重复写重连
        socket.onerror = function () {};
    }

    MF.ws = {
        /** 已登录时调用; 重复调用无副作用 */
        start: function () {
            if (started) {
                return;
            }
            started = true;
            open();
        },
        /** 退出登录时调用, 主动断开且不再重连 */
        stop: function () {
            started = false;
            clearTimeout(retryTimer);
            if (socket) {
                try {
                    socket.close();
                } catch (error) {
                    /* 已经断开的情况忽略 */
                }
                socket = null;
            }
        },
        isConnected: function () {
            return !!socket && socket.readyState === 1;
        }
    };
})();
