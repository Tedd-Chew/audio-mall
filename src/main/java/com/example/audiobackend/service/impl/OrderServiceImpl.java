package com.example.audiobackend.service.impl;

import com.example.audiobackend.constant.OrderStatus;
import com.example.audiobackend.constant.PayType;
import com.example.audiobackend.constant.PaymentStatus;
import com.example.audiobackend.constant.SaleType;
import com.example.audiobackend.dto.CreateOrderDTO;
import com.example.audiobackend.entity.Order;
import com.example.audiobackend.entity.OrderItem;
import com.example.audiobackend.entity.Payment;
import com.example.audiobackend.entity.Product;
import com.example.audiobackend.exception.BusinessException;
import com.example.audiobackend.mapper.OrderItemMapper;
import com.example.audiobackend.mapper.OrderMapper;
import com.example.audiobackend.mapper.PaymentMapper;
import com.example.audiobackend.mapper.ProductMapper;
import com.example.audiobackend.service.OrderService;
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

    /** 单次最多买 10 件，跟 DTO 上的校验保持一致 */
    private static final int MAX_QUANTITY = 10;

    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final PaymentMapper paymentMapper;

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
        return vo;
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
