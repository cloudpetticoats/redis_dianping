package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    private StringRedisTemplate stringRedisTemplate;
    private String name;

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    private final static String KEY_PREFIX = "lock:";

    @Override
    public boolean tryLock(long timeOutSec) {
        // 获取当前调用线程标识
        long value = Thread.currentThread().getId();
        // 加锁
        Boolean isSuccess = stringRedisTemplate.opsForValue().
                setIfAbsent(KEY_PREFIX + name, value + "", timeOutSec, TimeUnit.SECONDS);

        return Boolean.TRUE.equals(isSuccess);
    }

    @Override
    public void unlock() {
        stringRedisTemplate.delete(KEY_PREFIX + name);
    }
}
