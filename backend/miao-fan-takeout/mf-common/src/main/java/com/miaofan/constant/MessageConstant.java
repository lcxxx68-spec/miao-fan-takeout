package com.miaofan.constant;

/**
 * 信息提示常量类
 */
public class MessageConstant {

    public static final String PASSWORD_ERROR = "密码错误";
    public static final String ACCOUNT_NOT_FOUND = "账号不存在";
    public static final String ACCOUNT_LOCKED = "账号被锁定";
    public static final String ALREADY_EXITS = "已经存在";
    public static final String UNKNOWN_ERROR = "未知错误";
    public static final String USER_NOT_LOGIN = "用户未登录";
    public static final String CATEGORY_BE_RELATED_BY_SETMEAL = "当前分类关联了套餐,不能删除";
    public static final String CATEGORY_BE_RELATED_BY_DISH = "当前分类关联了菜品,不能删除";
    public static final String SHOPPING_CART_IS_NULL = "购物车数据为空，不能下单";
    public static final String ADDRESS_BOOK_IS_NULL = "用户地址为空，不能下单";
    public static final String LOGIN_FAILED = "登录失败";
    public static final String UPLOAD_FAILED = "文件上传失败";
    public static final String SETMEAL_ENABLE_FAILED = "套餐内包含未启售菜品，无法启售";
    public static final String PASSWORD_EDIT_FAILED = "密码修改失败";
    public static final String DISH_ON_SALE = "起售中的菜品不能删除";
    public static final String SETMEAL_ON_SALE = "起售中的套餐不能删除";
    public static final String DISH_BE_RELATED_BY_SETMEAL = "当前菜品关联了套餐,不能删除";
    public static final String ORDER_STATUS_ERROR = "订单状态错误";
    public static final String ORDER_NOT_FOUND = "订单不存在";

    public static final String SECKILL_ACTIVITY_NOT_FOUND = "抢购活动不存在";
    public static final String SECKILL_ACTIVITY_PARAM_ERROR = "活动名称、菜品、时间不能为空";
    public static final String SECKILL_ACTIVITY_TIME_ERROR = "活动开始时间必须早于结束时间";
    public static final String SECKILL_ACTIVITY_PRICE_ERROR = "抢购价必须大于0";
    public static final String SECKILL_ACTIVITY_STOCK_ERROR = "活动库存必须大于0";
    public static final String SECKILL_ACTIVITY_ENDED_ERROR = "活动已结束, 不能上架";
    public static final String SECKILL_ACTIVITY_STATUS_ERROR = "当前活动状态不允许该操作";
    public static final String PARAM_FORMAT_ERROR = "请求参数格式错误, 时间格式应为 yyyy-MM-dd HH:mm";
    public static final String SECKILL_RATE_LIMIT = "请求过于频繁, 请稍后再试";
    public static final String ADDRESS_BOOK_REQUIRED = "请先选择收货地址";
    public static final String SECKILL_NO_RECORD = "没有查询到抢购记录";

}
