package com.internship.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.internship.entity.InternshipProgressRecord;
import com.internship.entity.dto.RecentActivityRecordRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface InternshipProgressRecordMapper extends BaseMapper<InternshipProgressRecord> {
    List<RecentActivityRecordRow> selectRecentPage(@Param("studentId") Long studentId,
                                                    @Param("cursorTime") Date cursorTime,
                                                    @Param("cursorId") Long cursorId,
                                                    @Param("limit") int limit);
}
