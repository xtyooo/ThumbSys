package com.xty.thumbsys.service.impl;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xty.thumbsys.constant.RedisLuaScriptConstant;
import com.xty.thumbsys.mapper.ThumbMapper;
import com.xty.thumbsys.model.dto.thumb.DoThumbRequest;
import com.xty.thumbsys.model.entity.Thumb;
import com.xty.thumbsys.model.entity.User;
import com.xty.thumbsys.model.enums.LuaStatusEnum;
import com.xty.thumbsys.service.ThumbService;
import com.xty.thumbsys.service.UserService;
import com.xty.thumbsys.util.RedisKeyUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service("thumbServiceRedis")
@Slf4j
@RequiredArgsConstructor
public class ThumbServiceRedisImpl extends ServiceImpl<ThumbMapper, Thumb> implements ThumbService {
  
    private final UserService userService;
  
    private final RedisTemplate<String, Object> redisTemplate;
  
    @Override  
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {  
            throw new RuntimeException("参数错误");  
        }  
        User loginUser = userService.getLoginUser(request);
        Long blogId = doThumbRequest.getBlogId();  
  
        String timeSlice = getTimeSlice();  
        // Redis Key  
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(timeSlice);
        String userThumbKey = RedisKeyUtil.getUserThumbKey(loginUser.getId());  
  
        // 执行 Lua 脚本  
        long result = redisTemplate.execute(  
                RedisLuaScriptConstant.THUMB_SCRIPT,
                Arrays.asList(tempThumbKey, userThumbKey),
                loginUser.getId(),  
                blogId  
        );  
  
        if (LuaStatusEnum.FAIL.getValue() == result) {
            throw new RuntimeException("用户已点赞");  
        }  
  
        // 更新成功才执行  
        return LuaStatusEnum.SUCCESS.getValue() == result;  
    }  
  
    @Override  
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {  
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {  
            throw new RuntimeException("参数错误");  
        }  
        User loginUser = userService.getLoginUser(request);  
  
        Long blogId = doThumbRequest.getBlogId();  
        // 计算时间片  
        String timeSlice = getTimeSlice();  
        // Redis Key  
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(timeSlice);  
        String userThumbKey = RedisKeyUtil.getUserThumbKey(loginUser.getId());  
  
        // 执行 Lua 脚本  
        long result = redisTemplate.execute(  
                RedisLuaScriptConstant.UNTHUMB_SCRIPT,  
                Arrays.asList(tempThumbKey, userThumbKey),  
                loginUser.getId(),  
                blogId  
        );  
        // 根据返回值处理结果  
        if (result == LuaStatusEnum.FAIL.getValue()) {  
            throw new RuntimeException("用户未点赞");  
        }  
        return LuaStatusEnum.SUCCESS.getValue() == result;  
    }  
  
    private String getTimeSlice() {  
        DateTime nowDate = DateUtil.date();
        // 获取到当前时间前最近的整数秒，比如当前 11:20:23 ，获取到 11:20:20  
        return DateUtil.format(nowDate, "HH:mm:") + (DateUtil.second(nowDate) / 10) * 10;  
    }  
  
    @Override  
    public Boolean hasThumb(Long blogId, Long userId) {  
        return redisTemplate.opsForHash().hasKey(RedisKeyUtil.getUserThumbKey(userId), blogId.toString());  
    }  
}
