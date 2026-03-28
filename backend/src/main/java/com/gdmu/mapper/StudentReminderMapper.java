package com.gdmu.mapper;

import com.gdmu.entity.StudentReminder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface StudentReminderMapper {
    int insert(StudentReminder reminder);

    StudentReminder selectById(@Param("id") Long id);

    int updateById(StudentReminder reminder);

    List<StudentReminder> selectPendingByStudentId(@Param("studentId") Long studentId);
}