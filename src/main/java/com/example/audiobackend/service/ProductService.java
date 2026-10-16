package com.example.audiobackend.service;

import com.example.audiobackend.entity.Product;
import java.util.List;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

// 产品服务接口：定义所有产品相关的业务方法（只定义，不实现）
public interface ProductService {
    // 1. 获取所有产品列表 → 返回List<Product>
    List<Product> list();

    Page<Product> getProductPage(Integer pageNum, Integer pageSize);//实际上会用到sql中的limit语句进行分页。
    // 2. 根据ID查询产品 → 入参Long id，返回Product
    Product getById(Long id);

    // 3. 添加产品 → 入参Product对象，返回boolean（是否成功）
    boolean add(Product product);

    // 4. 修改产品 → 入参Product对象，返回boolean
    boolean update(Product product);

    // 5. 删除产品 → 入参Long id，返回boolean
    boolean delete(Long id);

    // 6. 获取所有产品（含已隐藏）→ 管理后台用
    List<Product> listAll();

    // 7. 切换产品显示/隐藏状态
    boolean toggleDelete(Long id);
}
