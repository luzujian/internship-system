package com.internship.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;

class LogControllerSecurityTest {
    @Test
    void legacyListsMaskIpAndRemoveDetails() {
        var mapper = org.mockito.Mockito.mock(com.internship.mapper.OperateLogMapper.class);
        var controller = new LogController();
        org.springframework.test.util.ReflectionTestUtils.setField(controller,"operateLogMapper",mapper);
        var row = new com.internship.entity.OperateLog(); row.setIpAddress("192.168.1.2"); row.setDetailJson("secret");
        org.mockito.Mockito.when(mapper.selectAll(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenReturn(java.util.List.of(row));
        org.junit.jupiter.api.Assertions.assertEquals(200,controller.getRecentLogs().getCode());
        org.junit.jupiter.api.Assertions.assertEquals("192.168.*.*",row.getIpAddress());
        org.junit.jupiter.api.Assertions.assertNull(row.getDetailJson());
        row.setIpAddress("2001:db8::1"); row.setDetailJson("secret");
        org.junit.jupiter.api.Assertions.assertEquals(200,controller.queryOperationLogs(1,10,null,null,null,null,"id","desc").getCode());
        org.junit.jupiter.api.Assertions.assertNull(row.getDetailJson());
        org.junit.jupiter.api.Assertions.assertNotEquals("2001:db8::1",row.getIpAddress());
        com.github.pagehelper.PageHelper.clearPage();
    }
    @Test
    void exportRequiresCategoryPermissionAndMasksXlsxIp() throws Exception {
        var controller=new LogController();
        var export=new com.internship.dto.LogExportRequest(); export.setCategory("login-events");
        var response=new org.springframework.mock.web.MockHttpServletResponse();
        var context=org.springframework.security.core.context.SecurityContextHolder.getContext();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("admin","",java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("log:export"))));
        try {
            controller.exportLogs(export,response);
            org.junit.jupiter.api.Assertions.assertEquals(403,response.getStatus());
            var row=new com.internship.entity.LoginLog(); row.setIpAddress("10.1.2.3");
            response=new org.springframework.mock.web.MockHttpServletResponse();
            org.springframework.test.util.ReflectionTestUtils.invokeMethod(controller,"writeXlsx","login-events",java.util.List.of(row),response);
            try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(response.getContentAsByteArray()))) {
                org.junit.jupiter.api.Assertions.assertEquals("10.1.*.*",book.getSheetAt(0).getRow(1).getCell(6).getStringCellValue());
            }
        } finally {org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    }

    @Test
    void operationLogsCannotBeCreatedFromClientSuppliedPayloads() {
        boolean exposesClientWriteEndpoint = Arrays.stream(LogController.class.getDeclaredMethods())
                .map(Method::getDeclaredAnnotations)
                .flatMap(Arrays::stream)
                .filter(PostMapping.class::isInstance)
                .map(PostMapping.class::cast)
                .flatMap(mapping -> Arrays.stream(mapping.value()))
                .anyMatch("/operation"::equals);

        assertFalse(exposesClientWriteEndpoint,
                "operation audit logs must only be generated from server-side execution context");
    }
}
