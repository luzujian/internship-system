package com.internship.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CompanyVisitRecordRequest {
    @NotNull(message = "企业不能为空")
    private Long companyId;

    @NotNull(message = "走访日期不能为空")
    private LocalDate visitDate;

    @JsonAlias({"teacherIds", "participantIds"})
    @NotEmpty(message = "走访教师不能为空")
    private List<Long> participantTeacherIds;

    @Size(max = 2000, message = "走访备注长度不能超过2000个字符")
    private String remark;
}
