package com.gdmu.mapper;

import com.gdmu.entity.dto.ApprovalDetailDTO;
import com.gdmu.entity.dto.CompanyDetailDTO;
import com.gdmu.entity.dto.CompanyTrendDTO;
import com.gdmu.entity.dto.CoreMetricsDTO;
import com.gdmu.entity.dto.StudentInternshipDetailDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 统计报表Mapper接口
 */
@Mapper
public interface ReportMapper {
    
    /**
     * 获取核心指标数据
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 核心指标DTO
     */
    CoreMetricsDTO getCoreMetrics(@Param("startDate") Date startDate, @Param("endDate") Date endDate);
    
    /**
     * 获取企业入驻趋势数据（按季度）
     * @param year 年份
     * @return 企业入驻趋势列表
     */
    List<CompanyTrendDTO> getCompanyTrendByQuarter(@Param("year") Integer year);
    
    /**
     * 获取企业入驻详情列表
     * @param offset 偏移量
     * @param pageSize 每页大小
     * @return 企业详情列表
     */
    List<CompanyDetailDTO> getCompanyDetails(@Param("offset") Integer offset, @Param("pageSize") Integer pageSize);
    
    /**
     * 获取企业入驻详情总数
     * @return 总数
     */
    Long getCompanyDetailsCount();
    
    /**
     * 获取学生实习详情列表
     * @param offset 偏移量
     * @param pageSize 每页大小
     * @return 学生实习详情列表
     */
    List<StudentInternshipDetailDTO> getStudentInternshipDetails(@Param("offset") Integer offset, @Param("pageSize") Integer pageSize);
    
    /**
     * 获取学生实习详情总数
     * @return 总数
     */
    Long getStudentInternshipDetailsCount();
    
    /**
     * 获取申请审核详情列表
     * @param offset 偏移量
     * @param pageSize 每页大小
     * @return 申请审核详情列表
     */
    List<ApprovalDetailDTO> getApprovalDetails(@Param("offset") Integer offset, @Param("pageSize") Integer pageSize);
    
    /**
     * 获取申请审核详情总数
     * @return 总数
     */
    Long getApprovalDetailsCount();
    
    /**
     * 获取资源下载量
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 下载量
     */
    Integer getResourceDownloads(@Param("startDate") Date startDate, @Param("endDate") Date endDate);
}
