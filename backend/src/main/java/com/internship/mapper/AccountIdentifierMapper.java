package com.internship.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** Account references are updated in the same transaction as the account. */
@Mapper
public interface AccountIdentifierMapper {
    @Update("UPDATE class SET teacher_user_id=#{newId}, update_time=NOW() WHERE teacher_user_id=#{oldId}")
    int renameTeacher(@Param("oldId") String oldId, @Param("newId") String newId);

    @Update("UPDATE internship_application SET student_user_id=#{newId} WHERE student_id=#{id}")
    int renameApplicationStudent(@Param("id") Long id, @Param("newId") String newId);

    @Update("UPDATE student_application SET student_user_id=#{newId} WHERE student_id=#{id}")
    int renameStudentApplicationStudent(@Param("id") Long id, @Param("newId") String newId);

    @Update("UPDATE internship_reflection SET student_user_id=#{newId} WHERE student_id=#{id}")
    int renameReflectionStudent(@Param("id") Long id, @Param("newId") String newId);

    @Update("UPDATE internship_confirmation_record SET student_user_id=#{newId} WHERE student_id=#{id}")
    int renameConfirmationStudent(@Param("id") Long id, @Param("newId") String newId);
}
