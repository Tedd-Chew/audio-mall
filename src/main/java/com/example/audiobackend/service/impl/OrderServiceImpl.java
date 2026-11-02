package com.example.audiobackend.service.impl;

import com.example.audiobackend.constant.OrderStatus;
import com.example.audiobackend.constant.PayType;
import com.example.audiobackend.constant.PaymentStatus;
import com.example.audiobackend.constant.SaleType;
import com.example.audiobackend.dto.CreateOrderDTO;
import com.example.audiobackend.dto.SeckillOrderMessage;
import com.example.audiobackend.entity.Order;
import com.example.audiobackend.entity.OrderItem;
import com.example.audiobackend.entity.Payment;
import com.example.audiobackend.entity.Product;
import com.example.audiobackend.exception.BusinessException;
import com.example.audiobackend.mapper.OrderItemMapper;
import com.example.audiobackend.mapper.OrderMapper;
import com.example.audiobackend.mapper.PaymentMapper;
import com.example.audiobackend.mapper.ProductMapper;
import com.example.audiobackend.mapper.SeckillActivityMapper;
import com.example.audiobackend.service.OrderService;
import com.example.audiobackend.service.SeckillActivityService;
import com.example.audiobackend.service.SeckillOrderService;
import com.example.audiobackend.util.BizNoGenerator;
import com.example.audiobackend.vo.OrderItemVO;
import com.example.audiobackend.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    /** 普通订单 15 分钟未支付自动关闭 */
    private static final int UNPAID_TIMEOUT_MINUTES = 15;

    /** 秒杀订单 5 分钟未支付自动关闭 —— 名额紧张，不该长时间占着 */
    private static final int SECKILL_TIMEOUT_MINUTES = 5;

    /** 单次最多买 10 件，跟 DTO 上的校验保持一致 */
    private static final int MAX_QUANTITY = 10;

    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final PaymentMapper paymentMapper;
    private final SeckillActivityMapper seckillActivityMapper;
    private final SeckillActivityService seckillActivityService;
    private final SeckillOrderService seckillOrderService;

    /**
     * 用来把普通下单包成一个事务。
     *
     * <p>不用 @Transactional 的原因见 {@link #createNormalOrder} 的注释：
     * 我们需要在事务<b>结束之后</b>捕获唯一键冲突，再查原单返回。
     */
    private final TransactionTemplate transactionTemplate;

    /**
     * 普通下单（带幂等）。
     *
     * <p><b>为什么用 TransactionTemplate，而不是 @Transactional：</b>
     * 撞唯一索引抛出的 {@link DuplicateKeyException} 必须在<b>事务结束之后</b>才能处理。
     * 在 @Transactional 方法内部 catch 它是不行的 —— Spring 已经把事务标成 rollback-only，
     * 之后就算查到原单、想正常返回，提交时也会变成 UnexpectedRollbackException。
     * TransactionTemplate 把"事务在哪结束"写得很直白：execute 返回或抛出，就是边界。
     */
    @Override
    public String createNormalOrder(Long userId, CreateOrderDTO dto) {
        try {
            return transactionTemplate.execute(status -> doCreateNormalOrder(userId, dto));
        } catch (DuplicateKeyException e) {
            // 撞的是 uk_user_request：同一个 (userId, requestId) 已经下过单。
            // 事务已回滚，刚才那次库存扣减也一起撤销了 —— 这里什么都不用补偿。
            Order existing = orderMapper.selectByUserAndRequestId(userId, dto.getRequestId());
            if (existing == null) {
                // requestId 为 null 时唯一索引不会报这个错，能走到这里说明撞的是别的键（order_no）
                throw e;
            }
            log.info("重复下单，返回原订单 orderNo={}，userId={}，requestId={}",
                    existing.getOrderNo(), userId, dto.getRequestId());
            return existing.getOrderNo();
        }
    }

    /**
     * 真正干活的那一段，由 {@link TransactionTemplate} 包成一个事务。
     *
     * <p>扣库存放在插订单之前：万一插入撞了唯一键，事务回滚会把这次扣减一起撤销，
     * 所以不会出现"扣了库存但没订单"。
     */
    private String doCreateNormalOrder(Long userId, CreateOrderDTO dto) {
        // 1. 校验商品
        Product product = productMapper.selectById(dto.getProductId());
        if (product == null) {
            throw new BusinessException("商品不存在");
        }
        if (product.getStatus() == null || product.getStatus() != 1) {
            throw new BusinessException("商品已下架");
        }
        if (dto.getQuantity() > MAX_QUANTITY) {
            throw new BusinessException("单次最多买 " + MAX_QUANTITY + " 件");
        }

        // 2. 扣库存。
        //    判断和扣减在同一条 UPDATE 里完成 —— 如果先 SELECT 判断再 UPDATE，
        //    两个请求之间就有窗口，并发下会超卖。
        int affected = productMapper.decreaseStock(product.getId(), dto.getQuantity());
        if (affected == 0) {
            throw new BusinessException("库存不足");
        }

        // 3. 建订单
        BigDecimal totalAmount = product.getPrice()
                .multiply(BigDecimal.valueOf(dto.getQuantity()));

        Order order = new Order();
        order.setOrderNo(BizNoGenerator.orderNo());
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setSaleType(SaleType.NORMAL);
        order.setRequestId(dto.getRequestId());
        order.setStatus(OrderStatus.UNPAID);
        order.setExpireTime(LocalDateTime.now().plusMinutes(UNPAID_TIMEOUT_MINUTES));
        orderMapper.insert(order);

        // 4. 建订单明细，存商品快照
        OrderItem item = new OrderItem();
        item.setOrderId(order.getId());
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setProductImg(product.getCoverImg());
        item.setUnitPrice(product.getPrice());
        item.setQuantity(dto.getQuantity());
        orderItemMapper.insert(item);

        // 5. 建支付单（待支付）
        Payment payment = new Payment();
        payment.setPayNo(BizNoGenerator.payNo());
        payment.setOrderId(order.getId());
        payment.setUserId(userId);
        payment.setPayType(PayType.FULL);
        payment.setAmount(totalAmount);
        payment.setStatus(PaymentStatus.UNPAID);
        paymentMapper.insert(payment);

        log.info("下单成功，orderNo={}，userId={}，productId={}，数量={}，金额={}",
                order.getOrderNo(), userId, product.getId(), dto.getQuantity(), totalAmount);
        return order.getOrderNo();
    }

    @Override
    public OrderVO getByOrderNo(String orderNo) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        List<OrderItem> items = orderItemMapper.selectByOrderId(order.getId());

        OrderVO vo = new OrderVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setStatus(order.getStatus());
        vo.setCreateTime(order.getCreateTime());
        vo.setExpireTime(order.getExpireTime());
        vo.setItems(items.stream().map(this::toItemVO).toList());

        // 前端要 payNo 才能调发起支付。当前一笔普通订单只有一张支付单，直接取第一张。
        // 等 Sprint 6 做预售（定金 + 尾款两张）时再改这里的取舍。
        List<Payment> payments = paymentMapper.selectByOrderId(order.getId());
        if (!payments.isEmpty()) {
            Payment payment = payments.get(0);
            vo.setPayNo(payment.getPayNo());
            vo.setPaymentStatus(payment.getStatus());
        }

        return vo;
    }

    @Override
    public List<Long> findExpiredOrderIds(int limit) {
        return orderMapper.selectExpiredUnpaid(LocalDateTime.now(), limit)
                .stream()
                .map(Order::getId)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean closeIfUnpaid(Long orderId) {
        // 先改状态：WHERE status = 0 决定"这次关的是不是我"
        if (orderMapper.close(orderId, LocalDateTime.now()) == 0) {
            // 已被支付或已被别的实例关掉 —— 绝不能回滚库存
            return false;
        }

        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            log.error("关单成功但查不到订单，orderId={}", orderId);
            return true;
        }

        // 库存还到哪，取决于下单时扣的是哪一份
        if (order.getSaleType() != null && order.getSaleType() == SaleType.SECKILL) {
            rollbackSeckillStock(order);
        } else {
            for (OrderItem item : orderItemMapper.selectByOrderId(orderId)) {
                productMapper.increaseStock(item.getProductId(), item.getQuantity());
            }
        }

        log.info("超时关单，orderId={}，saleType={}", orderId, order.getSaleType());
        return true;
    }

    /**
     * 秒杀单关单：还活动库存 + 放回 Redis 名额。
     *
     * <p>不能还 {@code product.stock} —— 那份库存在创建活动时就已经扣走了，
     * 还到这里会让商品库存凭空多出来。
     */
    private void rollbackSeckillStock(Order order) {
        if (order.getActivityId() == null) {
            log.error("秒杀订单缺少 activityId，无法回滚库存，orderNo={}", order.getOrderNo());
            return;
        }
        seckillActivityService.releaseSeckillStock(order.getActivityId(), order.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createSeckillOrder(SeckillOrderMessage message) {
        // ① 扣 DB 活动库存 —— 秒杀库存的最终裁决，Redis 多放进来的人在这里被挡住
        if (seckillActivityMapper.decreaseStock(message.getActId()) == 0) {
            // 0 行说明 DB 没库存了，Redis 和 DB 已经不一致。不建单，让事务回滚
            throw new BusinessException(
                    "活动库存已空，Redis 与 DB 不一致，actId=" + message.getActId()
                            + "，orderNo=" + message.getOrderNo());
        }

        // ② 建订单。order_no 唯一索引是幂等的落点：
        //    重复消息在这里抛 DuplicateKeyException，整个事务回滚（连上面的扣减一起撤销）
        Order order = new Order();
        order.setOrderNo(message.getOrderNo());
        order.setUserId(message.getUserId());
        order.setTotalAmount(message.getSeckillPrice());
        order.setSaleType(SaleType.SECKILL);
        order.setActivityId(message.getActId());
        // 秒杀的重复请求在 Redis 那道门就被挡住了，正常情况下走不到这里；
        // 带上 requestId 只是让 DB 唯一索引这一层也能兜底（比如 Redis 整库丢了）。
        order.setRequestId(message.getRequestId());
        order.setStatus(OrderStatus.UNPAID);
        order.setExpireTime(LocalDateTime.now().plusMinutes(SECKILL_TIMEOUT_MINUTES));
        orderMapper.insert(order);

        // ③ 商品快照。名称和图片从商品表取（它们不像价格那样需要严格快照），价格用消息里的
        Product product = productMapper.selectById(message.getProductId());
        if (product == null) {
            throw new BusinessException("商品不存在，productId=" + message.getProductId());
        }
        OrderItem item = new OrderItem();
        item.setOrderId(order.getId());
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setProductImg(product.getCoverImg());
        item.setUnitPrice(message.getSeckillPrice());
        item.setQuantity(1);
        orderItemMapper.insert(item);

        // ④ 支付单
        Payment payment = new Payment();
        payment.setPayNo(BizNoGenerator.payNo());
        payment.setOrderId(order.getId());
        payment.setUserId(message.getUserId());
        payment.setPayType(PayType.FULL);
        payment.setAmount(message.getSeckillPrice());
        payment.setStatus(PaymentStatus.UNPAID);
        paymentMapper.insert(payment);

        log.info("秒杀订单创建成功，orderNo={}，actId={}，userId={}，金额={}",
                order.getOrderNo(), message.getActId(), message.getUserId(), message.getSeckillPrice());
    }

    /**
     * 消费端撞唯一索引之后，判断该 ack 还是该撤销扣减。
     *
     * <p>不在事务里：调用时事务已经回滚了，这里做的都是"事务之后"的收尾。
     */
    @Override
    public void resolveSeckillDuplicate(SeckillOrderMessage message) {
        // 撞 uk_order_no 时按单号必然查得到；撞 uk_user_request 时这个单号是新的，查不到。
        // 所以这一次查询同时回答了"撞的是哪个索引"和"订单是否已经存在"。
        if (orderMapper.selectByOrderNo(message.getOrderNo()) != null) {
            log.info("秒杀消息重复投递，订单已存在，无需处理 orderNo={}", message.getOrderNo());
            return;
        }

        // 走到这里：库里已经有 (userId, requestId) 的另一张单，本次扣减永远建不出来。
        // 不撤销就是永久少卖一件 —— 这是老版本当成"重复消息"直接 ack 掉的地方。
        log.warn("秒杀订单撞 uk_user_request 但本单号不存在，撤销这次扣减。"
                        + "orderNo={}，actId={}，userId={}，requestId={}",
                message.getOrderNo(), message.getActId(), message.getUserId(), message.getRequestId());

        if (!seckillOrderService.compensateRejected(message)) {
            // 撤销失败（Redis 不可用 / 门已过期）→ 这条消息还没处理完，让调用方重试。
            // 补偿脚本是幂等的，重试一次不会多还名额。
            throw new IllegalStateException("撤销秒杀扣减失败，orderNo=" + message.getOrderNo());
        }
    }

    private OrderItemVO toItemVO(OrderItem item) {
        OrderItemVO vo = new OrderItemVO();
        vo.setProductId(item.getProductId());
        vo.setProductName(item.getProductName());
        vo.setProductImg(item.getProductImg());
        vo.setUnitPrice(item.getUnitPrice());
        vo.setQuantity(item.getQuantity());
        return vo;
    }
}
