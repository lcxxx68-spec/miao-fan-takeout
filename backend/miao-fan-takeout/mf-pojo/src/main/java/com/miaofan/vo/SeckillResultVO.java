package com.miaofan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 抢购结果
 * <p>
 * code 来自 SeckillResult 枚举, 前端直接按 code 展示对应文案
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeckillResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long code;

    private String message;

    //抢购成功后生成的订单id, 排队中或失败时为 null
    private Long orderId;
}
