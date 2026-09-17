package com.miaofan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 抢购活动展示数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeckillActivityVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private Long dishId;

    //菜品名称与图片, 由 dish 表关联查询得到
    private String dishName;

    private String dishImage;

    //菜品原价
    private BigDecimal originalPrice;

    private BigDecimal seckillPrice;

    private Integer totalStock;

    private Integer soldStock;

    //Redis 侧剩余库存, 由业务层填充
    private Integer remainStock;

    private Integer perUserLimit;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer status;
}
