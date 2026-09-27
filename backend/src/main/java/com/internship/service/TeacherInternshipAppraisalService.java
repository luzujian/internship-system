package com.internship.service;

import com.internship.entity.TeacherUser;
import com.internship.entity.dto.TeacherAppraisalDetail;
import com.internship.entity.dto.TeacherAppraisalExportFile;
import com.internship.entity.dto.TeacherAppraisalFileContent;
import com.internship.entity.dto.TeacherAppraisalListResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public interface TeacherInternshipAppraisalService {
    TeacherAppraisalListResponse list(TeacherUser teacher, int page, int pageSize,
                                        String keyword, Long classId, String status);

    TeacherAppraisalDetail findSubmittedDetail(TeacherUser teacher, Long studentId);

    TeacherAppraisalFileContent loadFile(TeacherUser teacher, Long fileId, boolean preview) throws Exception;

    List<TeacherAppraisalExportFile> prepareExport(TeacherUser teacher, String keyword,
                                                     Long classId, String status);

    void writeZip(List<TeacherAppraisalExportFile> files, OutputStream outputStream) throws IOException;
}
