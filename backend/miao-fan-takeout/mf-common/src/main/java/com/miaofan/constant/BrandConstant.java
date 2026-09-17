package com.miaofan.constant;

/**
 * 品牌标识相关常量
 */
public class BrandConstant {

    //菜品/套餐没有上传图片时用的默认图(品牌标, 由后端 /img/brand 提供)
    //不兜底的话, 新增的菜或套餐在管理端列表和小程序里都是一片空白
    public static final String DEFAULT_IMAGE =
            "http://localhost:18080/img/brand/logo-text.png";
}
