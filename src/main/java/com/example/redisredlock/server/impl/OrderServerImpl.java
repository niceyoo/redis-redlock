package com.example.redisredlock.server.impl;

import com.example.redisredlock.bean.Order;
import com.example.redisredlock.dao.OrderDao;
import com.example.redisredlock.server.OrderServer;
import com.example.redisredlock.server.StockService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@Transactional
public class OrderServerImpl implements OrderServer {

    /**
     * 库存service
     */
    @Resource
    private StockService stockService;

    /**
     * 订单order dao
     */
    @Resource
    private OrderDao orderDao;

    @Override
    public OrderDao getRepository() {
        return orderDao;
    }

    @Resource
    private RedissonClient redissonClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createOrder(String userId, String productId) {

        //  如果不加锁，必然超卖
        RLock lock = redissonClient.getLock("stock:" + productId);

        try {
            lock.lock(10, TimeUnit.SECONDS);

            int stock = stockService.get(productId).getStockNum();
            log.info("剩余库存：{}", stock);
            if (stock <= 0) {
                return false;
            }

            String orderNo = UUID.randomUUID().toString().replace("-", "").toUpperCase();

            if (stockService.decrease(productId)) {
                Order order = new Order();
                order.setUserId(userId);
                order.setProductId(productId);
                order.setOrderNo(orderNo);
                Date now = new Date();
                order.setCreateTime(now);
                order.setUpdateTime(now);
                orderDao.save(order);
                return true;
            }

        } catch (Exception ex) {
            log.error("下单失败", ex);
        } finally {
            lock.unlock();
        }

        return false;
    }
}
