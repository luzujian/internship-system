package com.internship.utils;

import com.internship.exception.BusinessException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;

/** Reads the company template or a recruitment registration workbook without executing formulas. */
public final class CompanyImportReader {
    private CompanyImportReader() {}
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("公司名称", "企业名称"), Map.entry("单位名称", "企业名称"),
            Map.entry("公司所在城市", "所在城市"), Map.entry("公司主营业务", "企业简介"));

    public static List<Map<String, Object>> read(MultipartFile file) throws IOException {
        try (Workbook workbook = ExcelUtils.getWorkbook(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() > 5000) throw new BusinessException("单次最多导入5000行企业数据");
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            formatter.setUseCachedValuesForFormulaCells(true);
            int headerIndex = -1;
            Map<Integer, String> headers = new LinkedHashMap<>();
            for (int r = 0; r <= Math.min(20, sheet.getLastRowNum()); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Map<Integer, String> candidate = new LinkedHashMap<>();
                for (Cell cell : row) {
                    String label = formatter.formatCellValue(cell).trim();
                    candidate.put(cell.getColumnIndex(), ALIASES.getOrDefault(label, label));
                }
                if (candidate.containsValue("企业名称") && candidate.containsValue("联系人")
                        && candidate.containsValue("联系电话")) {
                    headerIndex = r;
                    headers = candidate;
                    break;
                }
            }
            if (headerIndex < 0) throw new BusinessException("未找到企业名称、联系人和联系电话表头");
            List<Map<String, Object>> result = new ArrayList<>();
            for (int r = headerIndex + 1; r <= sheet.getLastRowNum(); r++) {
                Map<String, Object> data = new LinkedHashMap<>();
                for (Map.Entry<Integer, String> header : headers.entrySet()) {
                    if (header.getValue().isEmpty()) continue;
                    data.put(header.getValue(), value(sheet, r, header.getKey(), formatter));
                }
                if (Objects.toString(data.get("企业名称"), "").isBlank()) continue;
                data.put("_rowNum", r + 1);
                List<String> names = parts(data.get("联系人"));
                List<String> phones = parts(data.get("联系电话"));
                List<String> emails = parts(data.get("联系邮箱"));
                if (names.size() != phones.size() || emails.size() > names.size()) {
                    data.put("_contactError", "联系人与电话数量不一致，请按相同顺序填写；邮箱可留空");
                    result.add(data);
                    continue;
                }
                for (int i = 0; i < Math.max(1, names.size()); i++) {
                    Map<String, Object> contactRow = new LinkedHashMap<>(data);
                    contactRow.put("联系人", i < names.size() ? names.get(i) : "");
                    contactRow.put("联系电话", i < phones.size() ? phones.get(i) : "");
                    contactRow.put("联系邮箱", i < emails.size() ? emails.get(i) : "");
                    result.add(contactRow);
                }
            }
            return result;
        }
    }

    private static List<String> parts(Object value) {
        String text = Objects.toString(value, "").trim();
        return text.isEmpty() ? List.of() : Arrays.stream(text.split("[\\r\\n、,，;；/]+", -1)).map(String::trim).toList();
    }
    private static String value(Sheet sheet, int row, int column, DataFormatter formatter) {
        Row source = sheet.getRow(row);
        Cell cell = source == null ? null : source.getCell(column);
        if (cell == null || formatter.formatCellValue(cell).isBlank()) {
            for (CellRangeAddress merged : sheet.getMergedRegions()) {
                if (merged.isInRange(row, column)) {
                    source = sheet.getRow(merged.getFirstRow());
                    cell = source == null ? null : source.getCell(merged.getFirstColumn());
                    break;
                }
            }
        }
        return formatter.formatCellValue(cell).trim();
    }
}
