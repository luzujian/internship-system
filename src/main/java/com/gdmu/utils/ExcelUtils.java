package com.gdmu.utils;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Excel工具类，用于Excel文件的读取和解析
 */
public class ExcelUtils {

    /**
     * 获取工作簿对象
     * @param file 上传的Excel文件
     * @return 工作簿对象
     */
    public static Workbook getWorkbook(MultipartFile file) throws IOException {
        // 获取文件名
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isEmpty()) {
            throw new IOException("文件名不能为空");
        }
        
        // 检查文件类型
        if (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls")) {
            throw new IOException("不支持的文件类型，请上传.xlsx或.xls格式的Excel文件");
        }
        
        // 使用WorkbookFactory自动检测文件格式并创建适当的Workbook实例
        try (InputStream is = file.getInputStream()) {
            return WorkbookFactory.create(is);
        }
    }



    /**
     * 读取Excel文件内容
     * @param file 上传的Excel文件
     * @return 解析后的List<Map<String, Object>>数据
     */
    public static List<Map<String, Object>> readExcel(MultipartFile file) throws IOException {
        List<Map<String, Object>> resultList = new ArrayList<>();
        
        try (Workbook workbook = getWorkbook(file)) {
            // 获取第一个Sheet
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                return resultList;
            }
            
            // 获取第一行作为表头
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return resultList;
            }
            
            // 获取列数
            int columnCount = headerRow.getPhysicalNumberOfCells();
            
            // 获取总行数
            int rowCount = sheet.getPhysicalNumberOfRows();
            
            // 遍历所有行，从第二行开始读取数据
            for (int rowNum = 1; rowNum < rowCount; rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null) {
                    continue;
                }
                
                Map<String, Object> rowData = new HashMap<>();
                
                // 遍历所有列
                for (int cellNum = 0; cellNum < columnCount; cellNum++) {
                    // 获取表头列名
                    String columnName = getCellValue(headerRow.getCell(cellNum));
                    if (columnName == null || columnName.isEmpty()) {
                        continue;
                    }
                    
                    // 获取单元格值
                    Cell cell = row.getCell(cellNum);
                    String cellValue = getCellValue(cell);
                    
                    rowData.put(columnName, cellValue);
                }
                
                // 如果行数据不为空，则添加到结果列表
                if (!rowData.isEmpty()) {
                    resultList.add(rowData);
                }
            }
        }
        
        return resultList;
    }

    /**
     * 获取单元格的值
     * @param cell 单元格对象
     * @return 单元格的字符串值
     */
    private static String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        
        String cellValue = "";
        
        switch (cell.getCellType()) {
            case STRING:
                // 字符串类型
                cellValue = cell.getStringCellValue();
                break;
            case NUMERIC:
                // 数值类型
                if (DateUtil.isCellDateFormatted(cell)) {
                    // 日期类型
                    Date date = cell.getDateCellValue();
                    cellValue = new SimpleDateFormat("yyyy-MM-dd").format(date);
                } else {
                    // 数字类型，避免科学计数法
                    cellValue = new DecimalFormat("0").format(cell.getNumericCellValue());
                }
                break;
            case BOOLEAN:
                // 布尔类型
                cellValue = String.valueOf(cell.getBooleanCellValue());
                break;
            case FORMULA:
                // 公式类型
                try {
                    cellValue = cell.getStringCellValue();
                } catch (Exception e) {
                    cellValue = String.valueOf(cell.getNumericCellValue());
                }
                break;
            default:
                cellValue = "";
        }
        
        return cellValue;
    }
}