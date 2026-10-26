package com.internship.importing;

import com.internship.agent.business.AgentException;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@Component
public class SmartWorkbookReader {
    public static final int MAX_ROWS = 5000, MAX_COLUMNS = 80, MAX_SHEETS = 20;
    public record SourceRow(int number, List<String> cells, Set<Integer> uncertainColumns) {
        public SourceRow(int number,List<String> cells) { this(number,cells,Set.of()); }
    }
    public record SourceSheet(String id, String fileName, String name, List<SourceRow> rows) {}

    public List<SourceSheet> read(List<MultipartFile> files) {
        if (files == null || files.isEmpty() || files.size() > 5)
            throw invalid("请上传1至5个 Excel 文件。");
        long bytes = files.stream().mapToLong(MultipartFile::getSize).sum();
        if (bytes > 10 * 1024 * 1024) throw invalid("本次文件总大小不能超过10MB。");
        List<SourceSheet> out = new ArrayList<>(); int totalRows = 0;
        for (int f = 0; f < files.size(); f++) {
            MultipartFile file = files.get(f);
            String name = Objects.toString(file.getOriginalFilename(), "");
            if (file.isEmpty() || !name.toLowerCase(Locale.ROOT).matches(".*\\.xlsx?"))
                throw invalid("请上传非空的 .xlsx 或 .xls 文件。");
            try (var input = file.getInputStream(); Workbook book = WorkbookFactory.create(input)) {
                if (book.getNumberOfSheets() + out.size() > MAX_SHEETS) throw invalid("一次最多处理20个工作表。");
                DataFormatter formatter = new DataFormatter(Locale.CHINA);
                formatter.setUseCachedValuesForFormulaCells(true);
                for (int s = 0; s < book.getNumberOfSheets(); s++) {
                    Sheet sheet = book.getSheetAt(s);
                    if (sheet.getLastRowNum() > MAX_ROWS + 50) throw invalid("工作表行数过多，请拆分后导入（最多5000条）。");
                    if (sheet.getNumMergedRegions() > 2000) throw invalid("合并单元格过多，请拆分后导入。");
                    List<SourceRow> rows = new ArrayList<>();
                    int columns = 0;
                    for (Row row : sheet) columns = Math.max(columns, row.getLastCellNum());
                    if (columns > MAX_COLUMNS) throw invalid("工作表最多支持80列，请删除无关列后重试。");
                    for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                        Row row = sheet.getRow(r); List<String> cells = new ArrayList<>(); boolean any = false; Set<Integer> uncertain = new HashSet<>();
                        for (int c = 0; c < columns; c++) {
                            Cell cell = row == null ? null : row.getCell(c);
                            if (cell == null || cell.getCellType() == CellType.BLANK) {
                                for (var range : sheet.getMergedRegions()) {
                                    if (range.isInRange(r, c)) {
                                        Row first = sheet.getRow(range.getFirstRow());
                                        cell = first == null ? null : first.getCell(range.getFirstColumn()); break;
                                    }
                                }
                            }
                            String value = cell == null ? "" : formatter.formatCellValue(cell).trim();
                            if (cell != null && (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR
                                    || (cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell) && Math.abs(cell.getNumericCellValue()) >= 1e15))) uncertain.add(c + 1);
                            // Never evaluate formulas or follow links from uploaded documents.
                            if (cell != null && cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)
                                    && value.matches("[\\d.]+[Ee][+-]?\\d+"))
                                value = java.math.BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
                            if (value.length() > 16000) throw invalid("单元格内容过长，请缩短后重试。");
                            cells.add(value); any |= !value.isBlank();
                        }
                        if (any) rows.add(new SourceRow(r + 1, List.copyOf(cells),Set.copyOf(uncertain)));
                    }
                    totalRows += rows.size();
                    if (totalRows > MAX_ROWS + MAX_SHEETS * 20) throw invalid("本次数据过多，请拆分后导入（最多5000条）。");
                    if (!rows.isEmpty()) out.add(new SourceSheet(f + ":" + s, name, sheet.getSheetName(), rows));
                }
            } catch (AgentException e) { throw e; }
            catch (Exception e) { throw invalid("无法读取“" + name + "”，请确认文件未加密且为有效 Excel。"); }
        }
        if (out.isEmpty()) throw invalid("Excel 中没有可读取的数据。");
        return out;
    }
    static AgentException invalid(String message) { return new AgentException("IMPORT_INVALID", message, 400); }
}
