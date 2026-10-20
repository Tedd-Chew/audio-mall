package com.example.audiobackend.dto;

import lombok.Data;

/**
 * 商品列表查询条件。
 * 用对象传而不是一长串 @Param —— 以后加条件不用改 Mapper 接口签名。
 */
@Data
public class ProductQuery {

    /** 分类 id，null 表示不限 */
    private Long categoryId;

    /** 商品状态：1 上架 / 0 下架，null 表示不限（后台列表要用） */
    private Integer status;

    /** 页码，从 1 开始 */
    private Integer pageNum = 1;

    /** 每页条数 */
    private Integer pageSize = 10;

    /** SQL 的 LIMIT 偏移量 */
    public int getOffset() {
        return (safePageNum() - 1) * safePageSize();
    }

    /** SQL 的 LIMIT 条数 */
    public int getLimit() {
        return safePageSize();
    }

    private int safePageNum() {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    /** 上限 100：不设的话前端传 pageSize=100000 就能把整张表拉出来 */
    private int safePageSize() {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 100);
    }
}
