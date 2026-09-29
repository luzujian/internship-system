package com.internship.controller;

import com.internship.anno.Log;
import com.internship.dto.CompanyRegisterDTO;
import com.internship.entity.CompanyAlumniMember;
import com.internship.entity.CompanyUser;
import com.internship.utils.UserCredentialDefaults;
import com.internship.entity.Result;
import com.internship.mapper.CompanyUserMapper;
import com.internship.service.CompanyRegistrationRateLimitService;
import com.internship.service.CompanyTagService;
import com.internship.service.CompanyUserService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/company")
public class CompanyRegisterController {
    
    @Autowired
    private CompanyUserService companyUserService;
    
    @Autowired
    private CompanyUserMapper companyUserMapper;
    
    @Autowired
    private CompanyRegistrationRateLimitService registrationRateLimitService;
    
    @Autowired
    private CompanyTagService companyTagService;
    
    @PostMapping("/register")
    @Log(operationType = "CREATE", module = "COMPANY_MANAGEMENT", description = "企业注册", riskLevel = "MEDIUM")
    public Result register(@Valid @RequestBody CompanyRegisterDTO registerDTO,
                           HttpServletResponse response) {
        try {
            log.info("收到企业注册申请: {}", registerDTO.getCompanyName());
            if (!registrationRateLimitService.allow(registerDTO.getCompanyName(), registerDTO.getEmail())) {
                return Result.error("提交过于频繁，请稍后重试");
            }
            
            CompanyUser existUser = companyUserMapper.findByUsername(registerDTO.getCompanyName());
            if (existUser != null) {
                return Result.error("企业名称已被注册");
            }
            
            CompanyUser companyUser = new CompanyUser();
            companyUser.setCompanyName(registerDTO.getCompanyName());
            companyUser.setCreditCode(registerDTO.getCreditCode());
            companyUser.setContactPerson(registerDTO.getContactPerson());
            companyUser.setContactPhone(registerDTO.getContactPhone());
            companyUser.setContactEmail(registerDTO.getContactEmail());
            companyUser.setEmail(registerDTO.getEmail());
            companyUser.setPhone(registerDTO.getPhone());

            // 新地址字段
            companyUser.setProvince(registerDTO.getProvince());
            companyUser.setCity(registerDTO.getCity());
            companyUser.setDistrict(registerDTO.getDistrict());
            companyUser.setDetailAddress(registerDTO.getDetailAddress());
            // 保留旧的 address 字段以兼容（可选）
            companyUser.setAddress(registerDTO.getDetailAddress());

            companyUser.setIntroduction(registerDTO.getIntroduction());
            companyUser.setBusinessLicense(registerDTO.getBusinessLicense());
            companyUser.setIndustry(registerDTO.getIndustry());
            companyUser.setScale(registerDTO.getScale());
            companyUser.setWebsite(registerDTO.getWebsite());
            
            // 注册仅面向校企合作企业，不能由客户端选择准入类型。
            companyUser.setCooperationMode("mutual_choice");

            String legalIdCard = registerDTO.getLegalIdCardFront();
            if (registerDTO.getLegalIdCardBack() != null && !registerDTO.getLegalIdCardBack().isEmpty()) {
                legalIdCard += "," + registerDTO.getLegalIdCardBack();
            }
            companyUser.setLegalIdCard(legalIdCard);

            companyUser.setIsInternshipBase(registerDTO.getIsInternshipBase() != null ? registerDTO.getIsInternshipBase() : 0);
            companyUser.setPlaquePhoto(registerDTO.getPlaquePhoto());
            companyUser.setAcceptBackup(registerDTO.getAcceptBackup() != null ? registerDTO.getAcceptBackup() : 0);

            companyUser.setIsAlumniUnit(registerDTO.getIsAlumniUnit() != null ? registerDTO.getIsAlumniUnit() : 0);
            List<CompanyAlumniMember> alumniMembers = registerDTO.getAlumniMembers();
            if (companyUser.getIsAlumniUnit() == 1
                    && (alumniMembers == null || alumniMembers.isEmpty())
                    && registerDTO.getAlumniRepresentativeName() != null
                    && !registerDTO.getAlumniRepresentativeName().trim().isEmpty()) {
                CompanyAlumniMember legacyMember = new CompanyAlumniMember();
                legacyMember.setName(registerDTO.getAlumniRepresentativeName());
                legacyMember.setPhone(registerDTO.getAlumniRepresentativePhone());
                legacyMember.setPosition(registerDTO.getAlumniRepresentativePosition());
                alumniMembers = new ArrayList<>();
                alumniMembers.add(legacyMember);
            }
            companyUser.setAlumniMembers(companyUser.getIsAlumniUnit() == 1
                    ? alumniMembers : new ArrayList<>());

            companyUser.setHasTrainingBaseAgreement(registerDTO.getHasTrainingBaseAgreement() != null ? registerDTO.getHasTrainingBaseAgreement() : 0);

            String username = registerDTO.getCompanyName();
            String password = UserCredentialDefaults.PASSWORD;
            companyUser.setUsername(username);
            companyUser.setPassword(password);
            companyUser.setRole("ROLE_COMPANY");
            
            companyUser.setAuditStatus(0);
            companyUser.setStatus(1);
            companyUser.setApplyTime(new Date());
            companyUser.setRegisterTime(new Date());
            companyUser.setCreateTime(new Date());
            companyUser.setUpdateTime(new Date());
            
            int result = companyUserService.insert(companyUser);
            if (result > 0) {
                try {
                    companyTagService.updateCompanyTag(companyUser.getId());
                } catch (Exception tagException) {
                    log.warn("更新企业标签失败，但不影响注册: {}", tagException.getMessage());
                }
                log.info("企业注册申请提交成功: {}", registerDTO.getCompanyName());
                response.setHeader("Cache-Control", "no-store");
                return Result.success("注册申请提交成功，请等待管理员审核");
            } else {
                return Result.error("注册申请提交失败");
            }
        } catch (Exception e) {
            log.error("企业注册申请失败: {}", e.getMessage(), e);
            return Result.error("注册申请提交失败，请稍后重试");
        }
    }
    
    @GetMapping("/check-username")
    public Result checkUsername(@RequestParam String username) {
        try {
            CompanyUser existUser = companyUserMapper.findByUsername(username);
            if (existUser != null) {
                return Result.error("企业名称已被注册");
            }
            return Result.success("企业名称可用");
        } catch (Exception e) {
            log.error("检查企业名称失败: {}", e.getMessage(), e);
            return Result.error("检查企业名称失败");
        }
    }
    
    @GetMapping("/check-status")
    public Result checkCompanyStatus(@RequestParam String companyName) {
        try {
            CompanyUser companyUser = companyUserMapper.findByUsername(companyName);
            if (companyUser == null) {
                return Result.error("该企业未注册");
            }
            
            companyUser = companyUserService.findById(companyUser.getId());
            if (companyUser == null) {
                return Result.error("该企业未注册");
            }
            Map<String, Object> data = new java.util.HashMap<>();
            data.put("id", companyUser.getId());
            data.put("status", companyUser.getAuditStatus());
            data.put("recallStatus", companyUser.getRecallStatus());
            data.put("companyName", companyUser.getCompanyName());
            data.put("creditCode", companyUser.getCreditCode());
            data.put("contactPerson", companyUser.getContactPerson());
            data.put("contactPhone", companyUser.getContactPhone());
            data.put("contactEmail", companyUser.getContactEmail());
            data.put("email", companyUser.getEmail());
            data.put("phone", companyUser.getPhone());
            data.put("address", companyUser.getAddress());
            data.put("introduction", companyUser.getIntroduction());
            data.put("businessLicense", companyUser.getBusinessLicense());
            data.put("legalIdCard", companyUser.getLegalIdCard());
            data.put("isInternshipBase", companyUser.getIsInternshipBase());
            data.put("plaquePhoto", companyUser.getPlaquePhoto());
            data.put("hasReceivedInterns", companyUser.getHasReceivedInterns());
            data.put("currentEmployeesCount", companyUser.getCurrentEmployeesCount());
            data.put("acceptBackup", companyUser.getAcceptBackup());
            data.put("applyTime", companyUser.getApplyTime());
            data.put("auditTime", companyUser.getAuditTime());
            data.put("auditRemark", companyUser.getAuditRemark());
            data.put("recallReason", companyUser.getRecallReason());
            data.put("recallApplyTime", companyUser.getRecallApplyTime());
            data.put("recallAuditTime", companyUser.getRecallAuditTime());
            data.put("recallAuditRemark", companyUser.getRecallAuditRemark());
            data.put("recallReviewerId", companyUser.getRecallReviewerId());

            // 新增字段
            data.put("industry", companyUser.getIndustry());
            data.put("scale", companyUser.getScale());
            data.put("province", companyUser.getProvince());
            data.put("city", companyUser.getCity());
            data.put("district", companyUser.getDistrict());
            data.put("detailAddress", companyUser.getDetailAddress());
            data.put("website", companyUser.getWebsite());
            data.put("cooperationMode", companyUser.getCooperationMode());
            data.put("isAlumniUnit", companyUser.getIsAlumniUnit());
            data.put("alumniMembers", companyUser.getAlumniMembers());
            data.put("alumniRepresentativeName", companyUser.getAlumniRepresentativeName());
            data.put("alumniRepresentativePhone", companyUser.getAlumniRepresentativePhone());
            data.put("alumniRepresentativePosition", companyUser.getAlumniRepresentativePosition());
            data.put("hasTrainingBaseAgreement", companyUser.getHasTrainingBaseAgreement());

            return Result.success(data);
        } catch (Exception e) {
            log.error("检查企业注册状态失败: {}", e.getMessage(), e);
            return Result.error("检查企业注册状态失败");
        }
    }
    
    @PostMapping("/recall")
    @Log(operationType = "UPDATE", module = "COMPANY_MANAGEMENT", description = "撤回企业注册申请", riskLevel = "MEDIUM")
    public ResponseEntity<Result> recallApplication(@RequestBody Map<String, String> request) {
        try {
            String companyName = request.get("companyName");
            String recallReason = request.get("recallReason");

            if (recallReason == null || recallReason.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Result.error("撤回原因不能为空"));
            }

            CompanyUser companyUser = companyName == null ? null : companyUserMapper.findByUsername(companyName);
            if (companyUser == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Result.error("企业不存在"));
            }
            if (companyUser.getAuditStatus() != 0) {
                return ResponseEntity.badRequest().body(Result.error("只能追回正在审核中的申请"));
            }
            if (companyUser.getRecallStatus() != 0) {
                return ResponseEntity.badRequest().body(Result.error("已有撤回申请"));
            }

            Date recallTime = new Date();
            companyUser.setRecallStatus(2);
            companyUser.setRecallReason(recallReason);
            companyUser.setRecallApplyTime(recallTime);
            companyUser.setRecallAuditTime(recallTime);
            companyUser.setRecallAuditRemark("自动允许撤回修改");
            companyUser.setRecallReviewerId(null);
            companyUser.setAuditStatus(3);
            companyUser.setUpdateTime(recallTime);

            int result = companyUserMapper.update(companyUser);
            if (result > 0) {
                log.info("企业注册撤回申请已自动允许修改: companyId={}", companyUser.getId());
                return ResponseEntity.ok(Result.success("撤回申请已自动允许，现在可以修改并重新提交"));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.error("提交撤回申请失败"));
        } catch (Exception e) {
            log.error("提交企业注册撤回申请失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.error("提交撤回申请失败"));
        }
    }
    @PutMapping("/update/{id}")
    @Log(operationType = "UPDATE", module = "COMPANY_MANAGEMENT", description = "更新企业资料", riskLevel = "MEDIUM")
    public ResponseEntity<Result> updateCompany(@PathVariable Long id, @RequestBody Map<String, Object> updateData) {
        try {
            CompanyUser existingUser = companyUserMapper.findById(id);
            if (existingUser == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Result.error("企业不存在"));
            }
            
            if (existingUser.getRecallStatus() != 1 && existingUser.getRecallStatus() != 2 && existingUser.getRecallStatus() != 3 && existingUser.getAuditStatus() != 2) {
                return ResponseEntity.badRequest().body(Result.error("只能更新已允许撤回或审核未通过的申请"));
            }
            
            String email = (String) updateData.get("email");

            if (email == null || email.isEmpty()) {
                return ResponseEntity.badRequest().body(Result.error("邮箱不能为空"));
            }

            if (!email.trim().matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
                return ResponseEntity.badRequest().body(Result.error("个人邮箱格式不正确"));
            }

            CompanyUser companyUser = new CompanyUser();
            companyUser.setId(id);

            if (updateData.containsKey("creditCode")) {
                companyUser.setCreditCode((String) updateData.get("creditCode"));
            }
            if (updateData.containsKey("contactPerson")) {
                companyUser.setContactPerson((String) updateData.get("contactPerson"));
            }
            if (updateData.containsKey("contactPhone")) {
                companyUser.setContactPhone((String) updateData.get("contactPhone"));
            }
            if (updateData.containsKey("contactEmail")) {
                companyUser.setContactEmail((String) updateData.get("contactEmail"));
            }
            if (updateData.containsKey("email")) {
                companyUser.setEmail(email.trim());
            }
            if (updateData.containsKey("phone")) {
                companyUser.setPhone((String) updateData.get("phone"));
            }
            // 新地址字段
            if (updateData.containsKey("province")) {
                companyUser.setProvince((String) updateData.get("province"));
            }
            if (updateData.containsKey("city")) {
                companyUser.setCity((String) updateData.get("city"));
            }
            if (updateData.containsKey("district")) {
                companyUser.setDistrict((String) updateData.get("district"));
            }
            if (updateData.containsKey("detailAddress")) {
                companyUser.setDetailAddress((String) updateData.get("detailAddress"));
            }
            if (updateData.containsKey("address")) {
                companyUser.setAddress((String) updateData.get("address"));
            }
            if (updateData.containsKey("introduction")) {
                companyUser.setIntroduction((String) updateData.get("introduction"));
            }
            if (updateData.containsKey("businessLicense")) {
                companyUser.setBusinessLicense((String) updateData.get("businessLicense"));
            }
            // 新增字段
            if (updateData.containsKey("industry")) {
                companyUser.setIndustry((String) updateData.get("industry"));
            }
            if (updateData.containsKey("scale")) {
                companyUser.setScale((String) updateData.get("scale"));
            }
            if (updateData.containsKey("website")) {
                companyUser.setWebsite((String) updateData.get("website"));
            }
            companyUser.setCooperationMode("mutual_choice");
            if (updateData.containsKey("legalIdCardFront")) {
                String legalIdCard = (String) updateData.get("legalIdCardFront");
                if (updateData.containsKey("legalIdCardBack") && updateData.get("legalIdCardBack") != null) {
                    String back = (String) updateData.get("legalIdCardBack");
                    if (back != null && !back.isEmpty()) {
                        legalIdCard += "," + back;
                    }
                }
                companyUser.setLegalIdCard(legalIdCard);
            }
            if (updateData.containsKey("isInternshipBase")) {
                companyUser.setIsInternshipBase(((Number) updateData.get("isInternshipBase")).intValue());
            }
            if (updateData.containsKey("plaquePhoto")) {
                companyUser.setPlaquePhoto((String) updateData.get("plaquePhoto"));
            }
            if (updateData.containsKey("acceptBackup")) {
                companyUser.setAcceptBackup(((Number) updateData.get("acceptBackup")).intValue());
            }
            if (updateData.containsKey("isAlumniUnit")) {
                Integer isAlumniUnit = parseBinaryFlag(updateData.get("isAlumniUnit"), "是否校友单位");
                companyUser.setIsAlumniUnit(isAlumniUnit);
                if (isAlumniUnit == 0 && !updateData.containsKey("alumniMembers")) {
                    companyUser.setAlumniMembers(new ArrayList<>());
                }
            }
            if (updateData.containsKey("alumniMembers")) {
                companyUser.setAlumniMembers(parseAlumniMembers(updateData.get("alumniMembers")));
            }
            if (updateData.containsKey("alumniRepresentativeName")) {
                companyUser.setAlumniRepresentativeName((String) updateData.get("alumniRepresentativeName"));
            }
            if (updateData.containsKey("alumniRepresentativePhone")) {
                companyUser.setAlumniRepresentativePhone((String) updateData.get("alumniRepresentativePhone"));
            }
            if (updateData.containsKey("alumniRepresentativePosition")) {
                companyUser.setAlumniRepresentativePosition((String) updateData.get("alumniRepresentativePosition"));
            }
            if (updateData.containsKey("hasTrainingBaseAgreement")) {
                companyUser.setHasTrainingBaseAgreement(((Number) updateData.get("hasTrainingBaseAgreement")).intValue());
            }
            
            companyUser.setAuditStatus(0);
            // 仅重置当前撤回状态；历史撤回原因/时间继续保留，供管理员撤回记录追溯。
            companyUser.setRecallStatus(0);
            companyUser.setApplyTime(new java.util.Date());
            companyUser.setUpdateTime(new java.util.Date());
            
            int result = companyUserService.update(companyUser);
            
            if (result > 0) {
                log.info("企业注册信息更新成功: {}", existingUser.getCompanyName());
                try {
                    companyTagService.updateCompanyTag(id);
                } catch (Exception tagException) {
                    log.warn("企业标签更新失败: companyId={}", id, tagException);
                }
                return ResponseEntity.ok(Result.success("更新成功"));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.error("更新失败"));
            }
        } catch (Exception e) {
            log.error("更新企业注册信息失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.error("更新失败"));
        }
    }

    private Integer parseBinaryFlag(Object value, String fieldName) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue ? 1 : 0;
        }
        if (value instanceof Number number) {
            int result = number.intValue();
            if (result == 0 || result == 1) {
                return result;
            }
        }
        if (value instanceof String text && ("0".equals(text) || "1".equals(text))) {
            return Integer.valueOf(text);
        }
        throw new IllegalArgumentException(fieldName + "必须为0或1");
    }

    private List<CompanyAlumniMember> parseAlumniMembers(Object value) {
        if (!(value instanceof List<?> rawMembers)) {
            throw new IllegalArgumentException("校友列表格式不正确");
        }
        List<CompanyAlumniMember> members = new ArrayList<>();
        for (Object rawMember : rawMembers) {
            if (!(rawMember instanceof Map<?, ?> memberMap)) {
                throw new IllegalArgumentException("校友列表格式不正确");
            }
            CompanyAlumniMember member = new CompanyAlumniMember();
            member.setName(asNullableString(memberMap.get("name")));
            member.setPhone(asNullableString(memberMap.get("phone")));
            member.setPosition(asNullableString(memberMap.get("position")));
            members.add(member);
        }
        return members;
    }

    private String asNullableString(Object value) {
        return value == null ? null : value.toString();
    }
}
