package com.example.audiobackend.constant;

/**
 * 用户角色。
 * 目前只有两种，还没到需要 RBAC（角色-权限表）的复杂度。
 */
public final class UserRole {

    /** 买家 */
    public static final String BUYER = "BUYER";

    /** 后台管理员 */
    public static final String ADMIN = "ADMIN";

    private UserRole() {
    }
}
