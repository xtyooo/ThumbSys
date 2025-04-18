package com.xty.thumbsys.service.impl;


import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xty.thumbsys.common.ErrorCode;
import com.xty.thumbsys.constant.ThumbConstant;
import com.xty.thumbsys.mapper.ThumbMapper;
import com.xty.thumbsys.model.dto.thumb.DoThumbRequest;
import com.xty.thumbsys.model.entity.Blog;
import com.xty.thumbsys.model.entity.Thumb;
import com.xty.thumbsys.model.entity.User;
import com.xty.thumbsys.service.BlogService;
import com.xty.thumbsys.service.ThumbService;
import com.xty.thumbsys.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;

/**
 * @author xtyooo
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ThumbServiceImpl extends ServiceImpl<ThumbMapper, Thumb>
        implements ThumbService {

    private final UserService userService;

    private final BlogService blogService;

    private final TransactionTemplate transactionTemplate;

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new RuntimeException("参数错误");
        }
        User loginUser = userService.getLoginUser(request);
        if (loginUser == null) {
            throw new RuntimeException(ErrorCode.NOT_LOGIN_ERROR.getMessage());
        }
        // 加锁
        synchronized (loginUser.getId().toString().intern()) {

            // 编程式事务
            return transactionTemplate.execute(status -> {
                Long blogId = doThumbRequest.getBlogId();

                // TODO: 2025/4/19 缓存过期问题    待解决 
                boolean exists;
//                if (isHot(doThumbRequest.getCreateTime())){
                    //说明缓存未过期，进行缓存查询
                    exists = hasThumb(blogId, loginUser.getId());
//                }else {
//                    //说明缓存已过期，进行数据库查询
//                    exists = this.lambdaQuery()
//                            .eq(Thumb::getUserId, loginUser.getId())
//                            .eq(Thumb::getBlogId, blogId)
//                            .exists();
//                }

                if (exists) {
                    throw new RuntimeException("用户已点赞");
                }

                boolean update = blogService.lambdaUpdate()
                        .eq(Blog::getId, blogId)
                        .setSql("thumbCount = thumbCount + 1")
                        .update();

                Thumb thumb = new Thumb();
                thumb.setUserId(loginUser.getId());
                thumb.setBlogId(blogId);
                // 更新成功执行加入缓存操作
                boolean isSuccess = update && this.save(thumb);
                if (isSuccess){
                    String key = ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId().toString();
                    redisTemplate.opsForHash().put(key, blogId.toString(), thumb.getId());


                }

                return isSuccess;
            });
        }
    }

    @Override
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new RuntimeException("参数错误");
        }
        User loginUser = userService.getLoginUser(request);
        // 加锁
        synchronized (loginUser.getId().toString().intern()) {

            // 编程式事务
            return transactionTemplate.execute(status -> {
                Long blogId = doThumbRequest.getBlogId();
//                QueryWrapper<Thumb> queryWrapper = new QueryWrapper<Thumb>().eq("userId", loginUser.getId()).eq("blogId", blogId);
//                Thumb thumb = this.getOne(queryWrapper);
                Object thumbIdObj = redisTemplate.opsForHash().get(ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId().toString(), blogId.toString());
                if (thumbIdObj == null) {
                    System.out.println("用户未点赞"+thumbIdObj);
                    throw new RuntimeException("用户未点赞");
                }
                Long thumbId = (Long) thumbIdObj;
                //博客点赞数-1
                UpdateWrapper<Blog> updateWrapper = new UpdateWrapper<Blog>().eq("id", blogId).setSql("thumbCount = thumbCount - 1");
                boolean update = blogService.update(updateWrapper);

                //点赞表删除对应点赞记录
                return update && this.removeById(thumbId);
            });
        }
    }

    @Override
    public Boolean hasThumb(Long blogId, Long userId) {
        String key = ThumbConstant.USER_THUMB_KEY_PREFIX + userId.toString();

        return redisTemplate.opsForHash().hasKey(key, blogId.toString());
    }


    /**
     * 判断帖子是否是一个月内发布的
     */
    private boolean isHot(Date blogCreateTime) {
        long currentTimeMillis = System.currentTimeMillis();
        long time = blogCreateTime.getTime();
        return currentTimeMillis - time < 30L * 24 * 60 * 60 * 1000;
    }


}




