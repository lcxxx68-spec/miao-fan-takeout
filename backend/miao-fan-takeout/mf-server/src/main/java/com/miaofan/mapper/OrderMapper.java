package com.miaofan.mapper;

import com.github.pagehelper.Page;
import com.miaofan.dto.GoodsSalesDTO;
import com.miaofan.dto.OrdersPageQueryDTO;
import com.miaofan.entity.Orders;
import com.miaofan.vo.SalesTop10ReportVO;
import io.swagger.models.auth.In;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper {
    /**
     * 插入一条订单信息
     * @param orders
     */
    void insert(Orders orders);

    /**
     * 根据订单号和用户id查询订单
     * @param orderNumber
     * @param userId
     */
    @Select("select * from orders where number = #{orderNumber} and user_id= #{userId}")
    Orders getByNumberAndUserId(String orderNumber, Long userId);

    /**
     * 根据订单号查询订单
     * <p>
     * 支付回调是微信服务器发起的, 请求里没有登录态, 拿不到 userId,
     * 只能用商户订单号定位订单(订单号本身唯一)
     *
     * @param orderNumber
     */
    @Select("select * from orders where number = #{orderNumber}")
    Orders getByNumber(String orderNumber);

    /**
     * 修改订单信息
     * @param orders
     */
    void update(Orders orders);

    /**
     * 分页条件查询并按下单时间排序
     * @param ordersPageQueryDTO
     */
    Page<Orders> pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 根据id查询订单
     * @param id
     */
    @Select("select * from orders where id=#{id}")
    Orders getById(Long id);

    /**
     * 根据状态统计订单数量
     * @param status
     */
    @Select("select count(id) from orders where status = #{status}")
    Integer countStatus(Integer status);

    /**
     * 处理超时未支付订单
     */
    @Select("select * from orders where status=#{status} and order_time<#{time}")
    List<Orders> getByStatusAndOrderTimeLT(Integer status, LocalDateTime time);

    /**
     * 查询营业额
     */
    Double sumTurnover(LocalDateTime beginTime, LocalDateTime endTime, Integer status);

    /**
     * 查询某种状态下的订单总数
     */
    Integer countOrders(LocalDateTime beginTime, LocalDateTime endTime, Integer status);

    /**
     * 销量top10
     */
    ArrayList<GoodsSalesDTO> getSalesTop10(LocalDateTime beginTime, LocalDateTime endTime);


    Integer countByMap(LocalDateTime begin, LocalDateTime end,Integer status);
}
