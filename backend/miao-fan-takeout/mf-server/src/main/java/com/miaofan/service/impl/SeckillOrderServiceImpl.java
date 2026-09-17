package com.miaofan.service.impl;

import com.miaofan.constant.MessageConstant;
import com.miaofan.constant.SeckillConstant;
import com.miaofan.context.BaseContext;
import com.miaofan.dto.SeckillDTO;
import com.miaofan.entity.AddressBook;
import com.miaofan.entity.OrderDetail;
import com.miaofan.entity.Orders;
import com.miaofan.entity.SeckillRecord;
import com.miaofan.enumeration.SeckillResult;
import com.miaofan.exception.SeckillBusinessException;
import com.miaofan.mapper.AddressBookMapper;
import com.miaofan.mapper.OrderDetailMapper;
import com.miaofan.mapper.OrderMapper;
import com.miaofan.mapper.SeckillActivityMapper;
import com.miaofan.mapper.SeckillRecordMapper;
import com.miaofan.mq.SeckillMessageProducer;
import com.miaofan.service.SeckillActivityService;
import com.miaofan.service.SeckillCacheService;
import com.miaofan.service.SeckillOrderService;
import com.miaofan.vo.SeckillActivityVO;
import com.miaofan.vo.SeckillResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
public class SeckillOrderServiceImpl implements SeckillOrderService {

    @Autowired
    private SeckillActivityMapper seckillActivityMapper;

    @Autowired
    private SeckillRecordMapper seckillRecordMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private AddressBookMapper addressBookMapper;

    @Autowired
    private SeckillCacheService seckillCacheService;

    @Autowired
    private SeckillActivityService seckillActivityService;

    @Autowired
    private SeckillMessageProducer seckillMessageProducer;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private DefaultRedisScript<Long> seckillStockScript;

    /**
     * 自己注入自己, 目的是让同步模式下的调用也走 Spring 代理, 否则 @Transactional 会失效。
     * 这是 Spring 一个很经典的坑: 类内部 this.method() 不经过代理, 事务与缓存注解统统不生效
     */
    @Autowired
    @Lazy
    private SeckillOrderService selfService;

    /**
     * 是否开启异步下单。默认开启;
     * 压测对比或极端故障需要降级时, 可以关掉让请求内直接落库
     */
    @Value("${miaofan.seckill.async-enabled:true}")
    private boolean asyncEnabled;

    @Override
    public List<SeckillActivityVO> listOnline() {
        return seckillActivityService.listOnline();
    }

    @Override
    public SeckillResultVO seckill(Long activityId, SeckillDTO seckillDTO) {
        Long userId = BaseContext.getCurrentId();
        if (seckillDTO == null || seckillDTO.getAddressBookId() == null) {
            throw new SeckillBusinessException(MessageConstant.ADDRESS_BOOK_REQUIRED);
        }

        SeckillActivityVO activity = seckillCacheService.getActivity(activityId);
        if (activity == null) {
            throw new SeckillBusinessException(MessageConstant.SECKILL_ACTIVITY_NOT_FOUND);
        }

        // Redis 里没有库存 key, 说明活动没预热(例如 Redis 刚重启), 先补一次再抢
        if (seckillCacheService.getStock(activityId) == null) {
            seckillCacheService.warmUp(activityId);
        }

        // 一次 Lua 调用完成: 状态校验 + 时间校验 + 库存校验 + 限购校验 + 扣减 + 记录用户
        Long code = stringRedisTemplate.execute(seckillStockScript,
                Arrays.asList(SeckillConstant.stockKey(activityId), SeckillConstant.userCountKey(activityId)),
                String.valueOf(userId),
                String.valueOf(activity.getPerUserLimit()),
                String.valueOf(activity.getStatus()),
                String.valueOf(toMillis(activity.getStartTime())),
                String.valueOf(toMillis(activity.getEndTime())),
                String.valueOf(System.currentTimeMillis()));

        SeckillResult result = SeckillResult.of(code);
        if (!SeckillResult.SUCCESS.equals(result)) {
            log.info("抢购未通过: activityId={}, userId={}, result={}", activityId, userId, result);
            return SeckillResultVO.builder()
                    .code(result.getCode())
                    .message(result.getMessage())
                    .build();
        }

        // 顺序很重要: 先写"排队中"再投递消息。
        // 如果反过来的话, 消费者可能在消息投递后立刻处理完并写入成功结果,
        // 随后这里又把结果覆盖成"排队中", 前端就会一直看到排队中
        seckillCacheService.saveResult(activityId, userId, SeckillResult.QUEUING, null);

        if (asyncEnabled) {
            // 异步: 只投递消息, 落库交给消费者, 请求立即返回
            seckillMessageProducer.send(activityId, userId, seckillDTO.getAddressBookId());
        } else {
            // 同步: 请求内直接落库, 用于压测对比"同步落库"与"异步削峰"的差距
            Map<String, String> message = new HashMap<>(4);
            message.put("activityId", String.valueOf(activityId));
            message.put("userId", String.valueOf(userId));
            message.put("addressBookId", String.valueOf(seckillDTO.getAddressBookId()));
            selfService.handleSeckillMessage(message);
        }

        return SeckillResultVO.builder()
                .code(SeckillResult.QUEUING.getCode())
                .message(SeckillResult.QUEUING.getMessage())
                .build();
    }

    @Override
    public SeckillResultVO queryResult(Long activityId) {
        Long userId = BaseContext.getCurrentId();

        SeckillResultVO result = seckillCacheService.getResult(activityId, userId);
        if (result != null) {
            return result;
        }

        // 结果缓存过期后, 用数据库流水兜底
        SeckillRecord record = seckillRecordMapper.getByActivityIdAndUserId(activityId, userId);
        if (record == null) {
            throw new SeckillBusinessException(MessageConstant.SECKILL_NO_RECORD);
        }
        if (SeckillConstant.RECORD_SUCCESS.equals(record.getStatus())) {
            return SeckillResultVO.builder()
                    .code(SeckillResult.SUCCESS.getCode())
                    .message(SeckillResult.SUCCESS.getMessage())
                    .orderId(record.getOrderId())
                    .build();
        }
        if (SeckillConstant.RECORD_QUEUING.equals(record.getStatus())) {
            return SeckillResultVO.builder()
                    .code(SeckillResult.QUEUING.getCode())
                    .message(SeckillResult.QUEUING.getMessage())
                    .build();
        }
        // 流水已是失败状态(售罄、下单异常或订单已取消), Redis 里的具体原因可能已过期
        return SeckillResultVO.builder()
                .code(SeckillResult.FAILED.getCode())
                .message(SeckillResult.FAILED.getMessage())
                .build();
    }

    @Override
    @Transactional
    public void handleSeckillMessage(Map<String, String> body) {
        Long activityId = Long.valueOf(body.get("activityId"));
        Long userId = Long.valueOf(body.get("userId"));
        Long addressBookId = Long.valueOf(body.get("addressBookId"));

        // 1. 幂等: 先写流水, 唯一索引 (activity_id, user_id) 会挡住重复消息
        SeckillRecord record = SeckillRecord.builder()
                .activityId(activityId)
                .userId(userId)
                .status(SeckillConstant.RECORD_QUEUING)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        try {
            seckillRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            log.warn("重复的下单消息, 直接忽略: activityId={}, userId={}", activityId, userId);
            return;
        }

        // 2. 数据库库存乐观扣减, 这是 Redis 之外的第三道防线
        if (seckillActivityMapper.deductStock(activityId) == 0) {
            log.warn("数据库库存不足, 抢购失败: activityId={}, userId={}", activityId, userId);
            failRecord(record, SeckillResult.SOLD_OUT, "数据库库存不足");
            return;
        }

        // 3. 收货地址与活动信息
        AddressBook addressBook = addressBookMapper.getById(addressBookId);
        SeckillActivityVO activity = seckillActivityMapper.getById(activityId);
        if (addressBook == null || activity == null) {
            log.warn("地址或活动不存在, 抢购失败: addressBookId={}, activityId={}", addressBookId, activityId);
            rollbackStock(activityId, userId);
            failRecord(record, SeckillResult.SYSTEM_ERROR, "地址或活动不存在");
            return;
        }

        try {
            // 4. 生成订单与订单明细
            Orders orders = buildOrder(activity, addressBook, userId);
            orderMapper.insert(orders);

            OrderDetail orderDetail = buildOrderDetail(activity, orders.getId());
            orderDetailMapper.insertBatch(Collections.singletonList(orderDetail));

            // 5. 回写流水与结果
            record.setOrderId(orders.getId());
            record.setStatus(SeckillConstant.RECORD_SUCCESS);
            record.setUpdateTime(LocalDateTime.now());
            seckillRecordMapper.update(record);

            seckillCacheService.saveResult(activityId, userId, SeckillResult.SUCCESS, orders.getId());
            log.info("抢购下单成功: activityId={}, userId={}, orderId={}, orderNumber={}",
                    activityId, userId, orders.getId(), orders.getNumber());
        } catch (Exception e) {
            log.error("抢购下单异常, 回补库存: activityId={}, userId={}", activityId, userId, e);
            rollbackStock(activityId, userId);
            failRecord(record, SeckillResult.SYSTEM_ERROR, "下单异常");
        }
    }

    /**
     * 构建抢购订单
     */
    private Orders buildOrder(SeckillActivityVO activity, AddressBook addressBook, Long userId) {
        return Orders.builder()
                .number(generateOrderNumber(userId))
                .status(Orders.PENDING_PAYMENT)
                .userId(userId)
                .seckillActivityId(activity.getId())
                .addressBookId(addressBook.getId())
                .orderTime(LocalDateTime.now())
                .payMethod(1)
                .payStatus(Orders.UN_PAID)
                .amount(activity.getSeckillPrice())
                .remark("限时抢购: " + activity.getName())
                .phone(addressBook.getPhone())
                .address(addressBook.getDetail())
                .consignee(addressBook.getConsignee())
                .deliveryStatus(1)
                .packAmount(0)
                .tablewareNumber(1)
                .tablewareStatus(1)
                .build();
    }

    /**
     * 构建订单明细, 抢购默认一次一件
     */
    private OrderDetail buildOrderDetail(SeckillActivityVO activity, Long orderId) {
        return OrderDetail.builder()
                .orderId(orderId)
                .name(activity.getDishName())
                .dishId(activity.getDishId())
                .number(1)
                .amount(activity.getSeckillPrice())
                .image(activity.getDishImage())
                .build();
    }

    /**
     * 生成订单号
     * <p>
     * 原项目只用毫秒时间戳做订单号, 高并发下同一毫秒内会生成重号订单,
     * 这里拼上用户id和随机数降低碰撞概率
     */
    private String generateOrderNumber(Long userId) {
        return System.currentTimeMillis()
                + String.valueOf(userId)
                + ThreadLocalRandom.current().nextInt(100, 1000);
    }

    /**
     * 回补数据库库存与 Redis 预扣库存
     */
    private void rollbackStock(Long activityId, Long userId) {
        try {
            seckillActivityMapper.recoverStock(activityId);
            seckillCacheService.recoverStock(activityId, userId);
        } catch (Exception e) {
            log.error("库存回补失败, 需要依靠对账任务修复: activityId={}, userId={}", activityId, userId, e);
        }
    }

    /**
     * 标记流水为失败并写入结果
     */
    private void failRecord(SeckillRecord record, SeckillResult result, String reason) {
        record.setStatus(SeckillConstant.RECORD_FAILED);
        record.setUpdateTime(LocalDateTime.now());
        seckillRecordMapper.update(record);
        seckillCacheService.saveResult(record.getActivityId(), record.getUserId(), result, null);
        log.warn("抢购失败: activityId={}, userId={}, reason={}", record.getActivityId(), record.getUserId(), reason);
    }

    private long toMillis(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
