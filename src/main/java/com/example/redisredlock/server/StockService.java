package com.example.redisredlock.server;

import com.example.redisredlock.base.BaseService;
import com.example.redisredlock.bean.Stock;

/**
 * 库存server
 */
public interface StockService extends BaseService<Stock, String> {

    /**
     * 减库存
     * @param productId
     * @return
     */
    public boolean decrease(String productId);
}
