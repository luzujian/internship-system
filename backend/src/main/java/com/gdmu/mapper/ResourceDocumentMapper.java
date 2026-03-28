package com.gdmu.mapper;

import com.gdmu.entity.ResourceDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ResourceDocumentMapper {
    
    int insert(ResourceDocument resourceDocument);
    
    ResourceDocument findById(Long id);
    
    int update(ResourceDocument resourceDocument);
    
    int deleteById(Long id);
    
    List<ResourceDocument> findAll();

    List<ResourceDocument> findPublished();

    List<ResourceDocument> list(@Param("title") String title, 
                                @Param("status") String status,
                                @Param("publisherRole") String publisherRole);
    
    List<ResourceDocument> selectByIds(List<Long> ids);
    
    int batchDeleteByIds(@Param("ids") List<Long> ids);
    
    Long count();
    
    List<ResourceDocument> findPage(@Param("offset") Integer offset, 
                                    @Param("pageSize") Integer pageSize,
                                    @Param("title") String title,
                                    @Param("status") String status,
                                    @Param("publisherRole") String publisherRole);
    
    int incrementDownloadCount(Long id);
    
    int incrementViewCount(Long id);
    
    List<ResourceDocument> findTeacherPage(@Param("offset") Integer offset,
                                           @Param("pageSize") Integer pageSize,
                                           @Param("title") String title,
                                           @Param("status") String status,
                                           @Param("publisherRole") String publisherRole,
                                           @Param("publisherId") Long publisherId,
                                           @Param("filterType") String filterType);
    
    Long countTeacherResources(@Param("title") String title,
                               @Param("status") String status,
                               @Param("publisherRole") String publisherRole,
                               @Param("publisherId") Long publisherId,
                               @Param("filterType") String filterType);
}
