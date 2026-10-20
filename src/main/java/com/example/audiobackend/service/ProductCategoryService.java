package com.example.audiobackend.service;

import com.example.audiobackend.entity.ProductCategory;

import java.util.List;

/**
 * 商品分类服务。
 */
public interface ProductCategoryService {

    /** 全部分类，按 sort 升序 */
    List<ProductCategory> list();

    ProductCategory getById(Long id);

    /** 新增分类，返回新主键 */
    Long add(ProductCategory category);

    void update(Long id, ProductCategory category);

    void delete(Long id);
}
