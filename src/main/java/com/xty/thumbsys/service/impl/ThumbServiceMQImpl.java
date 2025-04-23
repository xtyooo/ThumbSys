package com.xty.thumbsys.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xty.thumbsys.constant.RedisLuaScriptConstant;
import com.xty.thumbsys.listener.thumb.msg.ThumbEvent;
import com.xty.thumbsys.mapper.ThumbMapper;
import com.xty.thumbsys.model.dto.thumb.DoThumbRequest;
import com.xty.thumbsys.model.entity.Thumb;
import com.xty.thumbsys.model.entity.User;
import com.xty.thumbsys.model.enums.LuaStatusEnum;
import com.xty.thumbsys.service.ThumbService;
import com.xty.thumbsys.service.UserService;
import com.xty.thumbsys.util.RedisKeyUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;


@Service("thumbService")
@Slf4j
@RequiredArgsConstructor
public class ThumbServiceMQImpl extends ServiceImpl<ThumbMapper, Thumb>
        implements ThumbService {


    private final UserService userService;

    private final RedisTemplate<String, Object> redisTemplate;

    private final PulsarTemplate<ThumbEvent> pulsarTemplate;

    @Override
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new RuntimeException("参数错误");
        }
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
        Long blogId = doThumbRequest.getBlogId();
        String userThumbKey = RedisKeyUtil.getUserThumbKey(userId);
        Long result = redisTemplate.execute(
                RedisLuaScriptConstant.THUMB_SCRIPT_MQ,
                List.of(userThumbKey),
                blogId
        );
        if (result == -1){
            throw new RuntimeException("用户已点赞");
        }

        ThumbEvent thumbEvent = ThumbEvent.builder()
                .userId(userId)
                .blogId(blogId)
                .type(ThumbEvent.EventType.INCR)
                .eventTime(LocalDateTime.now())
                .build();

        pulsarTemplate.sendAsync("thumb-topic",thumbEvent).exceptionally(ex ->{
            redisTemplate.opsForHash().delete(userThumbKey,blogId.toString(),true);
            log.error("点赞消息发送失败",ex);
            return null;
        });


        return true;
    }

    @Override
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new RuntimeException("参数错误");
        }
        User loginUser = userService.getLoginUser(request);
        Long loginUserId = loginUser.getId();
        Long blogId = doThumbRequest.getBlogId();
        String userThumbKey = RedisKeyUtil.getUserThumbKey(loginUserId);
        // 执行 Lua 脚本，点赞记录从 Redis 删除
        long result = redisTemplate.execute(
                RedisLuaScriptConstant.UNTHUMB_SCRIPT_MQ,
                List.of(userThumbKey),
                blogId
        );
        if (LuaStatusEnum.FAIL.getValue() == result) {
            throw new RuntimeException("用户未点赞");
        }
        ThumbEvent thumbEvent = ThumbEvent.builder()
                .blogId(blogId)
                .userId(loginUserId)
                .type(ThumbEvent.EventType.DECR)
                .eventTime(LocalDateTime.now())
                .build();
        pulsarTemplate.sendAsync("thumb-topic", thumbEvent).exceptionally(ex -> {
            redisTemplate.opsForHash().put(userThumbKey, blogId.toString(), true);
            log.error("点赞事件发送失败: userId={}, blogId={}", loginUserId, blogId, ex);
            return null;
        });

        return true;
    }

    @Override
    public Boolean hasThumb(Long blogId, Long userId) {
        return redisTemplate.opsForHash().hasKey(RedisKeyUtil.getUserThumbKey(userId), blogId.toString());
    }
}
