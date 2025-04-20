package com.xty.thumbsys.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xty.thumbsys.common.ErrorCode;
import com.xty.thumbsys.constant.ThumbConstant;
import com.xty.thumbsys.mapper.ThumbMapper;
import com.xty.thumbsys.model.dto.thumb.DoThumbRequest;
import com.xty.thumbsys.model.dto.thumb.ThumbInfo;
import com.xty.thumbsys.model.entity.Blog;
import com.xty.thumbsys.model.entity.Thumb;
import com.xty.thumbsys.model.entity.User;
import com.xty.thumbsys.service.BlogService;
import com.xty.thumbsys.service.ThumbService;
import com.xty.thumbsys.service.UserService;
import com.xty.thumbsys.util.RedisKeyUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;


/**
 * @author xtyooo
 */
@Service("thumbServiceDB")
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
                boolean exists = false;
                Object o = redisTemplate.opsForHash().get(RedisKeyUtil.getUserThumbKey(loginUser.getId()), blogId.toString());
                if (o == null) {//说明不在缓存中  可能是没点赞  也可能是帖子发布时间超过一个月了缓存被删除了
                    exists = this.lambdaQuery()
                            .eq(Thumb::getUserId, loginUser.getId())
                            .eq(Thumb::getBlogId, blogId)
                            .exists();

                    if (exists) {//点过赞  但超过一个月   记录从redis中删除
                        throw new RuntimeException("用户已点赞");
                    } else {//没点过赞  执行点赞逻辑
                        boolean update = blogService.lambdaUpdate()
                                .eq(Blog::getId, blogId)
                                .setSql("thumbCount = thumbCount + 1")
                                .update();
                        LambdaQueryWrapper<Blog> select = new LambdaQueryWrapper<Blog>().eq(Blog::getId, blogId)
                                .select(Blog::getCreateTime);
                        Blog blog = blogService.getOne(select);
                        Thumb thumb = new Thumb();
                        thumb.setUserId(loginUser.getId());
                        thumb.setBlogId(blogId);
                        // 更新成功执行加入缓存操作
                        boolean isSuccess = update && this.save(thumb);
                        if (isSuccess) {
                            ThumbInfo thumbInfo = new ThumbInfo();
                            thumbInfo.setThumbId(thumb.getId());
                            thumbInfo.setExpireTime(blog.getCreateTime().getTime() + ThumbConstant.THUMB_EXPIRE_TIME);
                            String key = RedisKeyUtil.getUserThumbKey(loginUser.getId());
                            redisTemplate.opsForHash().put(key, blogId.toString(), thumbInfo);
                        }
                        return isSuccess;
                    }
                } else {//在缓存中 点过赞了 但还需要 需要判断是否过期  过期的话要删除缓存
                    ThumbInfo thumbInfo = (ThumbInfo) o;
                    long currentTime = System.currentTimeMillis();
                    long expireTime = thumbInfo.getExpireTime();
                    if (expireTime < currentTime) {
                        // 缓存过期，删除缓存记录
                        redisTemplate.opsForHash().delete(RedisKeyUtil.getUserThumbKey(loginUser.getId()), blogId.toString());
                    }
                    throw new RuntimeException("用户已点赞");
                }

            });
        }
    }


    /**
     * 取消点赞
     *
     * @param doThumbRequest
     * @param request
     * @return
     */
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

                Object o = redisTemplate.opsForHash().get(RedisKeyUtil.getUserThumbKey(loginUser.getId()), blogId.toString());
                boolean success = false;
                if (o == null) {//说明不在缓存中  可能是没点赞  也可能是帖子发布时间超过一个月了缓存被删除了

                    LambdaQueryWrapper<Thumb> lambdaQueryWrapper = new LambdaQueryWrapper<Thumb>()
                            .eq(Thumb::getUserId, loginUser.getId())
                            .eq(Thumb::getBlogId, blogId);
                    Thumb thumb = this.getOne(lambdaQueryWrapper);

                    if (thumb == null) {//没点过赞
                        throw new RuntimeException("用户未点赞");
                    }
                    //点过赞 但超过一个月   记录从redis中删除 取消点赞需要从数据库中删除
                    //博客点赞数-1
                    UpdateWrapper<Blog> updateWrapper = new UpdateWrapper<Blog>().eq("id", blogId).setSql("thumbCount = thumbCount - 1");
                    boolean update = blogService.update(updateWrapper);

                    //点赞表删除对应点赞记录
                    success = update && this.removeById(thumb.getId());
                } else {//在缓存中 点过赞了 删除缓存 和 数据库的点赞记录
                    ThumbInfo thumbInfo = (ThumbInfo) o;
                    //博客点赞数-1
                    UpdateWrapper<Blog> updateWrapper = new UpdateWrapper<Blog>().eq("id", blogId).setSql("thumbCount = thumbCount - 1");
                    boolean update = blogService.update(updateWrapper);

                    //点赞表删除对应点赞记录
                    success = update && this.removeById(thumbInfo.getThumbId());


                    // 点赞记录从 Redis 删除
                    if (success) {
                        redisTemplate.opsForHash().delete(RedisKeyUtil.getUserThumbKey(loginUser.getId()), blogId.toString());
                    }
                }
                return success;
            });
        }
    }

    @Override
    public Boolean hasThumb(Long blogId, Long userId) {
        String key = RedisKeyUtil.getUserThumbKey(userId);

        return redisTemplate.opsForHash().hasKey(key, blogId.toString());
    }


}




