package com.example.audiobackend.controller;

import com.example.audiobackend.common.PageResult;
import com.example.audiobackend.common.Result;
import com.example.audiobackend.dto.ProductQuery;
import com.example.audiobackend.service.ProductService;
import com.example.audiobackend.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品查询接口（公开，未登录也能访问）。
 * 增删改在 AdminProductController，路径带 /api/admin/，拦截器会校验管理员角色。
 */
@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * 商品列表，分页。
     * 例：/api/product/page?categoryId=1&status=1&pageNum=1&pageSize=10
     * 前台调用应带 status=1 只看上架的；不传 status 是后台用法。
     */
    @GetMapping("/page")
    public Result<PageResult<ProductVO>> page(ProductQuery query) {
        return Result.success(productService.page(query));
    }

    /** 商品详情，走 Redis 缓存 */
    @GetMapping("/{id}")
    public Result<ProductVO> detail(@PathVariable Long id) {
        return Result.success(productService.getDetail(id));
    }
}
