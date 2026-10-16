package com.example.audiobackend.common;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Result<T> {
    private Integer code;// 200成功，500失败
    private String msg;//提示信息：成功/失败的具体描述
    private T data;//实际返回的数据：比如产品列表、产品详情等

    // 手动添加构造器
    public Result(Integer code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    // 静态快捷方法：成功（带数据）→ 比如返回产品列表时用
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "成功", data);
    }


    // 静态快捷方法：成功（无数据）→ 比如删除产品后，只返回“成功”，不用传数据
    public static <T> Result<T> success() {
        return new Result<>(200, "成功", null);
    }
    // 静态快捷方法：失败→ 比如查不存在的产品ID时，返回失败信息
    public static <T> Result<T> error(String msg) {
        return new Result<>(500, msg, null);
    }
    // 新增：传code+msg（支持自定义错误码，比如401）
    public static <T> Result<T> error(int code, String msg) {
        return new Result<>(code, msg, null);
    }

}