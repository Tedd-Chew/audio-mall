package com.example.audiobackend.service;

import com.example.audiobackend.common.PageResult;
import com.example.audiobackend.dto.ProductDTO;
import com.example.audiobackend.dto.ProductQuery;
import com.example.audiobackend.vo.ProductVO;

/**
 * 商品服务。
 *
 * 跟原来的接口比有两处变化：
 * 1. 返回业务对象（ProductVO / PageResult），不再返回 Page&lt;T&gt; 这种 ORM 的类型
 * 2. 失败不再返回 false，改成抛 BusinessException —— boolean 返回值要靠调用方
 *    自己判断，忘了判断就静默失败
 */
public interface ProductService {

    /** 商品详情，走 Redis 缓存 */
    ProductVO getDetail(Long id);

    /** 商品列表分页 */
    PageResult<ProductVO> page(ProductQuery query);

    /** 新增商品，返回新生成的主键 */
    Long add(ProductDTO dto);

    /** 修改商品 */
    void update(Long id, ProductDTO dto);

    /** 上架 / 下架 */
    void updateStatus(Long id, Integer status);

    /** 删除商品 */
    void delete(Long id);
}
