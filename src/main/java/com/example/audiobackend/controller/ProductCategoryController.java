package com.example.audiobackend.controller;

import com.example.audiobackend.common.Result;
import com.example.audiobackend.entity.ProductCategory;
import com.example.audiobackend.service.ProductCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分类查询接口（公开，未登录也能访问）。
 * 增删改在 AdminCategoryController。
 */
@RestController
@RequestMapping("/api/category")
@RequiredArgsConstructor
public class ProductCategoryController {

    private final ProductCategoryService productCategoryService;

    @GetMapping("/list")
    public Result<List<ProductCategory>> list() {
        return Result.success(productCategoryService.list());
    }

    @GetMapping("/{id}")
    public Result<ProductCategory> getById(@PathVariable Long id) {
        return Result.success(productCategoryService.getById(id));
    }
}
