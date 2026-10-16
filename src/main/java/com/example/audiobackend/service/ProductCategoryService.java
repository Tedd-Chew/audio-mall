package com.example.audiobackend.service;

import com.example.audiobackend.entity.ProductCategory;
import java.util.List;

public interface ProductCategoryService {
    List<ProductCategory> list();
    ProductCategory getById(Long id);
    boolean add(ProductCategory category);
    boolean update(ProductCategory category);
    boolean delete(Long id);
}