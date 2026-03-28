package com.gdmu.entity;

import lombok.Data;
import java.util.Date;

/**
 * 职位收藏实体
 */
@Data
public class PositionFavorite {
    private Long id;
    private Long positionId;
    private Long studentId;
    private Date createTime;
}