package com.example.audiobackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 分类的新增 / 修改入参。
 */
@Data
public class CategoryDTO {

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称不能超过 50 个字")
    private String name;

    /** 排序，小的在前，不传默认 0 */
    private Integer sort;
}
