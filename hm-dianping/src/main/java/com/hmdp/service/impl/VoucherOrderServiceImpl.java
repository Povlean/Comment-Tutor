package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;

/**
 * @description:
 * @author:Povlean
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 获取用户
        Long userId = UserHolder.getUser().getId();
        // 获取订单id
        long order = redisIdWorker.nextId("order");
        // 1.执行Lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        // 2.判断结果为0
        int r = result.intValue();
        if(r != 0) {
            // 2.1不为0，代表没有购买的资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        // 2.2为0,有购买的资格，把下单信息保存到阻塞队列
        long orderId = redisIdWorker.nextId("order");
        // TODO 保存阻塞队列

        // 3.返回订单id
        return Result.ok(0);
    }

    // @Override
    // public Result seckillVoucher(Long voucherId) {
    //     if (voucherId == null) {
    //         return Result.fail("id错误");
    //     }
    //     // 查询数据库
    //     SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
    //     // 判断秒杀是否开始
    //     if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
    //         // 开始时间在现在时间之后，说明活动未开始
    //         return Result.fail("活动尚未开始");
    //     }
    //     if (LocalDateTime.now().isAfter(voucher.getEndTime())) {
    //         // 当前时间在结束时间之后
    //         return Result.fail("活动已结束");
    //     }
    //     // 判断库存是否充足
    //     if (voucher.getStock() < 1) {
    //         return Result.fail("库存不足");
    //     }
    //     // 扣减库存中优惠券的数量
    //     boolean success = seckillVoucherService.update()
    //             .setSql("stock = stock - 1")
    //             .eq("voucher_id", voucherId)
    //             // gt -> 大于, lt -> 小于
    //             .gt("stock",0)
    //             .update();
    //     if (!success) {
    //         return Result.fail("更新失败");
    //     }
    //     Long userId = UserHolder.getUser().getId();
    //     // 创建分布式锁对象
    //     // SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
    //     RLock lock = redissonClient.getLock("lock:order:" + userId);
    //     // 获取锁
    //     boolean isLock = lock.tryLock();
    //     // 判断锁是否获取成功
    //     if (!isLock) {
    //         // 获取锁失败
    //         return Result.fail("不允许重复下单");
    //     }
    //     try {
    //         // 获取代理事务
    //         IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
    //         return proxy.createVoucherOrd(voucherId);
    //     } catch (IllegalStateException e) {
    //         throw new RuntimeException(e);
    //     } finally {
    //         // 释放锁
    //         lock.unlock();
    //     }
    // }

    @Transactional
    public Result createVoucherOrd(Long voucherId) {
        // 一人一单
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()) {
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            if (count > 0) {
                // 当数量大于0时，说明该用户已经购买过秒杀券了
                return Result.fail("不能再次购买秒杀券");
            }
            // 创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            // 设置订单信息
            // 设置订单id
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
            // 设置用户id
            voucherOrder.setUserId(userId);
            // 代金券id
            voucherOrder.setVoucherId(voucherId);
            this.save(voucherOrder);
            // 返回订单id
            return Result.ok(orderId);
        }
    }

}
