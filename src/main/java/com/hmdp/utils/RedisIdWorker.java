package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public static final long BEGIN_TIMESTAMP = 1740182400L;
    public static final long BITS = 32;
    public long nextId(String keyPrefix){
        // 获取时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowEpochSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowEpochSecond - BEGIN_TIMESTAMP;

        // 2.生成序列号
        // 2.1 获取当前日期，精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.2自增长
        Long count = stringRedisTemplate.opsForValue().increment("icr" + keyPrefix + ":" + date);

        // 返回全局id
        return timeStamp << BITS | count;
    }

}
