package com.example.audiobackend.controller;

import com.example.audiobackend.common.Result;
import com.example.audiobackend.dto.LoginDTO;
import com.example.audiobackend.dto.RegisterDTO;
import com.example.audiobackend.service.UserService;
import com.example.audiobackend.util.JwtUtil;
import com.example.audiobackend.vo.LoginVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口。
 * 买家和管理员共用这套登录，靠返回的 role 区分身份。
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    /** 注册，注册出来的一律是买家 */
    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.success(userService.register(dto));
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(userService.login(dto));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "").trim();
        Long userId = jwtUtil.getUserIdFromToken(token);
        if (userId != null) {
            userService.logout(userId);
        }
        return Result.success();
    }
}
