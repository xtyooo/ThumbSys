package com.xty.thumbsys.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xty.thumbsys.constant.ThumbConstant;
import com.xty.thumbsys.mapper.BlogMapper;
import com.xty.thumbsys.model.entity.Blog;
import com.xty.thumbsys.model.entity.Thumb;
import com.xty.thumbsys.model.entity.User;
import com.xty.thumbsys.model.vo.BlogVO;
import com.xty.thumbsys.service.BlogService;
import com.xty.thumbsys.service.ThumbService;
import com.xty.thumbsys.service.UserService;
import com.xty.thumbsys.util.RedisKeyUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author xtyooo
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog>
        implements BlogService {

    @Resource
    private UserService userService;

    @Resource
    @Lazy
    private ThumbService thumbService;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public BlogVO getBlogVOById(long blogId, HttpServletRequest request) {
        Blog blog = this.getById(blogId);
        User loginUser = userService.getLoginUser(request);
        return this.getBlogVO(blog, loginUser);
    }

    @Override
    public BlogVO getBlogVO(Blog blog, User loginUser) {
        BlogVO blogVO = new BlogVO();
        BeanUtil.copyProperties(blog, blogVO);

//        Thumb thumb = thumbService.lambdaQuery()
//                .eq(Thumb::getUserId, loginUser.getId())
//                .eq(Thumb::getBlogId, blog.getId())
//                .one();

        Boolean isHas = thumbService.hasThumb(blog.getId(), loginUser.getId());

        blogVO.setHasThumb(isHas);

        return blogVO;
    }

    @Override
    public List<BlogVO> getBlogVOList(List<Blog> blogList, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Map<Long, Boolean> blogIdHasThumbMap = new HashMap<>();
        if (ObjUtil.isNotEmpty(loginUser)) {
            List<Object> blogIdList = blogList.stream().map(Blog::getId).collect(Collectors.toList());
            // 获取点赞
//            List<Thumb> thumbList = thumbService.lambdaQuery()
//                    .eq(Thumb::getUserId, loginUser.getId())
//                    .in(Thumb::getBlogId, blogIdSet)
//                    .list();

            List<Object> thumbList = redisTemplate.opsForHash().multiGet(RedisKeyUtil.getUserThumbKey(loginUser.getId()), blogIdList);

            for (int i = 0; i < thumbList.size(); i++) {
                if (thumbList.get(i)==null){
                     continue;
                }
                blogIdHasThumbMap.put(Long.valueOf(blogIdList.get(i).toString()), true);
            }

        }

        return blogList.stream()
                .map(blog -> {
                    BlogVO blogVO = BeanUtil.copyProperties(blog, BlogVO.class);
                    blogVO.setHasThumb(blogIdHasThumbMap.getOrDefault(blog.getId(), false));
                    return blogVO;
                })
                .toList();
    }
}




