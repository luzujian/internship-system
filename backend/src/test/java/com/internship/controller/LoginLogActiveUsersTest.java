package com.internship.controller;

import com.internship.entity.LoginLog;
import com.internship.entity.Result;
import com.internship.mapper.LoginLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class LoginLogActiveUsersTest {
    private final TimeZone zone = TimeZone.getTimeZone("Asia/Shanghai");
    private Date selectedStart, selectedEnd;
    private final List<LoginLog> rows = new ArrayList<>();

    private LoginLogController controller() {
        LoginLogMapper mapper = (LoginLogMapper) Proxy.newProxyInstance(LoginLogMapper.class.getClassLoader(),
                new Class<?>[]{LoginLogMapper.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("selectByDateRange")) throw new AssertionError(method.getName());
                    selectedStart = (Date) args[0]; selectedEnd = (Date) args[1];
                    return rows.stream().filter(row -> row.getLoginTime() == null ||
                            (!row.getLoginTime().before(selectedStart) && !row.getLoginTime().after(selectedEnd))).toList();
                });
        LoginLogController controller = new LoginLogController();
        ReflectionTestUtils.setField(controller, "loginLogMapper", mapper);
        return controller;
    }

    private Date date(String text) throws Exception {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        format.setTimeZone(zone);
        return format.parse(text);
    }

    private void row(String id, String role, String status, String time) throws Exception {
        LoginLog row = new LoginLog(); row.setUserId(id); row.setUserType(role); row.setLoginStatus(status);
        row.setLoginTime(time == null ? null : date(time)); rows.add(row);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> series(Result result, String name) {
        assertEquals(200, result.getCode());
        return (List<Map<String, Object>>) ((Map<String, Object>) result.getData()).get(name);
    }

    @Test
    void companyLoginsAreDeduplicatedPerDayAndSeparatedFromOtherRoles() throws Exception {
        row("1", "ROLE_COMPANY", "SUCCESS", "2026-10-08 00:00:00.000");
        row("1", "COMPANY", "SUCCESS", "2026-10-08 12:00:00.000");
        row("2", "company", "SUCCESS", "2026-10-08 23:59:59.999");
        row("1", "ROLE_COMPANY", "SUCCESS", "2026-10-10 23:59:59.999");
        row("1", "ROLE_STUDENT", "SUCCESS", "2026-10-08 12:00:00.000");
        row("1", "ROLE_TEACHER_COUNSELOR", "SUCCESS", "2026-10-08 12:00:00.000");
        row("1", "ROLE_ADMIN", "SUCCESS", "2026-10-08 12:00:00.000");
        row("3", "ROLE_COMPANY", "FAILURE", "2026-10-08 12:00:00.000");
        row("4", "UNKNOWN", "SUCCESS", "2026-10-08 12:00:00.000");
        row("", "ROLE_COMPANY", "SUCCESS", "2026-10-08 12:00:00.000");
        row("5", "ROLE_COMPANY", "SUCCESS", null);
        Result result = controller().getActiveUsersStats(null, date("2026-10-08 00:00:00.000"), date("2026-10-10 00:00:00.000"));
        assertEquals(List.of(2, 0, 1), series(result, "companyData").stream().map(item -> item.get("count")).toList());
        for (String role : List.of("studentData", "teacherData", "adminData")) {
            assertEquals(List.of(1, 0, 0), series(result, role).stream().map(item -> item.get("count")).toList());
        }
        assertEquals(date("2026-10-08 00:00:00.000"), selectedStart);
        assertEquals(date("2026-10-10 23:59:59.999"), selectedEnd);
    }

    @ParameterizedTest
    @ValueSource(ints = {7, 14, 30})
    void presetPeriodsIncludeTheWholeFirstAndLastDay(int days) throws Exception {
        Calendar first = Calendar.getInstance(zone);
        first.add(Calendar.DAY_OF_MONTH, -(days - 1));
        first.set(Calendar.HOUR_OF_DAY, 0); first.set(Calendar.MINUTE, 0); first.set(Calendar.SECOND, 0); first.set(Calendar.MILLISECOND, 0);
        LoginLog row = new LoginLog(); row.setUserId("company"); row.setUserType("COMPANY"); row.setLoginStatus("SUCCESS"); row.setLoginTime(first.getTime()); rows.add(row);
        List<Map<String, Object>> data = series(controller().getActiveUsersStats(days, null, null), "companyData");
        assertEquals(days, data.size()); assertEquals(1, data.getFirst().get("count"));
        Calendar start = Calendar.getInstance(zone); start.setTime(selectedStart);
        Calendar end = Calendar.getInstance(zone); end.setTime(selectedEnd);
        assertEquals(0, start.get(Calendar.HOUR_OF_DAY)); assertEquals(0, start.get(Calendar.MILLISECOND));
        assertEquals(23, end.get(Calendar.HOUR_OF_DAY)); assertEquals(999, end.get(Calendar.MILLISECOND));
    }

    @Test
    void emptyHistoryReturnsAlignedZeroValuesForAllFourRoles() {
        Result result = controller().getActiveUsersStats(null, null, null);
        for (String role : List.of("studentData", "teacherData", "adminData", "companyData")) {
            List<Map<String, Object>> data = series(result, role);
            assertEquals(7, data.size()); assertTrue(data.stream().allMatch(item -> item.get("count").equals(0)));
        }
    }

    @Test
    void invalidPeriodsAreRejectedBeforeQueryingLogs() throws Exception {
        LoginLogController controller = controller();
        assertEquals(400, controller.getActiveUsersStats(366, null, null).getCode());
        assertEquals(400, controller.getActiveUsersStats(null, date("2026-10-10 00:00:00.000"), date("2026-10-08 00:00:00.000")).getCode());
        assertEquals(400, controller.getActiveUsersStats(null, date("2025-01-01 00:00:00.000"), date("2026-10-08 00:00:00.000")).getCode());
        assertNull(selectedStart); assertNull(selectedEnd);
    }

    @Test
    void activeUsersEndpointRemainsAdministratorOnly() throws Exception {
        assertEquals("hasRole('ADMIN')", LoginLogController.class.getMethod("getActiveUsersStats", Integer.class, Date.class, Date.class)
                .getAnnotation(PreAuthorize.class).value());
    }
}
