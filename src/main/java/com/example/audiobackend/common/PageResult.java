package com.example.audiobackend.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页返回。通用的，不只商品用。
 * 原来的 Page&lt;Product&gt; 是 MyBatis-Plus 的类，会顺着返回值一路暴露到 Controller，
 * 换成这个之后接口层不再依赖任何 ORM 的类型。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /** 总条数 */
    private long total;

    /** 当前页数据 */
    private List<T> list;

    /** 当前页码，从 1 开始 */
    private int pageNum;

    /** 每页条数 */
    private int pageSize;
}
