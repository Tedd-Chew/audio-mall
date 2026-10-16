package com.example.audiobackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("product_category")
public class ProductCategory {
    @TableId(type = IdType.AUTO)  // 关键！代表数据库自增
    private Long id;
    private String name;
    private Integer isDelete;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}