package com.example.audiobackend.controller;

import com.example.audiobackend.common.Result;
import com.example.audiobackend.dto.CreateOrderDTO;
import com.example.audiobackend.exception.BusinessException;
import com.example.audiobackend.service.OrderService;
import com.example.audiobackend.util.JwtUtil;
import com.example.audiobackend.vo.OrderVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口。需要登录 —— /api/order/** 不在放行列表里。
 */
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final JwtUtil jwtUtil;

    /** 普通下单，返回订单号 */
    @PostMapping
    public Result<String> create(@RequestHeader("Authorization") String authHeader,
                                 @Valid @RequestBody CreateOrderDTO dto) {
        return Result.success(orderService.createNormalOrder(currentUserId(authHeader), dto));
    }

    /** 订单详情，前端支付后轮询用 */
    @GetMapping("/{orderNo}")
    public Result<OrderVO> detail(@PathVariable String orderNo) {
        return Result.success(orderService.getByOrderNo(orderNo));
    }

    /**
     * 从 token 里取当前用户 id。
     * 拦截器已经校验过 token 有效性，这里只是再解析一次拿 id。
     * （以后可以把用户信息放进 ThreadLocal，避免重复解析。）
     */
    private Long currentUserId(String authHeader) {
        String token = authHeader.replace("Bearer ", "").trim();
        Long userId = jwtUtil.getUserIdFromToken(token);
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        return userId;
    }
}
