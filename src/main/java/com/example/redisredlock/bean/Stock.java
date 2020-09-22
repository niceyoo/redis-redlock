package com.example.redisredlock.bean;

import com.example.redisredlock.base.BaseEntity;
import com.example.redisredlock.utils.SnowFlakeUtil;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.format.annotation.DateTimeFormat;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.Date;

/**
 * 库存表
 *
 * @author niceyoo
 */
@Data
@Entity
@Table(name = "stock")
public class Stock extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 用产品id,设置为库存id
     */

    /**
     * 库存数量
     */
    private Integer stockNum;


}