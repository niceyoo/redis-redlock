package com.example.redisredlock.server.impl;

import com.example.redisredlock.bean.Stock;
import com.example.redisredlock.dao.StockDao;
import com.example.redisredlock.server.StockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
public class StockServerImpl implements StockService {

    @Autowired
    private StockDao stockDao;

    @Override
    public StockDao getRepository() {
        return stockDao;
    }

    /**
     * 减库存
     *
     * @param productId
     * @return
     */
    @Override
    public boolean decrease(String productId) {
        Stock one = stockDao.getOne(productId);
        int stockNum = one.getStockNum() - 1;
        one.setStockNum(stockNum);
        stockDao.saveAndFlush(one);
        return true;
    }
}
