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
import com.example.audiobackend.util.BizNoGenerator;
import com.example.audiobackend.vo.OrderItemVO;
import com.example.audiobackend.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createNormalOrder(Long userId, CreateOrderDTO dto) {
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
