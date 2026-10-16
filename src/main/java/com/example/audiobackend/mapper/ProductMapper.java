package com.example.audiobackend.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.audiobackend.entity.Product;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    // 无需手写任何 SQL！BaseMapper 已包含：
    // selectList() → 查询所有
    // selectById() → 按 ID 查询
    // insert() → 新增
    // updateById() → 修改
    // deleteById() → 删除
}