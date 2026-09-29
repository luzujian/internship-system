package com.internship.controller;

import com.internship.entity.Position;
import com.internship.entity.User;
import com.internship.mapper.PositionMapper;
import com.internship.service.PositionCacheService;
import com.internship.service.StudentInternshipStatusService;
import com.internship.service.impl.PositionServiceImpl;
import com.internship.utils.CurrentHolder;
import com.internship.websocket.AnnouncementWebSocketHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PositionMutationBoundaryTest {
    private final PositionMapper mapper = mock(PositionMapper.class);
    private final PositionController controller = new PositionController();
    PositionMutationBoundaryTest() {
        ReflectionTestUtils.setField(controller, "positionService", new PositionServiceImpl(mapper, mock(StudentInternshipStatusService.class)));
        ReflectionTestUtils.setField(controller, "positionCacheService", mock(PositionCacheService.class));
        ReflectionTestUtils.setField(controller, "webSocketHandler", mock(AnnouncementWebSocketHandler.class));
        Position own = new Position(); own.setId(10L); own.setCompanyId(20L);
        when(mapper.findById(10L)).thenReturn(own);
        when(mapper.findByIdForUpdate(10L)).thenReturn(own);
    }
    @AfterEach void clear() { CurrentHolder.remove(); SecurityContextHolder.clearContext(); }
    private void login(String role) {
        User actor = new User(); actor.setId(20L); actor.setRole(role); CurrentHolder.setUser(actor);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "actor", "unused", List.of(new SimpleGrantedAuthority(role), new SimpleGrantedAuthority("recruitment:delete"))));
    }
    @Test void companyUpdateCannotTransferOwnershipThroughSharedRoute() {
        login("ROLE_COMPANY");
        Position body = new Position(); body.setCompanyId(999L); body.setPositionName("合法岗位");
        assertEquals(200, controller.updatePosition(10L, body).getCode());
        assertNull(body.getCompanyId()); verify(mapper).update(body);
    }
    @Test void mixedBatchRejectsBeforeWrite() {
        login("ROLE_COMPANY");
        Position foreign = new Position(); foreign.setCompanyId(99L);
        when(mapper.findByIdForUpdate(11L)).thenReturn(foreign);
        assertEquals(400, controller.batchDeletePositions(List.of(10L, 11L)).getCode());
        verify(mapper, never()).batchDeleteByIds(any());
    }
    @Test void roleCollisionDoesNotGrantCompanyMutation() {
        login("ROLE_TEACHER_COUNSELOR");
        assertEquals(400, controller.batchDeletePositions(List.of(10L)).getCode());
        assertEquals(400, controller.updatePosition(10L, new Position()).getCode());
        assertEquals(400, controller.deletePosition(10L).getCode());
        verify(mapper, never()).batchDeleteByIds(any()); verify(mapper, never()).update(any());
        verify(mapper, never()).deleteById(any());
    }
    @Test void administratorAndCompanyKeepTheirLegitimateBatchAccess() {
        login("ROLE_ADMIN");
        assertEquals(200, controller.batchDeletePositions(List.of(10L)).getCode());
        login("ROLE_COMPANY");
        assertEquals(200, controller.batchDeletePositions(List.of(10L)).getCode());
        verify(mapper, times(2)).batchDeleteByIds(List.of(10L));
    }
}
