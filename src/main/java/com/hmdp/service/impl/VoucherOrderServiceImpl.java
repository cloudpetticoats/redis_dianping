package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIDWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Zhang Haishuo
 * @since 2024-2-3
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService iSeckillVoucherService;
    @Resource
    private RedisIDWorker redisIDWorker;

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
        synchronized (userID.toString().intern()) {
            // 使用代理对象，防止@Transactional失效
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {

        // 查看该用户是否已经购买过
        Long userID = UserHolder.getUser().getId();
        int count = query().eq("voucher_id", voucherId).eq("user_id", userID).count();
        if(count > 0) {
            return Result.fail("已经购买过！");
        }

        // 扣减库存
        boolean isSuccess = iSeckillVoucherService.update().
                setSql("stock = stock - 1").eq("voucher_id", voucherId)
                .gt("stock", 0)    // 添加乐观锁, 原始的乐观锁是 = stack，但那样会造成失败率太高，
                // 故而只让其判断在自己修改的时候stack是否大于0
                .update();
        if (!isSuccess) {
            return Result.fail("库存不足！");
        }

        // 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 设置订单ID
        long orderID = redisIDWorker.nextId("order");
        voucherOrder.setId(orderID);
        // 设置用户ID
        voucherOrder.setUserId(userID);
        // 设置代金券ID
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);

        return Result.ok(orderID);
    }
}
