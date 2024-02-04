package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
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
    private final static String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    @Override
    public boolean tryLock(long timeOutSec) {
        // 获取当前调用线程标识
        String value = ID_PREFIX + Thread.currentThread().getId();
        // 加锁
        Boolean isSuccess = stringRedisTemplate.opsForValue().
                setIfAbsent(KEY_PREFIX + name, value, timeOutSec, TimeUnit.SECONDS);

        return Boolean.TRUE.equals(isSuccess);
    }

    @Override
    public void unlock() {
        // 每次释放锁的时候判断当前调用者要删的锁是否是他的锁，防止把别人的锁删除掉。
        String value = ID_PREFIX + Thread.currentThread().getId();
        String s = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        if(value.equals(s)) {
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }
}
