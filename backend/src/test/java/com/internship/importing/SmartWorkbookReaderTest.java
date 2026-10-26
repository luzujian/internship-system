package com.internship.importing;

import com.internship.agent.business.AgentException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SmartWorkbookReaderTest {
    @Test void readsEverySheetAndSparseRowPreservingIdsAndMergedValues() throws Exception {
        try (var book = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            var first = book.createSheet("学生名单"); first.createRow(0).createCell(0).setCellValue("2023级名单");
            var header = first.createRow(2); header.createCell(0).setCellValue("学生编号"); header.createCell(2).setCellValue("学生姓名"); header.createCell(3).setCellValue("行政班");
            var a = first.createRow(3); a.createCell(0).setCellValue(12345678901d); a.createCell(2).setCellValue("甲"); a.createCell(3).setCellValue("23级生工7班");
            first.createRow(4).createCell(0).setCellValue("00123"); first.addMergedRegion(new CellRangeAddress(3,4,3,3));
            first.createRow(8).createCell(0).setCellValue("00999");
            book.createSheet("第二专业").createRow(0).createCell(0).setCellValue("学号");
            book.write(bytes);
            var sheets = new SmartWorkbookReader().read(List.of(new MockMultipartFile("files","名单.XLSX","application/octet-stream",bytes.toByteArray())));
            assertEquals(2,sheets.size()); assertEquals("12345678901",sheets.getFirst().rows().get(2).cells().getFirst());
            assertEquals("00123",sheets.getFirst().rows().get(3).cells().getFirst());
            assertEquals("23级生工7班",sheets.getFirst().rows().get(3).cells().get(3)); assertEquals(9,sheets.getFirst().rows().getLast().number());
            var layout = SmartImportRecognition.guess(SmartImportType.STUDENT,sheets.getFirst());
            assertEquals(3,layout.headerRow()); assertEquals(1,layout.columns().get("学号")); assertEquals(3,layout.columns().get("姓名"));
        }
    }
    @Test void damagedAndNonExcelFilesAreRejected() {
        var reader = new SmartWorkbookReader();
        assertThrows(AgentException.class,() -> reader.read(List.of(new MockMultipartFile("files","名单.csv","text/csv","a,b".getBytes()))));
        assertThrows(AgentException.class,() -> reader.read(List.of(new MockMultipartFile("files","名单.xlsx","text/plain","not a workbook".getBytes()))));
    }
    @Test void duplicateHeaderAliasesRequireManualMapping() {
        var sheet = new SmartWorkbookReader.SourceSheet("0:0","名单.xlsx","sheet1",List.of(new SmartWorkbookReader.SourceRow(1,List.of("学号","学生编号","姓名"))));
        assertFalse(SmartImportRecognition.guess(SmartImportType.STUDENT,sheet).columns().containsKey("学号"));
        assertThrows(AgentException.class,() -> SmartImportRecognition.validate(SmartImportType.STUDENT,sheet,new SmartImportRecognition.Layout("0:0",1,Map.of("学号",1,"姓名",1))));
    }
    @Test void formulasAndNumbersBeyondExcelPrecisionAreMarkedForHumanVerification() throws Exception {
        try (var book = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("名单"); var row = sheet.createRow(0);
            row.createCell(0).setCellFormula("1+2"); row.createCell(1).setCellValue(12345678901234567d);
            row.createCell(2).setCellValue("12345678901234567"); book.write(bytes);
            var data = new SmartWorkbookReader().read(List.of(new MockMultipartFile("files","名单.xlsx","application/octet-stream",bytes.toByteArray())));
            assertEquals(Set.of(1,2),data.getFirst().rows().getFirst().uncertainColumns());
        }
    }
}
