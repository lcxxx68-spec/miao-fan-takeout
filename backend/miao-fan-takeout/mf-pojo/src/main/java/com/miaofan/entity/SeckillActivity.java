package com.miaofan.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 限时抢购活动
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeckillActivity implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    //活动名称
    private String name;

    //关联菜品id
    private Long dishId;

    //抢购价
    private BigDecimal seckillPrice;

    //活动总库存(数据库侧真实库存)
    private Integer totalStock;

    //已售数量
    private Integer soldStock;

    //每人限购数量
    private Integer perUserLimit;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    //0未上架 1已上架 2已结束
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long createUser;

    private Long updateUser;
}
