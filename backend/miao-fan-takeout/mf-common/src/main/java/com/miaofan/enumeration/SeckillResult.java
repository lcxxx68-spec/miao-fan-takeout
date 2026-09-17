package com.miaofan.enumeration;

/**
 * 抢购结果
 * <p>
 * code 与 Lua 脚本的返回值一一对应, Lua 里返回数字, Java 侧翻译成枚举
 */
public enum SeckillResult {

    //Lua 脚本返回的六种情况
    SUCCESS(0L, "抢购成功, 正在生成订单"),
    SOLD_OUT(1L, "手慢了, 已经抢光"),
    LIMIT_EXCEEDED(2L, "超出每人限购数量"),
    NOT_STARTED(3L, "活动尚未开始"),
    ENDED(4L, "活动已经结束"),
    OFFLINE(5L, "活动未上架"),

    //以下为 Java 侧的结果
    QUEUING(6L, "排队中, 请稍后查询结果"),
    CANCELLED(7L, "订单超时未支付, 库存已释放"),
    FAILED(8L, "本次抢购未成功"),
    SYSTEM_ERROR(9L, "系统繁忙, 请稍后重试");

    private final Long code;

    private final String message;

    SeckillResult(Long code, String message) {
        this.code = code;
        this.message = message;
    }

    public Long getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    /**
     * 把 Lua 脚本返回的数字翻译成枚举
     *
     * @param code Lua 返回值
     * @return 对应结果, 无匹配时返回 SYSTEM_ERROR
     */
    public static SeckillResult of(Long code) {
        if (code == null) {
            return SYSTEM_ERROR;
        }
        for (SeckillResult result : values()) {
            if (result.code.equals(code)) {
                return result;
            }
        }
        return SYSTEM_ERROR;
    }
}
