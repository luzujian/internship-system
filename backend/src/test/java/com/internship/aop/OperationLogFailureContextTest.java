package com.internship.aop;

import com.internship.anno.Log;
import com.internship.entity.OperateLog;
import com.internship.entity.Result;
import com.internship.service.AuditLogService;
import com.internship.utils.CurrentHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class OperationLogFailureContextTest {
    public static class Fixture {
        @Log(module="TEST", operationType="ADD", description="审计诊断测试")
        public Result fail() { return Result.error("请上传相关材料"); }
    }
    @Test void aspectPassesFailureContextToAuditBeforePersistence() throws Throwable {
        CurrentHolder.remove();
        var aspect = new OperationLogAspect(); var audit = mock(AuditLogService.class);
        ReflectionTestUtils.setField(aspect, "auditLogService", audit);
        var join = mock(ProceedingJoinPoint.class); var signature = mock(MethodSignature.class);
        when(join.getSignature()).thenReturn(signature); when(join.getArgs()).thenReturn(new Object[0]);
        when(signature.getMethod()).thenReturn(Fixture.class.getMethod("fail"));
        var result = new Fixture().fail(); when(join.proceed()).thenReturn(result);
        assertSame(result, aspect.logOperation(join));
        var capture = ArgumentCaptor.forClass(OperateLog.class); verify(audit).record(capture.capture());
        assertEquals("FAILURE", capture.getValue().getOperationResult());
        assertEquals("400", capture.getValue().getErrorCode());
        assertTrue(capture.getValue().getDetailJson().contains("请上传相关材料"));
    }
}
