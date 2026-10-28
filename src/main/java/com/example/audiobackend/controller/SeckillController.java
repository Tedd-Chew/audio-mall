package com.example.audiobackend.controller;

import com.example.audiobackend.common.Result;
import com.example.audiobackend.service.SeckillActivityService;
import com.example.audiobackend.vo.SeckillActivityVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 秒杀活动查询。不做登录要求 —— 详情页谁都能看。
 * 抢购接口在 {@code /api/order/seckill}，那个要登录。
 */
@RestController
@RequestMapping("/api/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillActivityService seckillActivityService;

    @GetMapping("/{actId}")
    public Result<SeckillActivityVO> detail(@PathVariable Long actId) {
        return Result.success(seckillActivityService.getById(actId));
    }
}
