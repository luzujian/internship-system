package com.internship.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.entity.dto.PositionVO;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PositionCalendarDecouplingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void companyPayloadCannotSetOrReadPositionInternshipDates() throws Exception {
        Position position = objectMapper.readValue("""
                {
                  "positionName": "测试岗位",
                  "internshipStartDate": "2026-03-01",
                  "internshipEndDate": "2026-09-17"
                }
                """, Position.class);

        assertNull(position.getInternshipStartDate());
        assertNull(position.getInternshipEndDate());

        position.setInternshipStartDate(new Date());
        position.setInternshipEndDate(new Date());
        String json = objectMapper.writeValueAsString(position);
        assertFalse(json.contains("internshipStartDate"));
        assertFalse(json.contains("internshipEndDate"));
    }

    @Test
    void publicPositionDtoDoesNotExposeLegacyPositionInternshipDates() throws Exception {
        assertThrows(NoSuchFieldException.class,
                () -> PositionVO.class.getDeclaredField("internshipStartDate"));
        PositionVO position = new PositionVO();
        position.setInternshipEndDate(new Date());
        String json = objectMapper.writeValueAsString(position);
        assertFalse(json.contains("internshipEndDate"));
        PositionVO incoming = objectMapper.readValue("{\"internshipEndDate\":\"2026-09-17\"}", PositionVO.class);
        assertNull(incoming.getInternshipEndDate());
    }
}
