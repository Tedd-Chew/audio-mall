package com.example.audiobackend.mapper;

import com.example.audiobackend.entity.ProductCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品分类数据访问。
 * 注意数据库表名是 category，不是 product_category。
 */
@Mapper
public interface ProductCategoryMapper {

    ProductCategory selectById(@Param("id") Long id);

    /** 全部分类，按 sort 升序 */
    List<ProductCategory> selectAll();

    int insert(ProductCategory category);

    int updateById(ProductCategory category);

    int deleteById(@Param("id") Long id);
}
