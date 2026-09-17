package com.miaofan.service;

import com.miaofan.dto.UserLoginDTO;
import com.miaofan.entity.User;

public interface UserService {

    /**
     * 用户登录
     */
    User wxLogin(UserLoginDTO userLoginDTO);
}
