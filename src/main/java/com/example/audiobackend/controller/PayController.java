package com.example.audiobackend.controller;

import com.example.audiobackend.common.Result;
import com.example.audiobackend.dto.PayCallbackDTO;
import com.example.audiobackend.exception.BusinessException;
import com.example.audiobackend.service.PayService;
import com.example.audiobackend.util.JwtUtil;
import com.example.audiobackend.vo.PaymentVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付接口。
 *
 * <p>注意 {@link #callback} 的返回类型是纯文本 String 而不是 {@code Result}，
 * 这不是漏改 —— 支付平台只认 "success" 这个字符串，看到别的内容就认为没收到并重试。
 */
@RestController
@RequestMapping("/api/pay")
@RequiredArgsConstructor
public class PayController {

    private final PayService payService;
    private final JwtUtil jwtUtil;

    /** 发起支付，返回收银台参数 */
    @PostMapping("/{payNo}")
    public Result<PaymentVO> createPay(@PathVariable String payNo,
                                       @RequestHeader("Authorization") String authHeader) {
        return Result.success(payService.createPay(payNo, currentUserId(authHeader)));
    }

    /** 前端轮询支付结果 */
    @GetMapping("/{payNo}/status")
    public Result<Integer> status(@PathVariable String payNo,
                                  @RequestHeader("Authorization") String authHeader) {
        return Result.success(payService.queryStatus(payNo, currentUserId(authHeader)));
    }

    /**
     * 支付平台回调。不走 JWT，身份由 sign 证明。
     *
     * @return success = 已处理，别再发；fail = 处理不了，请重试
     */
    @PostMapping(value = "/callback", produces = MediaType.TEXT_PLAIN_VALUE)
    public String callback(@Valid @RequestBody PayCallbackDTO dto) {
        return payService.handleCallback(dto);
    }

    private Long currentUserId(String authHeader) {
        String token = authHeader.replace("Bearer ", "").trim();
        Long userId = jwtUtil.getUserIdFromToken(token);
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        return userId;
    }
}
