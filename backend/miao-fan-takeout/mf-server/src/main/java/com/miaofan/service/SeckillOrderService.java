package com.miaofan.service;

import com.miaofan.dto.SeckillDTO;
import com.miaofan.vo.SeckillActivityVO;
import com.miaofan.vo.SeckillResultVO;

import java.util.List;
import java.util.Map;

/**
 * 用户端抢购业务接口
 */
public interface SeckillOrderService {

    /**
     * 用户端可见的活动列表
     */
    List<SeckillActivityVO> listOnline();

    /**
     * 参与抢购
     * <p>
     * 只做 Redis 预扣与消息投递, 不落库
     */
    SeckillResultVO seckill(Long activityId, SeckillDTO seckillDTO);

    /**
     * 查询抢购结果, 供前端轮询
     */
    SeckillResultVO queryResult(Long activityId);

    /**
     * 消费抢购下单消息, 完成幂等校验与订单落库
     */
    void handleSeckillMessage(Map<String, String> body);
}
