package com.gdmu.entity;

import com.gdmu.enums.AuditDecision;
import com.gdmu.enums.AuditType;
import com.gdmu.enums.RiskLevel;
import com.gdmu.enums.TargetType;
import lombok.Data;

import java.util.Date;

@Data
public class AiAuditRecord {
    private Long id;
    private String auditType;
    private Long targetId;
    private String targetType;
    private String recallReason;
    private String auditDecision;
    private String auditReason;
    private String riskLevel;
    private String aiRemark;
    private String modelUsed;
    private Date auditTime;
    private Integer auditDuration;
    private Integer status;
    private String errorMessage;
    private Date createTime;

    public AuditType getAuditTypeEnum() {
        return AuditType.fromCode(auditType);
    }

    public void setAuditTypeEnum(AuditType auditTypeEnum) {
        this.auditType = auditTypeEnum.getCode();
    }

    public TargetType getTargetTypeEnum() {
        return TargetType.fromCode(targetType);
    }

    public void setTargetTypeEnum(TargetType targetTypeEnum) {
        this.targetType = targetTypeEnum.getCode();
    }

    public AuditDecision getAuditDecisionEnum() {
        return AuditDecision.fromCode(auditDecision);
    }

    public void setAuditDecisionEnum(AuditDecision auditDecisionEnum) {
        this.auditDecision = auditDecisionEnum.getCode();
    }

    public RiskLevel getRiskLevelEnum() {
        if (riskLevel == null) {
            return null;
        }
        try {
            return RiskLevel.fromCode(Integer.parseInt(riskLevel));
        } catch (NumberFormatException e) {
            return RiskLevel.fromName(riskLevel);
        }
    }

    public void setRiskLevelEnum(RiskLevel riskLevelEnum) {
        if (riskLevelEnum != null) {
            this.riskLevel = riskLevelEnum.name();
        }
    }
}
