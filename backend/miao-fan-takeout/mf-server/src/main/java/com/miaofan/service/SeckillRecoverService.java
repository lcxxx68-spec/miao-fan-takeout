package com.miaofan.service;

import com.miaofan.constant.SeckillConstant;
import com.miaofan.entity.Orders;
import com.miaofan.entity.SeckillRecord;
import com.miaofan.enumeration.SeckillResult;
import com.miaofan.mapper.SeckillActivityMapper;
import com.miaofan.mapper.SeckillRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 抢购库存回收服务
 * <p>
 * 抢购链路里库存是"先扣后建单", 一旦订单最终没有成立(超时未支付、下单异常),
 * 必须把库存还回去, 否则会出现"库存扣了但订单不存在"的资损问题
 */
@Slf4j
@Service
public class SeckillRecoverService {

    @Autowired
    private SeckillActivityMapper seckillActivityMapper;

    @Autowired
    private SeckillRecordMapper seckillRecordMapper;

    @Autowired
    private SeckillCacheService seckillCacheService;

    /**
     * 抢购订单超时取消后回收库存
     * <p>
     * 业务取舍: 这里选择"释放用户占位", 即该用户还可以重新抢购。
     * 如果业务上不允许用户二次抢购, 把 seckillCacheService.recoverStock 换成
     * "只回补库存、不动用户占位"的写法即可
     */
    @Transactional
    public void recoverByCancelledOrder(Orders orders) {
        Long activityId = orders.getSeckillActivityId();
        Long userId = orders.getUserId();

        // 数据库库存加回去
        int dbRecovered = seckillActivityMapper.recoverStock(activityId);
        // Redis 预扣库存加回去, 同时释放该用户的占位
        seckillCacheService.recoverStock(activityId, userId);

        // 抢购流水标记为失败, 这样对账时不会再把它算成有效占位
        SeckillRecord record = seckillRecordMapper.getByActivityIdAndUserId(activityId, userId);
        if (record != null && !SeckillConstant.RECORD_FAILED.equals(record.getStatus())) {
            record.setStatus(SeckillConstant.RECORD_FAILED);
            record.setUpdateTime(LocalDateTime.now());
            seckillRecordMapper.update(record);
        }

        // 覆盖抢购结果, 让用户轮询时能看到"订单已取消, 库存已释放"
        seckillCacheService.saveResult(activityId, userId, SeckillResult.CANCELLED, orders.getId());

        log.info("抢购订单超时取消, 库存已回补: orderId={}, activityId={}, userId={}, 数据库回补行数={}",
                orders.getId(), activityId, userId, dbRecovered);
    }
}
