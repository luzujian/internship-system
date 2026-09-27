package com.internship.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TeacherAppraisalFileContent {
    private String fileName;
    private String fileType;
    private byte[] content;
}
