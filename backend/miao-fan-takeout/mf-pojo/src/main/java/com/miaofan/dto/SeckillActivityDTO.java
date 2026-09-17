package com.miaofan.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理端新增/修改抢购活动传递的数据
 */
@Data
public class SeckillActivityDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private Long dishId;

    private BigDecimal seckillPrice;

    private Integer totalStock;

    private Integer perUserLimit;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    //0未上架 1已上架 2已结束
    private Integer status;
}
