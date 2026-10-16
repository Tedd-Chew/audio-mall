package com.example.audiobackend.entity;
// Lombok注解：自动生成getter/setter/无参构造/全参构造（不用手写几百行重复代码）
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableField;


@Data  // @Data：替代所有getter/setter/toString等方法，企业必用
@NoArgsConstructor// 无参构造器，Lombok会自动生成
@AllArgsConstructor// 全参构造器，Lombok会自动生成
@TableName("product")
public class Product {
    @TableId(type = IdType.AUTO)  // 关键！代表数据库自增
    private Long id;
    private String name;
    private Double price;
    private String description;//这个是简介，放在介绍页
    private String imageUrl;// 注意：Java 用驼峰，MyBatis-Plus 会自动映射到数据库的 image_url
    private Integer viewCount; // 新增一个字段，记录浏览量

    // 新增字段
    private Integer categoryId;
    @TableField(exist = false)
    private String categoryName;//这个字段不在数据库中，完全通过categoryId查询赋值。
    private String imageList;
    private String tags;
    private String detail;
    private Integer isDelete;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}