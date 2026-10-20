package com.example.audiobackend.mapper;

import com.example.audiobackend.dto.ProductQuery;
import com.example.audiobackend.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品数据访问。
 * 接口只声明方法，SQL 全部写在 resources/mapper/ProductMapper.xml 里（靠方法名对应）。
 */
@Mapper
public interface ProductMapper {

    /** 按 id 查商品详情 */
    Product selectById(@Param("id") Long id);

    /** 按条件分页查询，条件来自 ProductQuery */
    List<Product> selectPage(ProductQuery query);

    /** 按条件统计总数，分页要用 */
    long countByCondition(ProductQuery query);

    /** 新增商品 */
    int insert(Product product);

    /** 按 id 修改，只更新传了值的字段 */
    int updateById(Product product);

    /** 上架 / 下架 */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /** 按 id 删除 */
    int deleteById(@Param("id") Long id);

    /** 回写浏览量（定时任务把 Redis 里的计数同步回数据库） */
    int updateViewCount(@Param("id") Long id, @Param("viewCount") Integer viewCount);
}
