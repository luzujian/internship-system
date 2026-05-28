package com.gdmu.entity.dto;

import lombok.Data;

import java.util.List;

@Data
public class TeacherDashboardStatsDTO {
    private Integer totalStudents;
    private Integer confirmed;
    private Integer offer;
    private Integer noOffer;
    private Integer delay;
    private String scopeName;
    private List<GradeStatsDTO> gradeData;
    private List<MajorStatsDTO> majorData;
    private List<ClassStatsDTO> classData;
    private List<DivisionStatsDTO> divisionData;

    @Data
    public static class GradeStatsDTO {
        private String gradeName;
        private Integer total;
        private Integer confirmed;
        private Integer offer;
        private Integer noOffer;
        private Integer delay;
    }

    @Data
    public static class MajorStatsDTO {
        private String majorName;
        private Integer total;
        private Integer confirmed;
        private Integer offer;
        private Integer noOffer;
        private Integer delay;
    }

    @Data
    public static class ClassStatsDTO {
        private String className;
        private Integer total;
        private Integer confirmed;
        private Integer offer;
        private Integer noOffer;
        private Integer delay;
    }

    @Data
    public static class DivisionStatsDTO {
        private String divisionName;
        private Integer total;
        private Integer confirmed;
        private Integer offer;
        private Integer noOffer;
        private Integer delay;
    }
}
