package com.example.redisredlock.bean;

import com.example.redisredlock.base.BaseEntity;
import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * 订单表
 *
 * @author niceyoo
 */
@Data
@Entity
@Table(name = "order2")
public class Order extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 订单编号
     */
    private String orderNo;

    /**
     * 下单用户id
     */
    private String userId;

    /**
     * 产品id
     */
    private String productId;

}