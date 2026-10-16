package com.example.audiobackend.controller;

import com.example.audiobackend.entity.ProductCategory;
import com.example.audiobackend.service.ProductCategoryService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/category")
@CrossOrigin(originPatterns = "*")
public class ProductCategoryController {

    private final ProductCategoryService productCategoryService;

    public ProductCategoryController(ProductCategoryService productCategoryService) {
        this.productCategoryService = productCategoryService;
    }

    @GetMapping("/list")
    public Map<String, Object> list() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<ProductCategory> categories = productCategoryService.list();
            response.put("code", 200);
            response.put("message", "success");
            response.put("data", categories);
        } catch (Exception e) {
            response.put("code", 500);
            response.put("message", "获取类别列表失败: " + e.getMessage());
            response.put("data", null);
        }
        return response;
    }

    @GetMapping("/{id}")
    public Map<String, Object> getById(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            ProductCategory category = productCategoryService.getById(id);
            if (category != null) {
                response.put("code", 200);
                response.put("message", "success");
                response.put("data", category);
            } else {
                response.put("code", 404);
                response.put("message", "类别不存在");
                response.put("data", null);
            }
        } catch (Exception e) {
            response.put("code", 500);
            response.put("message", "获取类别详情失败: " + e.getMessage());
            response.put("data", null);
        }
        return response;
    }

    @PostMapping("/add")
    public Map<String, Object> add(@RequestBody ProductCategory category) {
        Map<String, Object> response = new HashMap<>();
        try {
            boolean success = productCategoryService.add(category);
            if (success) {
                response.put("code", 200);
                response.put("message", "添加成功");
                response.put("data", category);
            } else {
                response.put("code", 400);
                response.put("message", "添加失败");
                response.put("data", null);
            }
        } catch (Exception e) {
            response.put("code", 500);
            response.put("message", "添加类别失败: " + e.getMessage());
            response.put("data", null);
        }
        return response;
    }

    @PostMapping("/update")
    public Map<String, Object> update(@RequestBody ProductCategory category) {
        Map<String, Object> response = new HashMap<>();
        try {
            boolean success = productCategoryService.update(category);
            if (success) {
                response.put("code", 200);
                response.put("message", "更新成功");
                response.put("data", category);
            } else {
                response.put("code", 400);
                response.put("message", "更新失败");
                response.put("data", null);
            }
        } catch (Exception e) {
            response.put("code", 500);
            response.put("message", "更新类别失败: " + e.getMessage());
            response.put("data", null);
        }
        return response;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            boolean success = productCategoryService.delete(id);
            if (success) {
                response.put("code", 200);
                response.put("message", "删除成功");
                response.put("data", null);
            } else {
                response.put("code", 400);
                response.put("message", "删除失败");
                response.put("data", null);
            }
        } catch (Exception e) {
            response.put("code", 500);
            response.put("message", "删除类别失败: " + e.getMessage());
            response.put("data", null);
        }
        return response;
    }
}