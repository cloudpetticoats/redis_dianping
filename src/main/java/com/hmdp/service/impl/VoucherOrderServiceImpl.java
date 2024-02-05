package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIDWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Zhang Haishuo
 * @since 2024-2-3
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService iSeckillVoucherService;
    @Resource
    private RedisIDWorker redisIDWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private final static DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // 阻塞队列
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    // 线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    // 从阻塞队列中获取订单
                    VoucherOrder voucherOrder = orderTasks.take();
                    // 创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("创建订单时异常：", e);
                }
            }
        }
    }

    private IVoucherOrderService proxy;
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        // 这里的锁也可以不用，因为之前已经判断完了
        SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, "order:" + userId);
        boolean isSuccess = lock.tryLock(5);
        if (!isSuccess) {
            log.error("fail");
            return;
        }

        try {
            // 使用代理对象，防止@Transactional失效
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 调用lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        int i = result.intValue();
        if(i != 0) {
            return Result.fail(i == 1 ? "库存不足！" : "不能重复下单！");
        }

        // 把订单信息存到阻塞队列中
        VoucherOrder voucherOrder = new VoucherOrder();
        // 设置订单ID
        long orderId = redisIDWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 设置用户ID
        voucherOrder.setUserId(userId);
        // 设置代金券ID
        voucherOrder.setVoucherId(voucherId);
        orderTasks.add(voucherOrder);

        // 获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        return Result.ok(orderId);
    }

    /*
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 查询秒杀券
        SeckillVoucher voucher = iSeckillVoucherService.getById(voucherId);

        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始！");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已结束！");
        }
        if(voucher.getStock() < 1) {
            return Result.fail("库存不足！");
        }

        Long userID = UserHolder.getUser().getId();
        // 给调用的用户id加锁，每个id加一把锁即可，不同的用户可以并行访问
        //synchronized (userID.toString().intern()) {

        SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, "order:" + userID);
        boolean isSuccess = lock.tryLock(5);
        if (!isSuccess) {
            return Result.fail("不允许重复下单！");
        }

        try {
            // 使用代理对象，防止@Transactional失效
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            lock.unlock();
        }
    }
    */

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {

        // 这里的判断也用处不大
        // 查看该用户是否已经购买过
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        int count = query().eq("voucher_id", voucherId).eq("user_id", userId).count();
        if(count > 0) {
            log.error("已经购买过！");
        }

        // 扣减库存
        boolean isSuccess = iSeckillVoucherService.update().
                setSql("stock = stock - 1").eq("voucher_id", voucherId)
                .gt("stock", 0)    // 添加乐观锁, 原始的乐观锁是 = stack，但那样会造成失败率太高，
                // 故而只让其判断在自己修改的时候stack是否大于0
                .update();
        if (!isSuccess) {
            log.error("库存不足！");
        }

        save(voucherOrder);
    }
}
