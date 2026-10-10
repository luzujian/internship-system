package com.internship.utils;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CompanyImportReaderTest {
    @Test void recognizesRegistrationHeadersAndSplitsAlignedContactsWithoutCreatingPositions() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet(); sheet.createRow(0).createCell(0).setCellValue("招聘会统计表");
            String[] headings = {"公司名称", "公司所在城市", "联系人", "联系电话", "联系邮箱", "实习岗位内容"};
            var header = sheet.createRow(1); for (int c = 0; c < headings.length; c++) header.createCell(c).setCellValue(headings[c]);
            String[] values = {"测试企业", "深圳", "张老师、王老师", "13800000000、13900000000", "one@example.test、two@example.test", "岗位资料"};
            var row = sheet.createRow(2); for (int c = 0; c < values.length; c++) row.createCell(c).setCellValue(values[c]);
            ByteArrayOutputStream output = new ByteArrayOutputStream(); workbook.write(output);
            var rows = CompanyImportReader.read(new MockMultipartFile("file", "companies.xlsx", null, output.toByteArray()));
            assertEquals(2, rows.size()); assertEquals("测试企业", rows.getFirst().get("企业名称"));
            assertEquals("深圳", rows.getFirst().get("所在城市")); assertEquals("王老师", rows.getLast().get("联系人"));
            assertEquals("two@example.test", rows.getLast().get("联系邮箱")); assertEquals(3, rows.getFirst().get("_rowNum"));
        }
    }
    @Test @EnabledIfEnvironmentVariable(named = "COMPANY_REGISTRATION_WORKBOOK", matches = ".+")
    void readsTheProvidedWorkbookWithoutWritingBusinessData() throws Exception {
        Path path = Path.of(System.getenv("COMPANY_REGISTRATION_WORKBOOK"));
        var rows = CompanyImportReader.read(new MockMultipartFile("file", path.getFileName().toString(), null, Files.readAllBytes(path)));
        assertFalse(rows.isEmpty());
        assertTrue(rows.stream().allMatch(row -> row.containsKey("企业名称") && row.containsKey("联系电话")));
        long errors = rows.stream().filter(row -> row.containsKey("_contactError")).count();
        System.out.println("Registration workbook: parsed contact rows=" + rows.size() + ", unmatched contact rows=" + errors);
        assertEquals(0, errors, "工作簿存在联系人无法对应的行");
        var contacts = new com.internship.service.CompanyContactService(
                org.mockito.Mockito.mock(com.internship.mapper.CompanyContactMapper.class),
                org.mockito.Mockito.mock(com.internship.service.PositionCacheService.class));
        for (var row : rows) {
            var contact = new com.internship.entity.CompanyContact();
            contact.setName(java.util.Objects.toString(row.get("联系人"), ""));
            contact.setPhone(java.util.Objects.toString(row.get("联系电话"), ""));
            contact.setEmail(java.util.Objects.toString(row.get("联系邮箱"), ""));
            assertDoesNotThrow(() -> contacts.validateImported(contact), "请核实报名表第" + row.get("_rowNum") + "行联系人");
        }
    }
}
