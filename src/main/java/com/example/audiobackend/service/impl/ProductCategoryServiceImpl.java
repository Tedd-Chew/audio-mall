package com.example.audiobackend.service.impl;

import com.example.audiobackend.entity.ProductCategory;
import com.example.audiobackend.exception.BusinessException;
import com.example.audiobackend.mapper.ProductCategoryMapper;
import com.example.audiobackend.service.ProductCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCategoryServiceImpl implements ProductCategoryService {

    private final ProductCategoryMapper productCategoryMapper;

    @Override
    public List<ProductCategory> list() {
        return productCategoryMapper.selectAll();
    }

    @Override
    public ProductCategory getById(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException("分类 id 不合法");
        }
        ProductCategory category = productCategoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException("分类不存在");
        }
        return category;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long add(ProductCategory category) {
        validateName(category.getName());

        ProductCategory entity = new ProductCategory();
        entity.setName(category.getName());
        entity.setSort(category.getSort() == null ? 0 : category.getSort());

        productCategoryMapper.insert(entity);
        log.info("新增分类成功，id={}，名称={}", entity.getId(), entity.getName());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ProductCategory category) {
        validateName(category.getName());
        if (productCategoryMapper.selectById(id) == null) {
            throw new BusinessException("分类不存在");
        }

        ProductCategory entity = new ProductCategory();
        entity.setId(id);
        entity.setName(category.getName());
        entity.setSort(category.getSort());
        productCategoryMapper.updateById(entity);

        log.info("修改分类成功，id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (productCategoryMapper.deleteById(id) == 0) {
            throw new BusinessException("分类不存在");
        }
        log.info("删除分类成功，id={}", id);
    }

    /**
     * 分类名不能为空、不能带空格。
     * 带空格的老规矩是为了图片目录 —— 原来上传时用分类名当文件夹名。
     */
    private void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException("分类名称不能为空");
        }
        if (name.contains(" ")) {
            throw new BusinessException("分类名称不能包含空格，多个单词请用下划线连接");
        }
    }
}
