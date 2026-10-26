package com.internship.importing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.agent.business.*;
import com.internship.agent.business.AgentTypes.Actor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.internship.importing.SmartImportValidationTest.*;

class SmartImportServiceTest {
    final Actor admin = new Actor("ROLE_ADMIN:2:false:",2L,"ROLE_ADMIN","管理员",null,null,null,false,Set.of("user:student:add"));
    AgentIdentityService identity; AgentActionService actions; SmartImportCatalog catalog; SmartImportExecutor executor;
    SmartImportService service; Map<String,Object> saved; final ObjectMapper json = new ObjectMapper();
    @BeforeEach void setup() {
        identity = mock(AgentIdentityService.class); actions = mock(AgentActionService.class); catalog = mock(SmartImportCatalog.class); executor = mock(SmartImportExecutor.class);
        var tasks = mock(AgentTaskService.class);
        var recognition = new SmartImportRecognition(mock(AgentModel.class),tasks,mock(com.internship.util.AiCallGuard.class),json);
        service = new SmartImportService(new SmartWorkbookReader(),recognition,catalog,executor,actions,identity,json);
        when(identity.resolve("auth")).thenReturn(admin);
        when(catalog.load(eq(SmartImportType.STUDENT),anyList())).thenReturn(students());
        when(actions.preview(any(),eq("SMART_IMPORT"),eq("student"),anyMap(),isNull())).thenAnswer(call -> {
            saved = json.convertValue(call.getArgument(3),new com.fasterxml.jackson.core.type.TypeReference<>(){});
            return action("READY",null);
        });
        when(actions.get(any(),eq("plan"))).thenAnswer(call -> action("READY",null));
        when(actions.execute(any(),eq("plan"),eq("SMART_IMPORT"),anyMap(),any())).thenAnswer(call -> ((Supplier<?>)call.getArgument(4)).get());
        when(executor.execute(any(),eq(SmartImportType.STUDENT),anyList(),any())).thenReturn(new LinkedHashMap<>(Map.of("successCount",1,"failCount",0)));
    }
    AgentActionService.ActionView action(String status,Map<String,Object> receipt) {
        return new AgentActionService.ActionView("plan","SMART_IMPORT","student",saved,null,status,0,Long.MAX_VALUE,receipt,null,null);
    }
    @Test void revokedLivePermissionsCannotBeReplacedByCachedSecurityAuthorities() throws Exception {
        service.preview("auth",List.of(workbook(false)),"{\"type\":\"student\"}");
        var live = mock(com.internship.service.AdminPermissionService.class);
        when(live.getRolePermissionsByCode("ROLE_ADMIN")).thenReturn(List.of()); service.setAdminPermissions(live);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("admin","unused",
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("user:student:add"))));
        try {
            AgentException error = assertThrows(AgentException.class,() -> service.confirm("auth",new SmartImportService.ConfirmRequest("plan",true,false)));
            assertEquals("IMPORT_FORBIDDEN",error.getCode()); verifyNoInteractions(executor);
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }
    MockMultipartFile workbook(boolean problem) throws Exception {
        try (var book = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("名单"); var h = sheet.createRow(1);
            String[] headers = {"学生编号","学生姓名","行政班"}; for (int c = 0; c < headers.length; c++) h.createCell(c).setCellValue(headers[c]);
            var row = sheet.createRow(2); row.createCell(0).setCellValue("0012345"); row.createCell(1).setCellValue("测试学生"); row.createCell(2).setCellValue("23级莞生工07班");
            if (problem) { row = sheet.createRow(3); row.createCell(1).setCellValue("缺学号"); row.createCell(2).setCellValue("23级莞生工07班"); }
            book.write(bytes); return new MockMultipartFile("files","非模板名单.xlsx","application/octet-stream",bytes.toByteArray());
        }
    }
    @Test void previewReadsAndValidatesWithoutCreatingBusinessRecords() throws Exception {
        var result = service.preview("auth",List.of(workbook(false)),"{\"type\":\"student\"}");
        assertEquals(1,((Map<?,?>)result.get("summary")).get("ready")); assertEquals("plan",result.get("actionId"));
        verifyNoInteractions(executor);
        var rows = (List<Map<String,Object>>)saved.get("rows"); assertEquals("0012345",((Map<?,?>)rows.getFirst().get("values")).get("学号"));
    }
    @Test void confirmIsExplicitAndPartialImportRequiresAnExplicitChoice() throws Exception {
        service.preview("auth",List.of(workbook(true)),"{\"type\":\"student\"}");
        assertEquals("CONFIRM_REQUIRED",assertThrows(AgentException.class,() -> service.confirm("auth",new SmartImportService.ConfirmRequest("plan",false,true))).getCode());
        assertEquals("IMPORT_ISSUES_PENDING",assertThrows(AgentException.class,() -> service.confirm("auth",new SmartImportService.ConfirmRequest("plan",true,false))).getCode());
        verifyNoInteractions(executor);
        var receipt = service.confirm("auth",new SmartImportService.ConfirmRequest("plan",true,true)); assertEquals(1L,receipt.get("blockedCount"));
        verify(executor,times(1)).execute(eq(admin),eq(SmartImportType.STUDENT),anyList(),any());
    }
    @Test void dataChangedAfterPreviewBlocksExecution() throws Exception {
        service.preview("auth",List.of(workbook(false)),"{\"type\":\"student\"}");
        var changed = data(students().majors(),List.of(clazz(7,"23级生工7班",39,2024)),Map.of());
        when(catalog.load(eq(SmartImportType.STUDENT),anyList())).thenReturn(changed);
        assertEquals("IMPORT_CHANGED",assertThrows(AgentException.class,() -> service.confirm("auth",new SmartImportService.ConfirmRequest("plan",true,false))).getCode());
        verifyNoInteractions(executor);
    }
    @Test void permissionsAreRecheckedInsideExecution() throws Exception {
        service.preview("auth",List.of(workbook(false)),"{\"type\":\"student\"}");
        var revoked = new Actor(admin.ownerKey(),2L,"ROLE_ADMIN","管理员",null,null,null,false,Set.of());
        when(identity.resolve("auth")).thenReturn(admin,revoked);
        assertEquals("IMPORT_FORBIDDEN",assertThrows(AgentException.class,() -> service.confirm("auth",new SmartImportService.ConfirmRequest("plan",true,false))).getCode());
        verifyNoInteractions(executor);
    }
    @Test void readOnlyAdministratorAndTeachersCannotPreview() throws Exception {
        when(identity.resolve("auth")).thenReturn(new Actor("read-only",2L,"ROLE_ADMIN","管理员",null,null,null,true,Set.of("user:student:add")));
        assertEquals("READ_ONLY_FORBIDDEN",assertThrows(AgentException.class,() -> service.preview("auth",List.of(workbook(false)),"{\"type\":\"student\"}")).getCode());
        when(identity.resolve("auth")).thenReturn(new Actor("teacher",2L,"ROLE_TEACHER_COUNSELOR","教师",null,null,null,false,Set.of("user:student:add")));
        assertEquals("FORBIDDEN",assertThrows(AgentException.class,() -> service.preview("auth",List.of(workbook(false)),"{\"type\":\"student\"}")).getCode()); verifyNoInteractions(actions,executor);
    }
    @Test void receiptsAreReusedWithoutASecondImport() throws Exception {
        service.preview("auth",List.of(workbook(false)),"{\"type\":\"student\"}");
        Map<String,Object> receipt = Map.of("successCount",1,"actionStatus","SUCCEEDED");
        when(actions.get(admin,"plan")).thenReturn(action("SUCCEEDED",receipt));
        assertSame(receipt,service.confirm("auth",new SmartImportService.ConfirmRequest("plan",true,false))); verifyNoInteractions(executor);
    }
}
