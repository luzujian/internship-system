package com.gdmu.entity;

import lombok.Data;

/**
 * 资源分类实体类
 */
@Data
public class ResourceCategory {
    private Long id;
    private String name;
    private Long parentId;
}