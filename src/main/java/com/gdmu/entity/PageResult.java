package com.gdmu.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {
    private Long total; // 总记录数
    private List<T> rows; // 当前页数据
    private Integer pages; // 总页数
    private Integer current; // 当前页码
    private Integer pageSize; // 每页记录数

    // 构建分页结果
    public static <T> PageResult<T> build(Long total, List<T> rows) {
        return new PageResult<>(total, rows, null, null, null);
    }

    // 构建完整的分页结果
    public static <T> PageResult<T> build(Long total, List<T> rows, Integer pages, Integer current, Integer pageSize) {
        return new PageResult<>(total, rows, pages, current, pageSize);
    }
}