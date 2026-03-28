package com.gdmu.service.impl;

import com.gdmu.mapper.*;
import com.gdmu.service.DataExportService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

/**
 * 数据导出服务实现类
 */
@Slf4j
@Service
public class DataExportServiceImpl implements DataExportService {

    @Autowired
    private StudentInternshipStatusMapper internshipStatusMapper;
    
    @Autowired
    private PositionMapper positionMapper;
    
    @Autowired
    private StudentArchiveMapper studentArchiveMapper;

    @Override
    public byte[] exportStudentStatus(Integer status) {
        List<Map<String, Object>> data = internshipStatusMapper.findAllWithDetails(status);
        
        String[] headers = {"学号", "姓名", "专业", "班级", "实习状态", "实习企业", "实习岗位", "更新时间"};
        String[] fields = {"studentUserId", "studentName", "majorName", "className", "statusText", "companyName", "positionName", "updatedTime"};
        
        return createExcel("学生实习状态汇总", headers, fields, data);
    }

    @Override
    public byte[] exportMajorProgress() {
        List<Map<String, Object>> data = internshipStatusMapper.getMajorProgressForExport();
        
        String[] headers = {"专业名称", "学生总数", "已确定", "有Offer", "未找到", "实习结束", "完成率(%)"};
        String[] fields = {"majorName", "totalStudents", "confirmedCount", "hasOfferCount", "notFoundCount", "completedCount", "progressRate"};
        
        return createExcel("各专业实习进度", headers, fields, data);
    }

    @Override
    public byte[] exportCompanyRecruitment() {
        List<Map<String, Object>> data = positionMapper.findPageWithCompany(null, null);
        
        String[] headers = {"企业名称", "岗位名称", "岗位要求", "计划招聘", "已招人数", "招聘缺口"};
        String[] fields = {"companyName", "positionName", "requirements", "plannedRecruit", "recruitedCount", "remainingQuota"};
        
        return createExcel("企业招聘情况", headers, fields, data);
    }

    @Override
    public byte[] exportFocusStudents() {
        List<Map<String, Object>> data = internshipStatusMapper.findAllWithDetails(0); // 状态0=未找到实习
        
        String[] headers = {"学号", "姓名", "专业", "班级", "联系电话", "更新时间"};
        String[] fields = {"studentUserId", "studentName", "majorName", "className", "phone", "updatedTime"};
        
        return createExcel("重点关注学生名单", headers, fields, data);
    }

    @Override
    public byte[] exportClassStatistics() {
        List<Map<String, Object>> data = internshipStatusMapper.getClassStatisticsForExport();
        
        String[] headers = {"班级名称", "专业", "学生总数", "已确定", "有Offer", "未找到", "完成率(%)"};
        String[] fields = {"className", "majorName", "totalStudents", "confirmedCount", "hasOfferCount", "notFoundCount", "progressRate"};
        
        return createExcel("班级实习统计", headers, fields, data);
    }

    @Override
    public byte[] exportArchives() {
        List<Map<String, Object>> data = studentArchiveMapper.findPageWithStudent(null, null, null);
        
        String[] headers = {"学号", "姓名", "专业", "材料类型", "文件名", "上传时间", "审核状态"};
        String[] fields = {"studentUserId", "studentName", "majorName", "fileType", "fileName", "uploadTime", "statusText"};
        
        // 添加状态文本
        for (Map<String, Object> row : data) {
            Integer status = (Integer) row.get("status");
            String statusText = status == null ? "未知" : (status == 0 ? "待审核" : (status == 1 ? "已通过" : "已拒绝"));
            row.put("statusText", statusText);
        }
        
        return createExcel("学生材料归档", headers, fields, data);
    }

    /**
     * 创建Excel文件
     */
    private byte[] createExcel(String sheetName, String[] headers, String[] fields, List<Map<String, Object>> data) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            
            // 创建标题行样式
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREEN.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            
            // 创建标题行
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 4000);
            }
            
            // 创建数据行
            int rowNum = 1;
            for (Map<String, Object> rowData : data) {
                Row row = sheet.createRow(rowNum++);
                for (int i = 0; i < fields.length; i++) {
                    Cell cell = row.createCell(i);
                    Object value = rowData.get(fields[i]);
                    if (value != null) {
                        cell.setCellValue(value.toString());
                    }
                }
            }
            
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("创建Excel失败: {}", e.getMessage());
            throw new RuntimeException("创建Excel失败", e);
        }
    }
}
