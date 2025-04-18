package com.xty.thumbsys.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.xty.thumbsys.model.entity.Blog;
import com.xty.thumbsys.model.entity.User;
import com.xty.thumbsys.model.vo.BlogVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * @author xtyooo
 */
public interface BlogService extends IService<Blog> {

    BlogVO getBlogVOById(long blogId, HttpServletRequest request);

    BlogVO getBlogVO(Blog blog, User loginUser);

    List<BlogVO> getBlogVOList(List<Blog> blogList, HttpServletRequest request);
}
