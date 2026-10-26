package com.internship.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.internship.dto.TeacherCompanyDTO;
import com.internship.dto.TeacherRecruitmentPositionDTO;
import com.internship.entity.PageResult;
import com.internship.exception.BusinessException;
import com.internship.mapper.TeacherRecruitmentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/** All teacher levels share the published catalogue; no student-scope filtering or writes. */
@Service
@Transactional(readOnly = true)
public class TeacherRecruitmentService {
    private final TeacherRecruitmentMapper recruitmentMapper;
    private final CompanyRelationsService companyRelationsService;

    public TeacherRecruitmentService(TeacherRecruitmentMapper recruitmentMapper,
                                     CompanyRelationsService companyRelationsService) {
        this.recruitmentMapper = recruitmentMapper;
        this.companyRelationsService = companyRelationsService;
    }

    public PageResult<TeacherRecruitmentPositionDTO> findPage(
            Integer page, Integer pageSize, String companyName, String positionName, String status, Long categoryId) {
        String selectedStatus = trim(status);
        if (selectedStatus != null && !List.of("active", "paused").contains(selectedStatus)) {
            throw new BusinessException("招聘状态必须为招聘中或已暂停");
        }
        PageHelper.startPage(page, pageSize);
        List<TeacherRecruitmentPositionDTO> positions = recruitmentMapper.findPublishedPositions(
                trim(companyName), trim(positionName), selectedStatus, categoryId);
        PageInfo<TeacherRecruitmentPositionDTO> info = new PageInfo<>(positions);
        return PageResult.build(info.getTotal(), info.getList(), info.getPages(),
                info.getPageNum(), info.getPageSize());
    }

    public Detail findDetail(Long id) {
        TeacherRecruitmentPositionDTO position = recruitmentMapper.findPublishedPositionById(id);
        if (position == null) {
            throw new BusinessException("已发布岗位不存在");
        }
        TeacherCompanyDTO company = companyRelationsService.findCompanyDetail(position.getCompanyId());
        if (company == null) {
            throw new BusinessException("企业不存在");
        }
        return new Detail(position, company);
    }

    public record Detail(TeacherRecruitmentPositionDTO position, TeacherCompanyDTO company) { }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
