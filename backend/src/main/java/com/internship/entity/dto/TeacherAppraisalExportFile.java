package com.internship.entity.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

/** 仅供后端生成压缩包使用，禁止直接作为接口 JSON 返回。 */
@Data
public class TeacherAppraisalExportFile {
    private Long fileId;
    private String className;
    private String studentNumber;
    private String studentName;
    private String fileName;
    @JsonIgnore
    private String filePath;
}
