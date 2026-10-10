package com.internship.mapper;

import com.internship.entity.InternshipApplicationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Date;

@Mapper
public interface InternshipApplicationMapper {
    
    int insert(InternshipApplicationEntity application);
    
    InternshipApplicationEntity findById(@Param("id") Long id);
    
    List<InternshipApplicationEntity> findByCompanyId(@Param("companyId") Long companyId);
    
    List<InternshipApplicationEntity> findByPositionId(@Param("positionId") Long positionId);
    
    List<InternshipApplicationEntity> findByStudentId(@Param("studentId") Long studentId);
    
    List<InternshipApplicationEntity> findByStatus(@Param("status") String status);
    
    List<InternshipApplicationEntity> findByCompanyIdAndStatus(@Param("companyId") Long companyId, @Param("status") String status);
    
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    int updateLatestStatusByStudentAndPosition(@Param("studentId") Long studentId,
                                               @Param("positionId") Long positionId,
                                               @Param("status") String status);
    
    int updateViewed(@Param("id") Long id, @Param("viewed") Boolean viewed);

    List<InternshipApplicationEntity> findActivityPage(@Param("companyId") Long companyId,
            @Param("unreadOnly") boolean unreadOnly, @Param("offset") int offset, @Param("limit") int limit);

    long countActivities(@Param("companyId") Long companyId, @Param("unreadOnly") boolean unreadOnly);

    List<InternshipApplicationEntity> findActivityAttempts(@Param("companyId") Long companyId,
            @Param("studentId") Long studentId, @Param("positionId") Long positionId);

    int readActivityVersion(@Param("id") Long id, @Param("companyId") Long companyId,
            @Param("status") String status, @Param("updateTime") Date updateTime);
    
    int deleteById(@Param("id") Long id);
    
    List<InternshipApplicationEntity> findAll();
    
    Long countByCompanyId(@Param("companyId") Long companyId);
    
    Long countByCompanyIdAndStatus(@Param("companyId") Long companyId, @Param("status") String status);
}
