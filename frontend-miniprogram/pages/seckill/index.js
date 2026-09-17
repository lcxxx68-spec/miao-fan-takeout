/**
 * 限时抢购(用户端)
 * 这是手写的原生小程序页面, 直接调用后端的 /user/seckill 接口
 */
const BASE_URL = 'http://localhost:18080';

const RESULT = {
    SUCCESS: 0,
    SOLD_OUT: 1,
    LIMIT_EXCEEDED: 2,
    NOT_STARTED: 3,
    ENDED: 4,
    OFFLINE: 5,
    QUEUING: 6,
    CANCELLED: 7,
    FAILED: 8,
    SYSTEM_ERROR: 9
};

function request(path, options) {
    options = options || {};
    return new Promise(function (resolve, reject) {
        const header = { 'Content-Type': 'application/json' };
        if (!options.noToken) {
            header['authentication'] = wx.getStorageSync('token') || '';
        }
        wx.request({
            url: BASE_URL + path,
            method: options.method || 'GET',
            data: options.data,
            header: header,
            success: function (res) {
                if (res.statusCode === 401) {
                    wx.removeStorageSync('token');
                    reject(new Error('登录已过期'));
                    return;
                }
                const body = res.data;
                if (!body || body.code !== 1) {
                    reject(new Error((body && body.msg) || '请求失败'));
                    return;
                }
                resolve(body.data);
            },
            fail: function () {
                reject(new Error('网络异常，请检查后端是否已启动'));
            }
        });
    });
}

/** 保证有用户登录态: 先读缓存, 没有再用 wx.login 换一次 token */
function ensureToken() {
    return new Promise(function (resolve, reject) {
        const cached = wx.getStorageSync('token');
        if (cached) {
            resolve(cached);
            return;
        }
        wx.login({
            success: function (res) {
                if (!res.code) {
                    reject(new Error('微信登录失败'));
                    return;
                }
                request('/user/user/login', { method: 'POST', data: { code: res.code }, noToken: true })
                    .then(function (data) {
                        wx.setStorageSync('token', data.token);
                        resolve(data.token);
                    })
                    .catch(reject);
            },
            fail: function () {
                reject(new Error('微信登录失败'));
            }
        });
    });
}

function toTime(text) {
    if (!text) {
        return 0;
    }
    // 小程序环境下 'yyyy-MM-dd HH:mm' 需要用 / 分隔才能被正确解析
    return new Date(String(text).replace(/-/g, '/')).getTime();
}

function shortTime(text) {
    return text ? String(text).slice(5, 16) : '';
}

Page({
    data: {
        loading: true,
        submitting: false,
        activities: []
    },

    onLoad: function () {
        this.loadActivities();
    },

    onShow: function () {
        if (!this.data.loading) {
            this.loadActivities();
        }
    },

    loadActivities: function () {
        const that = this;
        ensureToken()
            .then(function () {
                return request('/user/seckill/list');
            })
            .then(function (list) {
                that.setData({ loading: false, activities: that.decorate(list || []) });
            })
            .catch(function (error) {
                that.setData({ loading: false, activities: [] });
                wx.showToast({ title: error.message, icon: 'none' });
            });
    },

    /** 计算每个活动的展示状态与按钮文案 */
    decorate: function (list) {
        const now = Date.now();
        return list.map(function (item) {
            const start = toTime(item.startTime);
            const end = toTime(item.endTime);
            const remain = item.remainStock;
            const notStarted = start && now < start;
            const ended = end && now > end;
            let canBuy = !notStarted && !ended && (remain === null || remain === undefined || remain > 0);
            let btnText = '立即抢';

            if (notStarted) {
                btnText = '未开始';
            } else if (ended) {
                btnText = '已结束';
            } else if (remain !== null && remain !== undefined && remain <= 0) {
                btnText = '已抢光';
            }

            return {
                id: item.id,
                dishName: item.dishName,
                dishImage: item.dishImage,
                seckillPrice: item.seckillPrice,
                originalPrice: item.originalPrice,
                canBuy: canBuy,
                btnText: btnText,
                stockText: (remain === null || remain === undefined) ? '库存待开放' : ('剩余 ' + remain + ' 份'),
                stockLow: remain !== null && remain !== undefined && remain <= 10,
                timeText: notStarted
                    ? (shortTime(item.startTime) + ' 开抢')
                    : (shortTime(item.startTime) + ' — ' + shortTime(item.endTime))
            };
        });
    },

    onSeckill: function (event) {
        const that = this;
        const activityId = event.currentTarget.dataset.id;
        if (this.data.submitting) {
            return;
        }
        this.setData({ submitting: true });

        ensureToken()
            .then(function () {
                return request('/user/addressBook/list');
            })
            .then(function (addressList) {
                if (!addressList || !addressList.length) {
                    wx.showModal({
                        title: '请先添加收货地址',
                        content: '抢购成功后需要按地址配送，先添加一个地址吧',
                        confirmText: '去添加',
                        success: function (res) {
                            if (res.confirm) {
                                wx.navigateTo({ url: '/pages/addOrEditAddress/addOrEditAddress' });
                            }
                        }
                    });
                    return null;
                }
                const picked = addressList.filter(function (item) {
                    return item.isDefault === 1;
                })[0] || addressList[0];
                return request('/user/seckill/' + activityId, {
                    method: 'POST',
                    data: { addressBookId: picked.id }
                }).then(function (result) {
                    that.showResult(activityId, result);
                });
            })
            .catch(function (error) {
                wx.showToast({ title: error.message, icon: 'none', duration: 2500 });
            })
            .then(function () {
                that.setData({ submitting: false });
            });
    },

    /** 抢购接口先返回"排队中", 需要轮询拿最终结果 */
    showResult: function (activityId, result) {
        const that = this;
        if (result.code === RESULT.QUEUING) {
            wx.showLoading({ title: '正在抢购…', mask: true });
            this.pollResult(activityId, 0);
            return;
        }
        this.toastResult(result);
        void that;
    },

    pollResult: function (activityId, attempt) {
        const that = this;
        if (attempt >= 10) {
            wx.hideLoading();
            wx.showToast({ title: '排队中，稍后可在订单里查看', icon: 'none', duration: 2500 });
            return;
        }
        setTimeout(function () {
            request('/user/seckill/result/' + activityId)
                .then(function (result) {
                    if (result.code === RESULT.QUEUING) {
                        that.pollResult(activityId, attempt + 1);
                        return;
                    }
                    wx.hideLoading();
                    that.toastResult(result);
                    that.loadActivities();
                })
                .catch(function () {
                    wx.hideLoading();
                });
        }, 800);
    },

    toastResult: function (result) {
        if (result.code === RESULT.SUCCESS) {
            wx.showModal({
                title: '抢购成功',
                content: '订单已生成，请在 15 分钟内完成支付，否则库存会自动释放。',
                confirmText: '去看订单',
                success: function (res) {
                    if (res.confirm) {
                        wx.navigateTo({ url: '/pages/order/index' });
                    }
                }
            });
            return;
        }
        wx.showToast({ title: result.message || '本次抢购未成功', icon: 'none', duration: 2500 });
    }
});
