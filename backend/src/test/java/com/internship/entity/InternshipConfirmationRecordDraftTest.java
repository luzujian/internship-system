package com.internship.entity;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternshipConfirmationRecordDraftTest {
    @Test
    void newRecordStartsAsSavedButNotSubmitted() {
        assertNull(new InternshipConfirmationRecord().getRecallStatus());
    }

    @Test
    void firstSubmitAtomicallyMovesDraftFromNullToZero() throws Exception {
        String mapperXml = Files.readString(Path.of(
                "src/main/resources/com/internship/mapper/InternshipConfirmationRecordMapper.xml"));
        assertTrue(mapperXml.contains("recall_status = 0"));
        assertTrue(mapperXml.contains("recall_status IS NULL"));
        assertTrue(mapperXml.contains("AND status = 0"));
    }
}
