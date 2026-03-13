package com.gdmu.service;

/**
 * 数据导出服务接口
 */
public interface DataExportService {
    
    /**
     * 导出学生实习状态汇总
     */
    byte[] exportStudentStatus(Integer status);
    
    /**
     * 导出各专业实习进度
     */
    byte[] exportMajorProgress();
    
    /**
     * 导出企业招聘情况
     */
    byte[] exportCompanyRecruitment();
    
    /**
     * 导出重点关注学生
     */
    byte[] exportFocusStudents();
    
    /**
     * 导出班级实习统计
     */
    byte[] exportClassStatistics();
    
    /**
     * 导出学生材料归档
     */
    byte[] exportArchives();
}
