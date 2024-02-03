package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIDWorker {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    private static final long BEGIN_TIMESTAMP = 1640995200L;

    public long nextId(String preKey) {
        // 获取时间戳
        LocalDateTime now = LocalDateTime.now();
        long time = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = time - BEGIN_TIMESTAMP;

        // 生成序列号
        // 获取当天日期并格式化
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count = stringRedisTemplate.opsForValue().increment("icr:" + preKey + ":" + date);

        // 拼接并返回
        return timestamp << 32 | count;
    }

}
