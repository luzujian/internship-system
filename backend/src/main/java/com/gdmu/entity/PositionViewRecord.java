package com.gdmu.entity;

import lombok.Data;
import java.util.Date;

/**
 * 职位浏览记录实体
 */
@Data
public class PositionViewRecord {
    private Long id;
    private Long positionId;
    private Long studentId;
    private Date viewTime;
}