### 前言

平时的工作中，由于生产环境中的项目是需要部署在多台服务器中的，所以经常会面临解决分布式场景下数据一致性的问题，那么就需要引入分布式锁来解决这一问题。

针对分布式锁的实现，目前比较常用的就如下几种方案：

1. 基于数据库实现分布式锁 
2. 基于 Redis 实现分布式锁    【本文】
3. 基于 Zookeeper 实现分布式锁

接下来这个系列文章会跟大家一块探讨这三种方案，本篇为 Redis 实现分布式锁篇。

Redis分布式环境搭建推荐：[基于Docker的Redis集群搭建](https://www.cnblogs.com/niceyoo/p/13011626.html)

### Redis分布式锁一览

说到 Redis 锁，能搜到的，或者说常用的无非就下面这两个：

- setNX  + Lua脚本            
- Redisson + RLock可重入锁  【本文】

接下来我们一一探索这两个的实现，本文为 Redisson + RLock可重入锁 实现篇。

### 1、setNX+Lua实现方式

跳转链接：[https://www.cnblogs.com/niceyoo/p/13711149.html](https://www.cnblogs.com/niceyoo/p/13711149.html)

### 2、Redisson介绍

Redisson 是 java 的 Redis 客户端之一，是 Redis 官网推荐的 java 语言实现分布式锁的项目。

Redisson 提供了一些 api 方便操作 Redis。因为本文主要以锁为主，所以接下来我们主要关注锁相关的类，以下是 Redisson 中提供的多样化的锁：

- 可重入锁（Reentrant Lock）
- 公平锁（Fair Lock）
- 联锁（MultiLock）
- 红锁（RedLock）
- 读写锁（ReadWriteLock）
- 信号量（Semaphore） 等等

总之，管你了解不了解，反正 Redisson 就是提供了一堆锁... 也是目前大部分公司使用 Redis 分布式锁最常用的一种方式。

本文中 Redisson 分布式锁的实现是基于 RLock 接口，而 RLock 锁接口实现源码主要是 RedissonLock 这个类，而源码中加锁、释放锁等操作都是使用 Lua 脚本来完成的，并且封装的非常完善，开箱即用。

接下来主要以 Redisson 实现 RLock 可重入锁为主。

### 代码中实现过程

一起来看看在代码中 Redisson 怎么实现分布式锁的，然后再对具体的方法进行解释。

源码地址：[https://github.com/niceyoo/redis-redlock](https://github.com/niceyoo/redis-redlock)

> 篇幅限制，文中代码不全，请以上方源码链接为主。

代码大致逻辑：首先会涉及数据库 2 个表，order2（订单表）、stock（库存表），controller层会提供一个创建订单的接口，创建订单之前，先获取 RedLock 分布式锁，获取锁成功后，在一个事务下减库存，创建订单；最后通过创建大于库存的并发数模拟是否出现超卖的情况。

代码环境：``SpringBoot2.2.2.RELEASE`` + ``Spring Data JPA`` + ``Redisson``

##### 1）Maven 依赖 pom.xml

```
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>2.2.2.RELEASE</version>
        <relativePath/> <!-- lookup parent from repository -->
    </parent>
    <groupId>com.example</groupId>
    <artifactId>redis-redlock</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>redis-redlock</name>
    <description>Demo project for Spring Boot</description>

    <properties>
        <java.version>1.8</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Redis-->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>1.18.10</version>
        </dependency>

        <!-- redisson -->
        <dependency>
            <groupId>org.redisson</groupId>
            <artifactId>redisson</artifactId>
            <version>3.11.1</version>
        </dependency>

        <!-- Gson -->
        <dependency>
            <groupId>com.google.code.gson</groupId>
            <artifactId>gson</artifactId>
            <version>2.8.6</version>
        </dependency>

        <!-- JPA -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- Mysql Connector -->
        <dependency>
            <groupId>mysql</groupId>
            <artifactId>mysql-connector-java</artifactId>
            <version>5.1.48</version>
        </dependency>

        <!-- 数据库连接池 -->
        <dependency>
            <groupId>com.alibaba</groupId>
            <artifactId>druid-spring-boot-starter</artifactId>
            <version>1.1.20</version>
        </dependency>

        <!-- Hutool工具包 -->
        <dependency>
            <groupId>cn.hutool</groupId>
            <artifactId>hutool-all</artifactId>
            <version>4.6.8</version>
        </dependency>

    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

redisson、MySQL 等相关依赖。

##### 2）application.yml 配置文件

```
server:
  port: 6666
  servlet:
    context-path: /

spring:
  # 数据源
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/redis_demo?useUnicode=true&characterEncoding=utf-8&useSSL=false
    username: root
    password: 123456
    type: com.alibaba.druid.pool.DruidDataSource
    driverClassName: com.mysql.jdbc.Driver
    logSlowSql: true
  jpa:
    # 显示sql
    show-sql: false
    # 自动生成表结构
    generate-ddl: true
    hibernate:
      ddl-auto: update
  redis:
    redis:
      cluster:
        nodes: 10.211.55.4:6379, 10.211.55.4:6380, 10.211.55.4:6381
      lettuce:
        pool:
          min-idle: 0
          max-idle: 8
          max-active: 20

# 日志
logging:
  # 输出级别
  level:
    root: info
  file:
    # 指定路径
    path: redis-logs
    # 最大保存天数
    max-history: 7
    # 每个文件最大大小
    max-size: 5MB
```

配置redis，指定数据库地址。

##### 3）Redisson配置类 RedissonConfig.java

```
/**
 * redisson配置类
 */
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient() {
       Config config = new Config();
       config.useClusterServers()
               .setScanInterval(2000)
               .addNodeAddress("redis://10.211.55.4:6379", "redis://redis://10.211.55.4:6380")
               .addNodeAddress("redis://redis://10.211.55.4:6381");
       RedissonClient redisson = Redisson.create(config);
       return redisson;
    }

}
```

##### 4）StockServerImpl 库存实现类，其他参考源码

```
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
```

库存实现类，就一个接口，完成对库存的-1操作。

##### 5）OrderServerImpl 订单实现类（核心代码）

```
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
```

##### 6）Order 订单实体类

```
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
```

##### 7）Stock 库存实体类

```
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
```

##### 8）OrderController 订单接口

```
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
```

### 表结构说明及接口测试部分

因为项目中使用 Spring Data JPA，所以会自动创建数据库表结构，大致为：

stock（库存表）

| id（商品id） | stock_num（库存数量） | create_time（创建时间） | update_time（更新时间） |
| ------------ | --------------------- | ----------------------- | ----------------------- |
| 1234         | 100                   | xxxx                    | xxxx                    |

order2（订单表）

| id（订单id） | order_no（订单号） | user_id（用户id） | product_id（商品id） |
| ------------ | ------------------ | ----------------- | -------------------- |
| xxxx         | xxxx               | xxxx              | 1234                 |

如下是详细表结构+数据：

```
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for order2
-- ----------------------------
DROP TABLE IF EXISTS `order2`;
CREATE TABLE `order2` (
  `id` varchar(64) NOT NULL,
  `create_time` datetime(6) DEFAULT NULL,
  `update_time` datetime(6) DEFAULT NULL,
  `order_no` varchar(255) DEFAULT NULL,
  `user_id` varchar(64) DEFAULT NULL,
  `product_id` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- ----------------------------
-- Table structure for stock
-- ----------------------------
DROP TABLE IF EXISTS `stock`;
CREATE TABLE `stock` (
  `id` varchar(255) NOT NULL,
  `create_time` datetime(6) DEFAULT NULL,
  `update_time` datetime(6) DEFAULT NULL,
  `stock_num` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- ----------------------------
-- Records of stock
-- ----------------------------
BEGIN;
INSERT INTO `stock` VALUES ('1234', '2020-09-21 21:38:09.000000', '2020-09-22 08:32:17.883000', 0);
COMMIT;

SET FOREIGN_KEY_CHECKS = 1;
```

创建订单的过程就是消耗库存表 stock_num 的过程，如果没有分布式锁的情况下，在高并发下很容易出现商品超卖的情况，所以引入了分布式锁的概念，如下是在库存100，并发1000的情况下，测试超卖情况：

**JMeter 模拟进程截图**

![](https://gitee.com/niceyoo/blog/raw/master/img/image-20200922091853399.png)

**JMeter 调用接口截图**

![](https://gitee.com/niceyoo/blog/raw/master/img/image-20200922092802604.png)

**stock 库存表截图**

![](https://gitee.com/niceyoo/blog/raw/master/img/image-20200922092229658.png)

**订单表截图**

![](https://gitee.com/niceyoo/blog/raw/master/img/WX20200922-092627@2x.jpg)

加了锁之后并没有出现超卖情况。

### 核心代码说明

整个 demo 核心代码在创建订单 createOrder() 加锁的过程，如下：

```
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
```

去除业务逻辑，加锁框架结构为:

```
RLock lock = redissonClient.getLock("xxx");

lock.lock();

try {
    ...
} finally {
    lock.unlock();
}
```

#### 关于 RedLock 中的方法

因为 RLock 本身继承自 Lock 接口，如下分为两部分展示：

```
public interface RLock extends Lock, RLockAsync {

    //----------------------Lock接口方法-----------------------

    /**
     * 加锁 锁的有效期默认30秒
     */
    void lock();
    
    /**
     * tryLock()方法是有返回值的，它表示用来尝试获取锁，如果获取成功，则返回true，如果获取失败（即锁已被其他线程获取），则返回false .
     */
    boolean tryLock();
    
    /**
     * tryLock(long time, TimeUnit unit)方法和tryLock()方法是类似的，只不过区别在于这个方法在拿不到锁时会等待一定的时间，
     * 在时间期限之内如果还拿不到锁，就返回false。如果如果一开始拿到锁或者在等待期间内拿到了锁，则返回true。
     *
     * @param time 等待时间
     * @param unit 时间单位 小时、分、秒、毫秒等
     */
    boolean tryLock(long time, TimeUnit unit) throws InterruptedException;
    
    /**
     * 解锁
     */
    void unlock();
    
    /**
     * 中断锁 表示该锁可以被中断 假如A和B同时调这个方法，A获取锁，B为获取锁，那么B线程可以通过
     * Thread.currentThread().interrupt(); 方法真正中断该线程
     */
    void lockInterruptibly();

    //----------------------RLock接口方法-----------------------
    /**
     * 加锁 上面是默认30秒这里可以手动设置锁的有效时间
     *
     * @param leaseTime 锁有效时间
     * @param unit      时间单位 小时、分、秒、毫秒等
     */
    void lock(long leaseTime, TimeUnit unit);
    
    /**
     * 这里比上面多一个参数，多添加一个锁的有效时间
     *
     * @param waitTime  等待时间
     * @param leaseTime 锁有效时间
     * @param unit      时间单位 小时、分、秒、毫秒等
     */
    boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException;
    
    /**
     * 检验该锁是否被线程使用，如果被使用返回True
     */
    boolean isLocked();
    
    /**
     * 检查当前线程是否获得此锁（这个和上面的区别就是该方法可以判断是否当前线程获得此锁，而不是此锁是否被线程占有）
     * 这个比上面那个实用
     */
    boolean isHeldByCurrentThread();
    
    /**
     * 中断锁 和上面中断锁差不多，只是这里如果获得锁成功,添加锁的有效时间
     * @param leaseTime  锁有效时间
     * @param unit       时间单位 小时、分、秒、毫秒等
     */
    void lockInterruptibly(long leaseTime, TimeUnit unit);  
}
```

#### 1、加锁

首先重点在 getLock() 方法，到底是怎么拿到分布式锁的，我们点进该方法：

```
public RLock getLock(String name) {
    return new RedissonLock(this.connectionManager.getCommandExecutor(), name);
}
```

调用 getLock() 方法后实际返回一个 RedissonLock 对象，此时就有点呼应了，文章前面提到的 ``Redisson 普通的锁实现源码主要是 RedissonLock 这个类，而源码中加锁、释放锁等操作都是使用 Lua 脚本来完成的，封装的非常完善，开箱即用。`` 

在 RedissonLock 对象中，主要实现 lock() 方法，而 lock() 方法主要调用 tryAcquire() 方法：

![](https://gitee.com/niceyoo/blog/raw/master/img/image-20200922094539784.png)

tryAcquire() 方法又继续调用 tryAcquireAsync() 方法：

![](https://gitee.com/niceyoo/blog/raw/master/img/image-20200922094802951.png)

到这，由于 leaseTime == -1，于是又调用 tryLockInnerAsync()方法，感觉有点无限套娃那种感觉了...

咳咳，不过这个方法是最关键的了：

![](https://gitee.com/niceyoo/blog/raw/master/img/image-20200922100756048.png)

这个方法就有点意思了，看到了一些熟悉的东西，还记得上一篇里的 Lua 脚本吗？

我们来分析一下这个部分的 Lua 脚本：

```
commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, command,
     "if (redis.call('exists', KEYS[1]) == 0) then " +
         "redis.call('hset', KEYS[1], ARGV[2], 1); " +
         "redis.call('pexpire', KEYS[1], ARGV[1]); " +
         "return nil; " +
     "end; " +
     "if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then " +
         "redis.call('hincrby', KEYS[1], ARGV[2], 1); " +
         "redis.call('pexpire', KEYS[1], ARGV[1]); " +
         "return nil; " +
     "end; " +
     "return redis.call('pttl', KEYS[1]);",
Collections.<Object>singletonList(getName()), internalLockLeaseTime, getLockName(threadId));
```

脚本里，一共是有两个参数 KEYS[1]、通过后面的参数可以得知： KEYS[1] 为 getName()，ARGV[2] 为 getLockName(threadId)。

假设传递加锁参数时传入的 name 值为 "niceyoo"，

假设线程调用的 ID 为 thread-1，

假设 RedissonLock 类的成员变量 UUID 类型的 id 值为 32063ed-98522fc-80287ap，

结合 getLockName(threadId)) 方法：

```
protected String getLockName(long threadId) {
    return this.id + ":" + threadId;
}
```

即，KEYS[1] = niceyoo，ARGV[2] = 32063ed-98522fc-80287ap:thread-1

然后将假设值带入语句中：

1. 判断是否存在名为 “niceyoo” 的 key；
2. 如果没有，则在其下设置一个字段为 “32063ed-98522fc-80287ap:thread-1”，值为 1 的键值对 ，并设置它的过期时间，也就是第一个 if 语句体；
3. 如果存在，则进一步判断 “32063ed-98522fc-80287ap:thread-1” 是否存在，若存在，则其值加 1，并重新设置过期时间，这个过程可以看做锁重入；
4. 返回 “niceyoo” 的生存时间（毫秒）；

如果放在锁这个场景下就是，key 表示当前线程名称，argv 为当前获得锁的线程，所有竞争这把锁的线程都要判断这个 key 下有没有自己的，也就是上边那些  if 判断，如果没有就不能获得锁，如果有，则进入重入锁，字段值+1。

#### 2、解锁

解锁调用的是 unlockInnerAsync() 方法：

![](https://gitee.com/niceyoo/blog/raw/master/img/image-20200922103915505.png)

该方法同样还是调用的 Lua 脚本实现的。

同样还是假设 name=niceyoo，假设线程 ID 是 thread-1

同理，我们可以得到：

KEYS[1] 是 getName()，即 KEYS[1]=niceyoo，

KEYS[2] 是 getChannelName()，即 KEYS[2]=redisson_lock__channel:{niceyoo}，

ARGV[1] 是 LockPubSub.unlockMessage，即ARGV[1]=0，

ARGV[2] 是生存时间，

ARGV[3] 是 getLockName(threadId)，即 ARGV[3]=32063ed-98522fc-80287ap:thread-1

因此，上面脚本的意思是：

1. 判断是否存在 name 为 “niceyoo” 的key；

2. 如果不存在，向 Channel 中广播一条消息，广播的内容是0，并返回1

3. 如果存在，进一步判断字段 32063ed-98522fc-80287ap:thread-1 是否存在

4. 若字段不存在，返回空，若字段存在，则字段值减1

5. 若减完以后，字段值仍大于0，则返回0

6. 减完后，若字段值小于或等于0，则广播一条消息，广播内容是0，并返回1；

可以猜测，广播0表示资源可用，即通知那些等待获取锁的线程现在可以获得锁了。

#### 3、加锁解锁小结

![](https://gitee.com/niceyoo/blog/raw/master/img/image-202009232250443291.png)

![](https://gitee.com/niceyoo/blog/raw/master/img/image-20200926152643744.png)

#### 4、其他补充

#####  4.1 lock() 方法

通常在获得 RLock 时，需要调用 lock() 方法，那么设置过期时间跟不设置有啥区别：

```
RLock lock = redissonClient.getLock("xxx");

/*最常见的使用方法*/
lock.lock();
```

如果没有设置过期时间，默认还是会有一个30秒的过期时间，等价于：

```
RLock lock = redissonClient.getLock("xxx");

/*支持过期解锁，30秒之后自动释放锁，无须调用unlock方法手动解锁*/
lock.lock(30, TimeUnit.SECONDS);
```

##### 4.1 tryLock() 方法

有的小伙在在获取分布式锁时，使用的是 tryLock() 方法，跟 lock() 方法有啥区别：

```
RLock lock = redissonClient.getLock("xxx");

/*尝试加锁，最多等待10秒，上锁以后10秒自动解锁，返回true表示加锁成功*/
if(lock.tryLock(10,10, TimeUnit.SECONDS)){
	xxx
}
```

首先我们来看一下  tryLock() 方法源码：

```
@Override
public boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
    long time = unit.toMillis(waitTime);
    long current = System.currentTimeMillis();
    long threadId = Thread.currentThread().getId();
    Long ttl = tryAcquire(leaseTime, unit, threadId);
    //1、 获取锁同时获取成功的情况下，和lock(...)方法是一样的 直接返回True，获取锁False再往下走
    if (ttl == null) {
        return true;
    }
    //2、如果超过了尝试获取锁的等待时间,当然返回false 了。
    time -= System.currentTimeMillis() - current;
    if (time <= 0) {
        acquireFailed(threadId);
        return false;
    }

    // 3、订阅监听redis消息，并且创建RedissonLockEntry，其中RedissonLockEntry中比较关键的是一个 Semaphore属性对象,用来控制本地的锁请求的信号量同步，返回的是netty框架的Future实现。
    final RFuture<RedissonLockEntry> subscribeFuture = subscribe(threadId);
    //  阻塞等待subscribe的future的结果对象，如果subscribe方法调用超过了time，说明已经超过了客户端设置的最大wait time，则直接返回false，取消订阅，不再继续申请锁了。
    //  只有await返回true，才进入循环尝试获取锁
    if (!await(subscribeFuture, time, TimeUnit.MILLISECONDS)) {
        if (!subscribeFuture.cancel(false)) {
            subscribeFuture.addListener(new FutureListener<RedissonLockEntry>() {
                @Override
                public void operationComplete(Future<RedissonLockEntry> future) throws Exception {
                    if (subscribeFuture.isSuccess()) {
                        unsubscribe(subscribeFuture, threadId);
                    }
                }
            });
        }
        acquireFailed(threadId);
        return false;
    }

   //4、如果没有超过尝试获取锁的等待时间，那么通过While一直获取锁。最终只会有两种结果
    //1)、在等待时间内获取锁成功 返回true。2）等待时间结束了还没有获取到锁那么返回false。
    while (true) {
        long currentTime = System.currentTimeMillis();
        ttl = tryAcquire(leaseTime, unit, threadId);
        // 获取锁成功
        if (ttl == null) {
            return true;
        }
       //   获取锁失败
        time -= System.currentTimeMillis() - currentTime;
        if (time <= 0) {
            acquireFailed(threadId);
            return false;
        }
    }
}
```

tryLock() 方法是申请锁并返回锁有效期还剩的时间，如果为空说明锁未被其他线程申请，那么就直接获取锁并返回，如果获取到时间，则进入等待竞争逻辑。

tryLock() 方法一般用于特定满足需求的场合，但不建议作为一般需求的分布式锁，一般分布式锁建议用 lock(long leaseTime, TimeUnit unit) 方法。因为从性能上考虑，在高并发情况下后者效率是前者的好几倍。

### Redis分布式锁的缺点

在上一节中我们提到了 「setNX+Lua脚本」实现分布式锁在集群模式下的缺陷，

我们再来回顾一下，通常我们为了实现 Redis 的高可用，一般都会搭建 Redis 的集群模式，比如给 Redis 节点挂载一个或多个 slave 从节点，然后采用哨兵模式进行主从切换。但由于 Redis 的主从模式是异步的，所以可能会在数据同步过程中，master 主节点宕机，slave 从节点来不及数据同步就被选举为 master 主节点，从而导致数据丢失，大致过程如下：

1. 用户在 Redis 的 master 主节点上获取了锁；
2. master 主节点宕机了，存储锁的 key 还没有来得及同步到 slave 从节点上；
3. slave 从节点升级为 master 主节点；
4. 用户从新的 master 主节点获取到了对应同一个资源的锁，同把锁获取两次。

ok，然后为了解决这个问题，Redis 作者提出了 RedLock 算法，步骤如下（五步）：

在下面的示例中，我们假设有 5 个完全独立的 Redis Master 节点，他们分别运行在 5 台服务器中，可以保证他们不会同时宕机。

1. 获取当前 Unix 时间，以毫秒为单位。
2. 依次尝试从 N 个实例，使用相同的 key 和随机值获取锁。在步骤 2，当向 Redis 设置锁时，客户端应该设置一个网络连接和响应超时时间，这个超时时间应该小于锁的失效时间。例如你的锁自动失效时间为 10 秒，则超时时间应该在 5-50 毫秒之间。这样可以避免服务器端 Redis 已经挂掉的情况下，客户端还在死死地等待响应结果。如果服务器端没有在规定时间内响应，客户端应该尽快尝试另外一个 Redis 实例。
3. 客户端使用当前时间减去开始获取锁时间（步骤 1 记录的时间）就得到获取锁使用的时间。当且仅当从大多数（这里是 3 个节点）的 Redis 节点都取到锁，并且使用的时间小于锁失效时间时，锁才算获取成功。
4. 如果取到了锁，key 的真正有效时间等于有效时间减去获取锁所使用的时间（步骤 3 计算的结果）。
5. 如果因为某些原因，获取锁失败（没有在至少 N/2+1 个Redis实例取到锁或者取锁时间已经超过了有效时间），客户端应该在所有的 Redis 实例上进行解锁（即便某些 Redis 实例根本就没有加锁成功）。

到这，基本看出来，只要是大多数的 Redis 节点可以正常工作，就可以保证 Redlock 的正常工作。这样就可以解决前面单点 Redis 的情况下我们讨论的节点挂掉，由于异步通信，导致锁失效的问题。

但是细想后， Redlock 还是存在如下问题：

假设一共有5个Redis节点：A, B, C, D, E。设想发生了如下的事件序列：

1. 客户端1成功锁住了A, B, C，获取锁成功（但D和E没有锁住）。
2. 节点C崩溃重启了，但客户端1在C上加的锁没有持久化下来，丢失了。
3. 节点C重启后，客户端2锁住了C, D, E，获取锁成功。
4. 这样，客户端1和客户端2同时获得了锁（针对同一资源）。

哎，还是不能解决故障重启后带来的锁的安全性问题...

针对节点重后引发的锁失效问题，Redis 作者又提出了 **延迟重启** 的概念，大致就是说，一个节点崩溃后，不要立刻重启他，而是等到一定的时间后再重启，等待的时间应该大于锁的过期时间，采用这种方式，就可以保证这个节点在重启前所参与的锁都过期，听上去感觉  **延迟重启**  解决了这个问题...

但是，还是有个问题，节点重启后，在等待的时间内，这个节点对外是不工作的。那么如果大多数节点都挂了，进入了等待，就会导致系统的不可用，因为系统在过期时间内任何锁都无法加锁成功...

巴拉巴拉那么多，关于 Redis 分布式锁的缺点显然进入了一个无解的步骤，包括后来的 神仙打架事件（Redis 作者 antirez 和  分布式领域专家 Martin Kleppmann）...

总之，首先我们要明确使用分布式锁的目的是什么？

无外乎就是保证同一时间内只有一个客户端可以对共享资源进行操作，也就是共享资源的原子性操作。

总之，在 Redis 分布式锁的实现上还有很多问题等待解决，我们需要认识到这些问题并清楚如何正确实现一个 Redis 分布式锁，然后在工作中合理的选择和正确的使用分布式锁。

目前我们项目中也有在用分布式锁，也有用到 Redis 实现分布式锁的场景，然后有的小伙伴就可能问，啊，你们就不怕出现上边提到的那种问题吗~

其实实现分布式锁，从中间件上来选，也有 Zookeeper 可选，并且 Zookeeper 可靠性比 Redis 强太多，但是效率是低了点，如果并发量不是特别大，追求可靠性，那么肯定首选 Zookeeper。

如果是为了效率，就首选 Redis 实现。

好了，之后一起探索 Zookeeper 实现分布式锁。

> 最近换工作了，一切都是重新开始，新的系统、新的人、新的业务…  未来路很长，大家一起加油。

博客园：[https://www.cnblogs.com/niceyoo](https://www.cnblogs.com/niceyoo)
