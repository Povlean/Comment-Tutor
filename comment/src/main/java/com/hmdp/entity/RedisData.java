package com.hmdp.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @description:Redis逻辑过期时间类
 * @author:Povlean
 */
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
