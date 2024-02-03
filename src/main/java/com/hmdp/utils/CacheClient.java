package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Component
@Slf4j
public class CacheClient {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 创建线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    private <R, ID> R queryWithPassThrough(
            String preKey, ID id, Class<R> type, Function<ID, R> dbFallBack, Long time, TimeUnit unit) {
        // 从redis中查数据
        String json = stringRedisTemplate.opsForValue().get(preKey + id);
        if (StrUtil.isNotBlank(json)) {
            return BeanUtil.toBean(json, type);
        }
        // 判断命中的是否为空值
        if(json != null) {
            return null;
        }
        // 从数据库中查数据
        R r = dbFallBack.apply(id);
        if(r == null) {
            // 保存空值，使用 缓存空对象 的方法解决缓存穿透
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 保存到redis
        this.set(preKey + id, r, time, unit);
        return r;
    }

    private <R, ID> R queryWithLogicalExpire(
            String preKet, ID id, Class<R> type, Function<ID, R> dbFallBack, Long time, TimeUnit unit) {
        // 从redis中查数据
        String json = stringRedisTemplate.opsForValue().get(preKet + id);
        if (StrUtil.isBlank(json)) {
            return null;
        }
        // 将Json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }
        // 过期了，重建缓存
        Boolean isLock = tryLock(preKet + id);
        if(isLock) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    R r1 = dbFallBack.apply(id);
                    setWithLogicalExpire(preKet + id, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(preKet + id);
                }
            });
        }
        return r;
    }

    private Boolean tryLock(String key) {
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(b);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
