package com.example.audiobackend.service.impl;

import com.example.audiobackend.entity.ProductCategory;
import com.example.audiobackend.mapper.ProductCategoryMapper;
import com.example.audiobackend.service.ProductCategoryService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductCategoryServiceImpl implements ProductCategoryService {

    private static final Logger log = LoggerFactory.getLogger(ProductCategoryServiceImpl.class);

    private final ProductCategoryMapper productCategoryMapper;

    @Override
    public List<ProductCategory> list() {
        try {
            List<ProductCategory> categories = productCategoryMapper.selectList(null);
            log.info("查询产品类别列表成功，数量: {}", categories.size());
            return categories;
        } catch (Exception e) {
            log.error("查询产品类别列表失败，错误: {}", e.getMessage());
            throw new RuntimeException("查询产品类别列表失败", e);
        }
    }

    @Override
    public ProductCategory getById(Long id) {
        try {
            if (id == null || id <= 0) {
                log.warn("获取产品类别失败，ID不合法: {}", id);
                return null;
            }
            ProductCategory category = productCategoryMapper.selectById(id);
            if (category != null) {
                log.info("获取产品类别成功，类别ID: {}", id);
            } else {
                log.warn("获取产品类别失败，未找到该类别，类别ID: {}", id);
            }
            return category;
        } catch (Exception e) {
            log.error("获取产品类别异常，类别ID: {}, 错误: {}", id, e.getMessage());
            throw new RuntimeException("获取产品类别失败", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean add(ProductCategory category) {
        try {
            if (category == null) {
                log.warn("添加产品类别失败，类别对象为空");
                return false;
            }

            String name = category.getName();
            if (name == null || name.trim().isEmpty()) {
                log.warn("添加产品类别失败，类别名称为空");
                throw new IllegalArgumentException("分类名称不能为空");
            }

            if (name.contains(" ")) {
                log.warn("添加产品类别失败，类别名称包含空格: {}", name);
                throw new IllegalArgumentException("分类名称不能包含空格，如需连接多个单词请使用下划线（_）");
            }

            int insertCount = productCategoryMapper.insert(category);
            boolean success = insertCount > 0;
            if (success) {
                log.info("添加产品类别成功，类别ID: {}", category.getId());
            } else {
                log.warn("添加产品类别失败");
            }
            return success;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("添加产品类别异常，错误: {}", e.getMessage());
            throw new RuntimeException("添加产品类别失败", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(ProductCategory category) {
        try {
            if (category == null || category.getId() == null) {
                log.warn("更新产品类别失败，类别对象或ID为空");
                return false;
            }

            String name = category.getName();
            if (name == null || name.trim().isEmpty()) {
                log.warn("更新产品类别失败，类别名称为空");
                throw new IllegalArgumentException("分类名称不能为空");
            }

            if (name.contains(" ")) {
                log.warn("更新产品类别失败，类别名称包含空格: {}", name);
                throw new IllegalArgumentException("分类名称不能包含空格，如需连接多个单词请使用下划线（_）");
            }

            int updateCount = productCategoryMapper.updateById(category);
            boolean success = updateCount > 0;
            if (success) {
                log.info("更新产品类别成功，类别ID: {}", category.getId());
            } else {
                log.warn("更新产品类别失败，未找到该类别，类别ID: {}", category.getId());
            }
            return success;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("更新产品类别异常，类别ID: {}, 错误: {}", category.getId(), e.getMessage());
            throw new RuntimeException("更新产品类别失败", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long id) {
        try {
            if (id == null || id <= 0) {
                log.warn("删除产品类别失败，ID不合法: {}", id);
                return false;
            }
            int deleteCount = productCategoryMapper.deleteById(id);
            boolean success = deleteCount > 0;
            if (success) {
                log.info("【物理删除】产品类别成功，类别ID: {}, 删除记录数: {}", id, deleteCount);
            } else {
                log.warn("删除产品类别失败，未找到该类别，类别ID: {}", id);
            }
            return success;
        } catch (Exception e) {
            log.error("删除产品类别异常，类别ID: {}, 错误: {}", id, e.getMessage());
            throw new RuntimeException("删除产品类别失败", e);
        }
    }
}