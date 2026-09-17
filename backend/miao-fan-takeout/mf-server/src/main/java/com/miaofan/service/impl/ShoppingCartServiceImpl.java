package com.miaofan.service.impl;

import com.miaofan.context.BaseContext;
import com.miaofan.dto.ShoppingCartDTO;
import com.miaofan.entity.Dish;
import com.miaofan.entity.Setmeal;
import com.miaofan.entity.ShoppingCart;
import com.miaofan.mapper.DishMapper;
import com.miaofan.mapper.SetmealMapper;
import com.miaofan.mapper.ShoppingCartMapper;
import com.miaofan.service.ShoppingCartService;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ShoppingCartServiceImpl implements ShoppingCartService {
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 添加购物车
     *
     * @param shoppingCartDTO
     */
    @Override
    @ApiOperation("添加购物车")
    public void addShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        //创建一条购物车记录
        //--当前购物车对象是一条购物车数据
        //--某个用户的购物车列表是一个单独的数据库(但所有用户的数据都在用一个库,用过userId区分)

        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);
        Long userId = BaseContext.getCurrentId();
        shoppingCart.setUserId(userId);

        //查询当前加入购物车商品是否已经存在
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        //----当前查找结果要不然只有一条数据,要不然为空

        //如果已经存在,给该商品数量加一
        if (list != null && list.size() > 0) {
            //这里只是给前边的shoppingCart换一个名字,前边的userId会传进来,无需重复定义
            ShoppingCart cart = list.get(0);
            cart.setNumber(cart.getNumber() + 1);
            shoppingCartMapper.updateNumberById(cart);
        } else {
            //如果不存在,新增该商品
            Long dishId = shoppingCart.getDishId();
            //--该商品是菜品
            if (dishId != null) {
                Dish dish = dishMapper.getById(dishId);

                shoppingCart.setAmount(dish.getPrice());
                shoppingCart.setName(dish.getName());
                shoppingCart.setImage(dish.getImage());
            } else {
                //--该商品是套餐
                Setmeal setmeal = setmealMapper.getById(shoppingCart.getSetmealId());

                shoppingCart.setName(setmeal.getName());
                shoppingCart.setImage(setmeal.getImage());
                shoppingCart.setAmount(setmeal.getPrice());
                shoppingCart.setImage(setmeal.getImage());
            }
            shoppingCart.setNumber(1);
            shoppingCart.setCreateTime(LocalDateTime.now());

            shoppingCartMapper.insert(shoppingCart);
        }
    }

    @Override
    public List<ShoppingCart> showShoppingCart() {
        ShoppingCart cart = ShoppingCart.builder()
                .userId(BaseContext.getCurrentId())
                .build();
        return shoppingCartMapper.list(cart);
    }

    @Override
    public void cleanShoppingCart() {
        Long userId = BaseContext.getCurrentId();
        shoppingCartMapper.deleteByUserId(userId);
    }

    @Override
    public void subShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        //查找该用户的购物车记录
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);
        Long userId = BaseContext.getCurrentId();
        shoppingCart.setUserId(userId);

        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);

        if(list != null && list.size() > 0) {
            ShoppingCart cart = list.get(0);
            //查看是否为多个相同商品,删除其中一个
            if(cart.getNumber() > 1) {
                cart.setNumber(cart.getNumber() - 1);
                shoppingCartMapper.updateNumberById(cart);
            }else{
                //为单个商品,删除这条购物车记录
                shoppingCartMapper.deleteById(cart.getId());
            }
        }
    }


}
