package com.xty.thumbsys.job;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xty.thumbsys.mapper.BlogMapper;
import com.xty.thumbsys.model.entity.Thumb;
import com.xty.thumbsys.service.ThumbService;
import com.xty.thumbsys.util.RedisKeyUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * @author xty
 * @date 2025/4/20
 *
 * 定时任务  同步缓存中的点赞数到数据库中
 * 1.同步前10秒钟所有加入到缓存thumb:temp:{time}中的值进入数据库  key 为 userId:blogId   value 为点赞数
 * 在thumb表中增加对应的点赞记录
 * 在blog表中增加对应博客的点赞数
 */
@Component
@Slf4j
public class SyncThumb2DBJob {


    @Resource
    private ThumbService thumbService;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private BlogMapper blogMapper;


    /**
     * 例子：[20:41:10，20:41:20) 时间区间的临时数据  在 20:41:24 执行 假如用时4秒执行完毕 那么下次任务执行时间是 20:41:24 + 4 + 10秒 = 20:41:38
     *      但是 如果执行时间过长 比如执行8秒才完成 那么下次任务执行时间是 20:41:24 + 8 + 10秒 = 20:41:42   这次任务处理的是[20:41:10,20:41:20)区间的数据
     *      下次任务开始执行时间是 20:41:24 + 8 + 10秒 = 20:41:42   处理的是[20:41:30,20:41:40)区间的数据]
     *      就会造成中间的数据[20:41:20,20:41:40)没被入库和删除   一直存在于redis中
     */
    // 每十秒执行一次
    @Scheduled(initialDelay = 10000, fixedRate = 10000)
    @Transactional(rollbackFor = Exception.class)
    public void run() {
        log.info("定时任务开始执行");

        DateTime nowDate = DateUtil.date();
        String timeSlice = DateUtil.format(nowDate, "HH:mm:") + (DateUtil.second(nowDate) / 10 - 1) * 10;

        process(timeSlice);

        log.info("定时任务执行结束");
    }

    public void process(String timeSlice) {
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(timeSlice);

        AtomicBoolean needRemove = new AtomicBoolean(false);
        //HMSET "thumb:temp:15:51:10" "2:1" "1"
        Map<Object, Object> map = redisTemplate.opsForHash().entries(tempThumbKey);
        if (CollUtil.isEmpty(map)) {
            return;
        }
        HashMap<Long, Long> blogIdToThumbCountMap = new HashMap<>();
        ArrayList<Thumb> thumbArrayList = new ArrayList<>();
        LambdaQueryWrapper<Thumb> wrapper = new LambdaQueryWrapper<>();
        map.forEach((k, v) -> {
            String[] split = k.toString().split(":");
            Long userId = Long.valueOf(split[0]);
            Long blogId = Long.valueOf(split[1]);

            Long thumbNumber = Long.valueOf(v.toString());//1表示 点赞，-1表示取消点赞 0表示点赞后取消点赞
            if (thumbNumber == 1) {
                Thumb thumb = new Thumb();
                thumb.setUserId(userId);
                thumb.setBlogId(blogId);
                thumbArrayList.add(thumb);
            } else if (thumbNumber == -1) {
                needRemove.set(true);
                wrapper.or().eq(Thumb::getUserId, userId).eq(Thumb::getBlogId, blogId);
            }
            blogIdToThumbCountMap.put(blogId, blogIdToThumbCountMap.getOrDefault(blogId, 0L) + thumbNumber);

        });
        //在thumb表中批量增加对应的点赞记录
        thumbService.saveBatch(thumbArrayList);
        //批量删除thumb表中对应的点赞记录
        if (needRemove.get()){
            thumbService.remove(wrapper);
        }

        //在blog表中增加对应博客的点赞数
        if (!blogIdToThumbCountMap.isEmpty()){
            blogMapper.batchUpdateThumbCount(blogIdToThumbCountMap);
        }


        //使用虚拟线程异步删除redis中的数据
        Thread.startVirtualThread(()->{
            redisTemplate.delete(tempThumbKey);
        });

    }


}
