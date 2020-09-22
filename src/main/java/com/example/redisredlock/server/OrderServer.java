package com.example.redisredlock.server;

import com.example.redisredlock.base.BaseService;
import com.example.redisredlock.bean.Order;

/**
 * 订单server
 */
public interface OrderServer extends BaseService<Order, String> {

    /**
     * 创建订单
     * @param userId
     * @param productId
     * @return
     */
    public boolean createOrder(String userId, String productId);

}
