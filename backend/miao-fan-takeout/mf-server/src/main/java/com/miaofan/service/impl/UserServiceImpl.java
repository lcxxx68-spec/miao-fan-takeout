package com.miaofan.service.impl;

import com.alibaba.druid.util.HttpClientUtils;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.miaofan.constant.MessageConstant;
import com.miaofan.dto.UserLoginDTO;
import com.miaofan.entity.User;
import com.miaofan.exception.LoginFailedException;
import com.miaofan.mapper.UserMapper;
import com.miaofan.properties.JwtProperties;
import com.miaofan.properties.WeChatProperties;
import com.miaofan.service.UserService;
import com.miaofan.utils.HttpClientUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class UserServiceImpl implements UserService {

    public static final String WX_LOGIN="https://api.weixin.qq.com/sns/jscode2session";

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    WeChatProperties weChatProperties;

    /**
     * 微信登录
     *
     * @param userLoginDTO
     * @return
     */
    public User wxLogin(UserLoginDTO userLoginDTO) {
        //获取用户的openid
        String openid = getOpenid(userLoginDTO.getCode());

        //判断openid是否为空,为空返回异常
        if(openid==null){
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }

        //查看用户表,查看用户是否注册
        User user = userMapper.getByOpenid(openid);

        //若未注册,直接注册用户
        if(user==null){
            user=User.builder()
                    .openid(openid)
                    .createTime(LocalDateTime.now())
                    .build();

            userMapper.insert(user);
        }

        //返回用户
        return user;
    }

    private String getOpenid(String code) {
        Map<String, String> map=new HashMap<>();
        map.put("appid",weChatProperties.getAppid());
        map.put("secret",weChatProperties.getSecret());
        map.put("js_code",code);
        map.put("grant_type","authorization_code");
        String json = HttpClientUtil.doGet(WX_LOGIN, map);
        JSONObject jsonObject = JSON.parseObject(json);
        return jsonObject.getString("openid");
    }
}
