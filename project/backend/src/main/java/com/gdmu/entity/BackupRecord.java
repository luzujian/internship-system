package com.gdmu.entity;

import lombok.Data;
import java.util.Date;

@Data
public class BackupRecord {
    private Long id;
    private String backupName;
    private String backupPath;
    private Long backupSize;
    private String backupType;
    private Date backupTime;
    private Integer status;
    private String remark;
    private String backupFormat;
    private Boolean isCompressed;
    private String checksum;
    private Long parentBackupId;
    private String tableList;
    private Integer tableCount;
}
