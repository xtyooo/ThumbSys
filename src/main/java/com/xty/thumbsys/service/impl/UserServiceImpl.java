package com.xty.thumbsys.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.xty.thumbsys.constant.UserConstant;
import com.xty.thumbsys.mapper.UserMapper;
import com.xty.thumbsys.model.entity.User;
import com.xty.thumbsys.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * @author xtyooo
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    @Override
    public User getLoginUser(HttpServletRequest request) {
        return (User) request.getSession().getAttribute(UserConstant.LOGIN_USER);
    }

}




