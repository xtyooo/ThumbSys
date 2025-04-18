package com.xty.thumbsys.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xty.thumbsys.model.entity.User;
import jakarta.servlet.http.HttpServletRequest;

/**
 * @author xtyooo
 */
public interface UserService extends IService<User> {

    User getLoginUser(HttpServletRequest request);
}
