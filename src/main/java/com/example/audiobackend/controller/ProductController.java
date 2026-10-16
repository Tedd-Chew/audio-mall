package com.example.audiobackend.controller;

import com.example.audiobackend.common.Result;
import com.example.audiobackend.entity.Product;
import com.example.audiobackend.entity.ProductCategory;
import com.example.audiobackend.mapper.ProductCategoryMapper;
import com.example.audiobackend.service.ProductService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/product")
@CrossOrigin(originPatterns = "*")
public class ProductController {

    private final ProductService productService;
    private final ProductCategoryMapper productCategoryMapper;

    @GetMapping("/list")
    public Result<List<Product>> list() {
        List<Product> products = productService.list();
        
        for (Product product : products) {
            if (product.getCategoryId() != null) {
                ProductCategory category = productCategoryMapper.selectById(product.getCategoryId());
                if (category != null) {
                    product.setCategoryName(category.getName());
                } else {
                    product.setCategoryName("未知分类");
                }
            } else {
                product.setCategoryName("未分类");
            }
        }
        
        return Result.success(products);
    }

    @GetMapping("/page")
    public Result<Page<Product>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "3") Integer pageSize
    ) {
        return Result.success(productService.getProductPage(pageNum, pageSize));
    }
    @GetMapping("/{id}")
    public Result<Product> getById(@PathVariable Long id) {
        Product product = productService.getById(id);
        if (product != null && product.getCategoryId() != null) {
            ProductCategory category = productCategoryMapper.selectById(product.getCategoryId());
            if (category != null) {
                product.setCategoryName(category.getName());
            } else {
                product.setCategoryName("未知分类");
            }
        }
        return Result.success(product);
    }

    @PostMapping("/add")
    public Result<Product> add(@RequestBody Product product) {
        productService.add(product);
        return Result.success(product);
    }

    @PutMapping("/update")
    public Result<Void> update(@RequestBody Product product) {
        productService.update(product);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return Result.success();
    }

    @GetMapping("/admin/list")
    public Result<List<Product>> adminList() {
        List<Product> products = productService.listAll();
        for (Product product : products) {
            if (product.getCategoryId() != null) {
                ProductCategory category = productCategoryMapper.selectById(product.getCategoryId());
                product.setCategoryName(category != null ? category.getName() : "未知分类");
            } else {
                product.setCategoryName("未分类");
            }
        }
        return Result.success(products);
    }

    @PutMapping("/toggle/{id}")
    public Result<Void> toggleDelete(@PathVariable Long id) {
        productService.toggleDelete(id);
        return Result.success();
    }

    public ProductController(ProductService productService, ProductCategoryMapper productCategoryMapper)
    {
        this.productService = productService;
        this.productCategoryMapper = productCategoryMapper;
    }

}