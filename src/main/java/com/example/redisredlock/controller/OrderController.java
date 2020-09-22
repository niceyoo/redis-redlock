package com.example.redisredlock.controller;

import com.example.redisredlock.bean.Order;
import com.example.redisredlock.server.OrderServer;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.annotation.Resource;

/**
 * @author niceyoo
 */
@RestController
@RequestMapping("/order")
public class OrderController {

    @Resource
    private OrderServer orderServer;

    @PostMapping("/createOrder")
    public boolean createOrder(Order order) {
        return orderServer.createOrder(order.getUserId(), order.getProductId());
    }

}
