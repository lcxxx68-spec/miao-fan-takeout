package com.miaofan.service.impl;

import com.miaofan.constant.StatusConstant;
import com.miaofan.entity.Orders;
import com.miaofan.mapper.DishMapper;
import com.miaofan.mapper.OrderMapper;
import com.miaofan.mapper.SetmealMapper;
import com.miaofan.mapper.UserMapper;
import com.miaofan.service.WorkspaceService;
import com.miaofan.vo.BusinessDataVO;
import com.miaofan.vo.DishOverViewVO;
import com.miaofan.vo.OrderOverViewVO;
import com.miaofan.vo.SetmealOverViewVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class WorkspaceServiceImpl implements WorkspaceService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 根据时间段统计营业数据
     * @param begin
     * @param end
     * @return
     */
    public BusinessDataVO getBusinessData(LocalDateTime begin, LocalDateTime end) {
        /**
         * 营业额：当日已完成订单的总金额
         * 有效订单：当日已完成订单的数量
         * 订单完成率：有效订单数 / 总订单数
         * 平均客单价：营业额 / 有效订单数
         * 新增用户：当日新增用户的数量
         */

        /*Map map = new HashMap();
        map.put("begin",begin);
        map.put("end",end);*/

        //查询总订单数
        Integer totalOrderCount = orderMapper.countOrders(begin,end,null);

//        map.put("status", Orders.COMPLETED);
        //营业额
        Double turnover = orderMapper.sumTurnover(begin,end,Orders.COMPLETED);
        turnover = turnover == null? 0.0 : turnover;

        //有效订单数
        Integer validOrderCount = orderMapper.countOrders(begin,end,Orders.COMPLETED);

        Double unitPrice = 0.0;

        Double orderCompletionRate = 0.0;
        if(totalOrderCount != 0 && validOrderCount != 0){
            //订单完成率
            orderCompletionRate = validOrderCount.doubleValue() / totalOrderCount;
            //平均客单价
            unitPrice = turnover / validOrderCount;
        }

        //新增用户数
        Integer newUsers = userMapper.countUsers(begin, end);

        return BusinessDataVO.builder()
                .turnover(turnover)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .unitPrice(unitPrice)
                .newUsers(newUsers)
                .build();
    }


    /**
     * 查询订单管理数据
     *
     * @return
     */
    public OrderOverViewVO getOrderOverView() {
        Map map = new HashMap();
//        map.put("begin", LocalDateTime.now().with(LocalTime.MIN));
//        map.put("status", Orders.TO_BE_CONFIRMED);
        LocalDateTime begin = LocalDateTime.now().with(LocalTime.MIN);
        Integer status = Orders.TO_BE_CONFIRMED;

        //待接单
        Integer waitingOrders = orderMapper.countOrders(begin,null,status);

        //待派送
//        map.put("status", Orders.CONFIRMED);
        status=Orders.CONFIRMED;
        Integer deliveredOrders = orderMapper.countOrders(begin,null,status);

        //已完成
//        map.put("status", Orders.COMPLETED);
        status=Orders.COMPLETED;
        Integer completedOrders = orderMapper.countOrders(begin,null,status);

        //已取消
//        map.put("status", Orders.CANCELLED);
        status=Orders.CANCELLED;
        Integer cancelledOrders = orderMapper.countOrders(begin,null,status);

        //全部订单
//        map.put("status", null);
        Integer allOrders = orderMapper.countOrders(begin,null,null);

        return OrderOverViewVO.builder()
                .waitingOrders(waitingOrders)
                .deliveredOrders(deliveredOrders)
                .completedOrders(completedOrders)
                .cancelledOrders(cancelledOrders)
                .allOrders(allOrders)
                .build();
    }

    /**
     * 查询菜品总览
     *
     * @return
     */
    public DishOverViewVO getDishOverView() {
        /*Map map = new HashMap();
        map.put("status", StatusConstant.ENABLE);*/
        Integer status = StatusConstant.ENABLE;
        Integer sold = dishMapper.countByMap(status,null);

//        map.put("status", StatusConstant.DISABLE);
        status = StatusConstant.DISABLE;
        Integer discontinued = dishMapper.countByMap(status,null);

        return DishOverViewVO.builder()
                .sold(sold)
                .discontinued(discontinued)
                .build();
    }

    /**
     * 查询套餐总览
     *
     * @return
     */
    public SetmealOverViewVO getSetmealOverView() {
        /*Map map = new HashMap();
        map.put("status", StatusConstant.ENABLE);*/
        Integer status = StatusConstant.ENABLE;
        Integer sold = setmealMapper.countByMap(status,null);

//        map.put("status", StatusConstant.DISABLE);
        status = StatusConstant.DISABLE;
        Integer discontinued = setmealMapper.countByMap(status,null);

        return SetmealOverViewVO.builder()
                .sold(sold)
                .discontinued(discontinued)
                .build();
    }
}
