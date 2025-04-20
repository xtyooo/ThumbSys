package com.xty.thumbsys.job;


import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;


/**
 * 例子：[20:41:10，20:41:20) 时间区间的临时数据  在 20:41:24 执行 假如用时4秒执行完毕 那么下次任务执行时间是 20:41:24 + 4 + 10秒 = 20:41:38
 *      但是 如果执行时间过长 比如执行8秒才完成 那么下次任务执行时间是 20:41:24 + 8 + 10秒 = 20:41:42   这次任务处理的是[20:41:10,20:41:20)区间的数据
 *      下次任务开始执行时间是 20:41:24 + 8 + 10秒 = 20:41:42   处理的是[20:41:30,20:41:40)区间的数据]
 *      就会造成中间的数据[20:41:20,20:41:40)没被入库和删除   一直存在于redis中
 *
 *   定时将 Redis 中的临时点赞数据同步到数据库的补偿措施
 *
 */
@Slf4j
@Component
public class SyncThumb2DBCompensatoryJob {


    @Resource
    RedisTemplate<String, Object> redisTemplate;

    @Resource
    SyncThumb2DBJob syncThumb2DBJob;

    @Scheduled(cron = "0 0 2 * * *")
    public void run() {
        try {
            Set<String> keys = redisTemplate.keys("thumb:temp:*");
            if (keys == null || keys.isEmpty()) {
                return;
            }
            keys.forEach(key -> {
                try {
                    syncThumb2DBJob.process(key.replace("thumb:temp:", ""));
                } catch (Exception e) {
                    log.error("处理键 {} 时出现异常", key, e);
                }
            });
        } catch (Exception e) {
            log.error("获取 Redis 键时出现异常", e);
        }
    }



}
