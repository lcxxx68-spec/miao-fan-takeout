package com.miaofan.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理端抢购活动分页查询条件
 */
@Data
public class SeckillActivityPageQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private int page = 1;

    private int pageSize = 10;

    //活动名称, 模糊查询
    private String name;

    //0未上架 1已上架 2已结束
    private Integer status;

    private Long dishId;
}
