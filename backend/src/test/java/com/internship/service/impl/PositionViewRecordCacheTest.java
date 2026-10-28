package com.internship.service.impl;

import com.internship.mapper.PositionMapper;
import com.internship.mapper.PositionViewRecordMapper;
import com.internship.service.PositionCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PositionViewRecordCacheTest {
    @Test
    void firstViewInvalidatesHotRankingCacheButRepeatViewsDoNot() {
        PositionViewRecordServiceImpl service = new PositionViewRecordServiceImpl();
        PositionViewRecordMapper records = mock(PositionViewRecordMapper.class);
        PositionMapper positions = mock(PositionMapper.class);
        PositionCacheService cache = mock(PositionCacheService.class);
        ReflectionTestUtils.setField(service, "viewRecordMapper", records);
        ReflectionTestUtils.setField(service, "positionMapper", positions);
        ReflectionTestUtils.setField(service, "positionCacheService", cache);
        when(records.existsByPositionIdAndStudentId(1L, 2L)).thenReturn(0, 1);

        assertTrue(service.recordView(1L, 2L));
        assertFalse(service.recordView(1L, 2L));

        verify(positions, times(1)).incrementViewCount(1L);
        verify(cache, times(1)).clearPositionsCache();
    }
}
