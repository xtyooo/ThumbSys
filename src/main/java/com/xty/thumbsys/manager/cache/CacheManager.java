package com.xty.thumbsys.manager.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.xty.thumbsys.common.AddResult;
import jakarta.annotation.Resource;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;


/**
 * 本地缓存主要用于
 */
@Slf4j
@Component
public class CacheManager {
    private TopK hotKeyDetector;
    private Cache<String,Object> localCache;

    @Bean
    public TopK getHotKeyDetector() {

        hotKeyDetector = new HeavyKeeper(
                // topK的大小
                100,
                // 宽度
                100000,
                // 深度
                5,
                // 衰减系数
                0.92,
                // 最小计数
                10
        );


        return hotKeyDetector;
    }

    @Bean
    public Cache<String,Object> localCache() {
        localCache = Caffeine.newBuilder()
                .maximumSize(100000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();
        return localCache;
    }


    @Resource
    RedisTemplate<String,Object> redisTemplate;

    // 构建缓存key
    public String buildCacheKey(String hashKey, String key){
        return hashKey + ":" + key;
    }

    /**
     * 从缓存中获取数据   并统计是否是热点数据
     * @param hashKey
     * @param key
     * @return
     */
    public Object get(String hashKey, String key){
        // 构建缓存key
        String cacheKey = buildCacheKey(hashKey, key);
        // 先从本地缓存中获取
        Object cacheValue = localCache.getIfPresent(cacheKey);
        if (cacheValue != null){
            log.info("从本地缓存中获取到数据{} = {}", cacheKey, cacheValue);
            //记录访问次数   每次访问计数+1
            hotKeyDetector.add(cacheKey, 1);
            return cacheValue;
        }
        // 本地缓存中没有，从redis中获取
        Object redisValue = redisTemplate.opsForHash().get(hashKey, key);
        if (redisValue == null){
            log.info("redis中查不到{}", cacheKey);
            return null;
        }

        // 到这里说明redis中有数据
        // 记录访问次数   每次访问计数+1
        AddResult addResult = hotKeyDetector.add(cacheKey, 1);
        // 判断是否是热点数据 如果是则缓存到本地缓存中
        if (addResult.isHotKey()){
            localCache.put(cacheKey, redisValue);
        }

        return redisValue;
    }

    public void set(String hashKey, String key, Object value){
        // 构建缓存key
        String cacheKey = buildCacheKey(hashKey, key);
        // 设置到redis中
        redisTemplate.opsForHash().put(hashKey, key, value);
        // 设置到本地缓存中
        localCache.put(cacheKey, value);
    }

    /**
     * 更新本地缓存
     * @param hashKey
     * @param key
     * @param value
     */
    public void putIfPresent(String hashKey, String key, Object value) {
        String compositeKey = buildCacheKey(hashKey, key);
        Object object = localCache.getIfPresent(compositeKey);
        //这里只有key在本地缓存中存在  才会进行更新  而不会主动添加之前不存在的key
        //只有查询时才会进行统计并校验是否符合hotKey   如果满足hotKey条件  则进行添加
        if (object == null) {
            return;
        }
        localCache.put(compositeKey, value);
    }

    // 定时清理过期的热 Key 检测数据
    @Scheduled(fixedRate = 20, timeUnit = TimeUnit.SECONDS)
    public void cleanHotKeys() {
        hotKeyDetector.fading();
    }
}
