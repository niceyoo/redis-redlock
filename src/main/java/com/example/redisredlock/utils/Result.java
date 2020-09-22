package com.example.redisredlock.utils;


import lombok.Data;

import java.io.Serializable;

/**
 * @author niceyoo
 */
@Data
public class Result<T> implements Serializable{

    private static final long serialVersionUID = 1L;

    /**
     * 成功标志
     */
    private boolean success;

    /**
     * 返回代码
     */
    private Integer code;

    /**
     * 消息
     */
    private String message;

    /**
     * 时间戳
     */
    private long timestamp = System.currentTimeMillis();

    /**
     * 结果对象
     */
    private T result;
}
