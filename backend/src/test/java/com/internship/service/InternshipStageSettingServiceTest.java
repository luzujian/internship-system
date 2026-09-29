package com.internship.service;

import com.internship.entity.InternshipStageSetting;
import com.internship.exception.BusinessException;
import com.internship.mapper.InternshipStageSettingMapper;
import com.internship.service.impl.InternshipStageSettingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternshipStageSettingServiceTest {
    @Mock private InternshipStageSettingMapper mapper;
    private InternshipStageSettingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InternshipStageSettingServiceImpl(mapper);
    }

    @Test
    void savesOnlyTheRequiredTwoStageTimeline() {
        InternshipStageSetting first = stage(1, "2026-03-01", "2026-03-31", "2026-04-01", "2026-06-30");
        InternshipStageSetting second = stage(2, "2026-06-01", "2026-06-30", "2026-07-01", "2026-12-31");
        first.setChangeDeadline("2026-03-20");
        first.setConfirmationDeadline("2026-03-31");
        second.setAppraisalDeadline("2027-01-15");
        when(mapper.findByStageNo(1)).thenReturn(null);
        when(mapper.findByStageNo(2)).thenReturn(null);

        service.saveAll(List.of(first, second), 9L, "teacher", "COUNSELOR");

        ArgumentCaptor<InternshipStageSetting> captor = ArgumentCaptor.forClass(InternshipStageSetting.class);
        verify(mapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        for (InternshipStageSetting saved : captor.getAllValues()) {
            assertEquals(1, saved.getEnabled());
            assertNull(saved.getChangeDeadline());
            assertNull(saved.getConfirmationDeadline());
            assertNull(saved.getReportDeadline());
            assertNull(saved.getAppraisalDeadline());
        }
    }

    @Test
    void selectionMustFinishNoLaterThanInternshipStart() {
        InternshipStageSetting first = stage(1, "2026-03-01", "2026-04-02", "2026-04-01", "2026-06-30");
        InternshipStageSetting second = stage(2, "2026-06-01", "2026-06-30", "2026-07-01", "2026-12-31");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.saveAll(List.of(first, second), 9L, "teacher", "COUNSELOR"));

        assertEquals("第一阶段选择截止时间不能晚于实习开始时间", error.getMessage());
        verify(mapper, never()).insert(any(InternshipStageSetting.class));
    }

    @Test
    void twoInternshipStagesCannotOverlapOrShareTheSameDate() {
        InternshipStageSetting first = stage(1, "2026-03-01", "2026-03-31", "2026-04-01", "2026-07-01");
        InternshipStageSetting second = stage(2, "2026-06-01", "2026-06-30", "2026-07-01", "2026-12-31");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.saveAll(List.of(first, second), 9L, "teacher", "COUNSELOR"));

        assertEquals("第二阶段实习开始时间必须晚于第一阶段实习结束时间", error.getMessage());
    }

    private InternshipStageSetting stage(int stageNo, String selectionStart, String selectionEnd,
                                         String internshipStart, String internshipEnd) {
        InternshipStageSetting setting = new InternshipStageSetting();
        setting.setStageNo(stageNo);
        setting.setStageName("第" + stageNo + "阶段实习");
        setting.setSelectionStartDate(selectionStart);
        setting.setSelectionEndDate(selectionEnd);
        setting.setInternshipStartDate(internshipStart);
        setting.setInternshipEndDate(internshipEnd);
        setting.setReportCycle(7);
        setting.setEnabled(1);
        setting.setVersion(0);
        return setting;
    }
}
