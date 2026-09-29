package com.internship.service;

import com.internship.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TeacherAutoTaskTargetsTest {
    @Test void supportsLegacyScopesAndRoundTripsExplicitSelection() {
        assertEquals(List.of(), TeacherAutoTaskTargets.decode("OWN_CLASSES"));
        assertEquals("OWN_DIVISION", TeacherAutoTaskTargets.encode("OWN_DIVISION", List.of()));
        assertEquals(List.of(3L, 9L), TeacherAutoTaskTargets.decode(
                TeacherAutoTaskTargets.encode("OWN_COLLEGE", List.of(9L, 3L, 9L))));
    }
    @Test void brokenSelectionCannotBecomeAllStudents() {
        for (String value : List.of("broken", "{}", "{\"scope\":\"OWN_CLASSES\",\"studentIds\":[]}",
                "{\"scope\":\"ALL\",\"studentIds\":[1]}", "{\"scope\":\"OWN_CLASSES\",\"studentIds\":[-1]}")) {
            assertThrows(BusinessException.class, () -> TeacherAutoTaskTargets.decode(value));
        }
        assertThrows(BusinessException.class, () -> TeacherAutoTaskTargets.ids(List.of(1.5)));
        assertThrows(BusinessException.class, () -> TeacherAutoTaskTargets.ids("1,2"));
    }
}
