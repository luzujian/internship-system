package com.gdmu.mapper;

import com.gdmu.entity.StudentArchive;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface StudentArchiveMapper {
    
    int insert(StudentArchive archive);
    
    StudentArchive findById(Long id);
    
    int update(StudentArchive archive);
    
    int deleteById(Long id);
    
    List<Map<String, Object>> findPageWithStudent(@Param("studentName") String studentName,
                                                   @Param("fileType") String fileType,
                                                   @Param("status") Integer status);
    
    List<StudentArchive> findByStudentId(Long studentId);
    
    Map<String, Object> getStatistics();
    
    int batchDeleteByIds(@Param("ids") List<Long> ids);
    
    int countByStatus(@Param("status") Integer status);
}
