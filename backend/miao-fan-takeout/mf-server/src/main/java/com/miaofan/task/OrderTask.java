package com.miaofan.task;

import com.miaofan.entity.Orders;
import com.miaofan.mapper.OrderMapper;
import com.miaofan.service.SeckillRecoverService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class OrderTask {
    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private SeckillRecoverService seckillRecoverService;

    /**
     * 处理超时未支付订单
     */
    @Scheduled(cron = "0 * * * * *")
//    @Scheduled(cron = "1/5 * * * * ?")
    public void processTimeoutOrder() {
        log.info("处理超时订单:{}", LocalDateTime.now());

        LocalDateTime time = LocalDateTime.now().plusMinutes(-15);

        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.PENDING_PAYMENT, time);

        if (ordersList.size() > 0) {
            //未支付的订单自动取消
            for (Orders orders : ordersList) {
                orders.setStatus(Orders.CANCELLED);
                orders.setCancelTime(LocalDateTime.now());
                orders.setCancelReason("订单超时,自动取消");

                orderMapper.update(orders);

                // 抢购订单取消后必须把预扣的库存还回去, 普通订单没有这个动作
                if (orders.getSeckillActivityId() != null) {
                    seckillRecoverService.recoverByCancelledOrder(orders);
                }
            }
        }
    }


    /**
     * 将前一天的"派送中"订单设为已完成
     */
    @Scheduled(cron = "0 0 1 * * ?")
//    @Scheduled(cron = "0/5 * * * * ?")
    public void processDeliveryOrder(){
        log.info("更新前一天的派送中订单:{}", LocalDateTime.now());
        LocalDateTime time = LocalDateTime.now().plusMinutes(-60);

        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.DELIVERY_IN_PROGRESS, time);
        if (ordersList.size() > 0) {
            //自动设置为已完成
            for (Orders orders : ordersList) {
                orders.setStatus(Orders.COMPLETED);
                orderMapper.update(orders);
            }
        }
    }

}
