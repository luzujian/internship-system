package com.gdmu.mapper;

import com.gdmu.entity.Resource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ResourceMapper {
    
    int insert(Resource resource);
    
    int updateById(Resource resource);
    
    int deleteById(Long id);
    
    Resource selectById(Long id);
    
    List<Resource> selectList();
    
    List<Resource> selectByType(@Param("type") String type);
    
    List<Resource> selectByStatus(@Param("status") String status);
    
    List<Resource> selectByTypeAndStatus(@Param("type") String type, @Param("status") String status);
    
    List<Resource> searchByKeyword(@Param("keyword") String keyword);
    
    List<Resource> selectByTypeAndKeyword(@Param("type") String type, @Param("keyword") String keyword);
    
    List<Resource> selectPage(@Param("offset") int offset, @Param("limit") int limit);
    
    List<Resource> selectPageByType(@Param("type") String type, @Param("offset") int offset, @Param("limit") int limit);
    
    int countByType(@Param("type") String type);
    
    int countByStatus(@Param("status") String status);
    
    int countByTypeAndStatus(@Param("type") String type, @Param("status") String status);
    
    int countByTypeAndKeyword(@Param("type") String type, @Param("keyword") String keyword);
    
    int incrementDownloadCount(@Param("id") Long id);
}
