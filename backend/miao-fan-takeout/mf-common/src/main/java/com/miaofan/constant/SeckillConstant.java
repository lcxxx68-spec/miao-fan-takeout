package com.miaofan.constant;

/**
 * 限时抢购相关常量
 */
public class SeckillConstant {

    // ---------- 活动状态 ----------
    //未上架
    public static final Integer ACTIVITY_OFFLINE = 0;
    //已上架
    public static final Integer ACTIVITY_ONLINE = 1;
    //已结束
    public static final Integer ACTIVITY_FINISHED = 2;

    // ---------- 抢购流水状态 ----------
    //排队中
    public static final Integer RECORD_QUEUING = 0;
    //抢购成功
    public static final Integer RECORD_SUCCESS = 1;
    //抢购失败
    public static final Integer RECORD_FAILED = 2;

    // ---------- Redis key ----------
    //活动详情缓存
    public static final String ACTIVITY_CACHE_PREFIX = "seckill:activity:";
    //剩余库存
    public static final String STOCK_PREFIX = "seckill:stock:";
    //用户已购数量(哈希: userId -> 数量)
    public static final String USER_COUNT_PREFIX = "seckill:usercount:";
    //抢购结果, 供前端轮询
    public static final String RESULT_PREFIX = "seckill:result:";
    //异步下单消息流
    public static final String STREAM_KEY = "seckill:stream";
    //消息流消费组与消费者名称
    public static final String STREAM_GROUP = "seckill-order-group";
    public static final String STREAM_CONSUMER = "seckill-consumer-1";
    //接口限流
    public static final String RATE_LIMIT_PREFIX = "seckill:rate:";

    // ---------- 抢购结果在 Redis 中的存活时间(分钟) ----------
    public static final long RESULT_TTL_MINUTES = 30L;

    /**
     * 拼接活动详情缓存 key
     */
    public static String activityCacheKey(Long activityId) {
        return ACTIVITY_CACHE_PREFIX + activityId;
    }

    /**
     * 拼接剩余库存 key
     */
    public static String stockKey(Long activityId) {
        return STOCK_PREFIX + activityId;
    }

    /**
     * 拼接用户已购数量 key
     */
    public static String userCountKey(Long activityId) {
        return USER_COUNT_PREFIX + activityId;
    }

    /**
     * 拼接抢购结果 key
     */
    public static String resultKey(Long activityId, Long userId) {
        return RESULT_PREFIX + activityId + ":" + userId;
    }
}
