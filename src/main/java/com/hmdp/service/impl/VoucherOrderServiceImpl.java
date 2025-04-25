package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        //1. 查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            log.error("秒杀尚未开始");
            return Result.fail("秒杀尚未开始");
        }
        //3.判断秒杀是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            log.error("秒杀已经结束");
            return Result.fail("秒杀已经结束");
        }
        //4.判断库存是否充足
        if (voucher.getStock() < 1) {
            log.error("库存不足");
            return Result.fail("库存不足");
        }
        //5.判断用户是否已经购买过
        Long userId = UserHolder.getUser().getId();

        // 创建锁对象
       SimpleRedisLock lock = new SimpleRedisLock("order" + userId, stringRedisTemplate);
//        RLock lock = redissonClient.getLock("lock:order" + userId);
        // 获取锁
        boolean isLock = lock.tryLock(1200);

        if (!isLock) {
            log.error("用户已经购买过一次");
            return Result.fail("用户已经购买过一次");
        }


        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            lock.unlock();
        }


    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            // 用户已经下过单了
            return Result.fail("用户已经购买过一次");
        }

        //5.扣减库存
        seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId).gt("stock", 0)
                .update();
        //6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = IdWorker.getId("order");
        voucherOrder.setId(orderId);

        //设置用户id
        voucherOrder.setUserId(userId);
        //设置代金券id
        voucherOrder.setVoucherId(voucherId);

        return Result.ok(voucherId);
    }
}
