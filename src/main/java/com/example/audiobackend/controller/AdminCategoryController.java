package com.example.audiobackend.controller;

import com.example.audiobackend.common.Result;
import com.example.audiobackend.dto.CategoryDTO;
import com.example.audiobackend.entity.ProductCategory;
import com.example.audiobackend.service.ProductCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 分类管理接口。路径在 /api/admin/ 下，需要管理员角色。
 */
@RestController
@RequestMapping("/api/admin/category")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final ProductCategoryService productCategoryService;

    @PostMapping
    public Result<Long> add(@Valid @RequestBody CategoryDTO dto) {
        ProductCategory category = new ProductCategory();
        category.setName(dto.getName());
        category.setSort(dto.getSort());
        return Result.success(productCategoryService.add(category));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CategoryDTO dto) {
        ProductCategory category = new ProductCategory();
        category.setName(dto.getName());
        category.setSort(dto.getSort());
        productCategoryService.update(id, category);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productCategoryService.delete(id);
        return Result.success();
    }
}
