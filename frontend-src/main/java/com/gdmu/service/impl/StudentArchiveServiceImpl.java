package com.gdmu.service.impl;

import com.gdmu.entity.PageResult;
import com.gdmu.entity.StudentArchive;
import com.gdmu.exception.BusinessException;
import com.gdmu.mapper.StudentArchiveMapper;
import com.gdmu.service.StudentArchiveService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class StudentArchiveServiceImpl implements StudentArchiveService {
    
    @Autowired
    private StudentArchiveMapper studentArchiveMapper;
    
    @Override
    public int insert(StudentArchive archive) {
        archive.setUploadTime(new Date());
        archive.setStatus(0); // 默认待审核
        return studentArchiveMapper.insert(archive);
    }
    
    @Override
    public StudentArchive findById(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException("材料ID无效");
        }
        return studentArchiveMapper.findById(id);
    }
    
    @Override
    public int update(StudentArchive archive) {
        return studentArchiveMapper.update(archive);
    }
    
    @Override
    public int delete(Long id) {
        return studentArchiveMapper.deleteById(id);
    }
    
    @Override
    public PageResult<Map<String, Object>> findPage(Integer page, Integer pageSize, String studentName, String fileType, Integer status) {
        PageHelper.startPage(page, pageSize);
        List<Map<String, Object>> list = studentArchiveMapper.findPageWithStudent(studentName, fileType, status);
        PageInfo<Map<String, Object>> pageInfo = new PageInfo<>(list);
        return PageResult.build(pageInfo.getTotal(), pageInfo.getList(),
                pageInfo.getPages(), pageInfo.getPageNum(), pageInfo.getPageSize());
    }
    
    @Override
    public List<StudentArchive> findByStudentId(Long studentId) {
        return studentArchiveMapper.findByStudentId(studentId);
    }
    
    @Override
    public Map<String, Object> getStatistics() {
        return studentArchiveMapper.getStatistics();
    }
    
    @Override
    public int updateStatus(Long id, Integer status) {
        StudentArchive archive = new StudentArchive();
        archive.setId(id);
        archive.setStatus(status);
        return studentArchiveMapper.update(archive);
    }
    
    @Override
    public int batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("材料ID列表不能为空");
        }
        return studentArchiveMapper.batchDeleteByIds(ids);
    }
}
