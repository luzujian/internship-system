package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.dto.CompanyRegisterDTO;
import com.gdmu.entity.CompanyUser;
import com.gdmu.entity.Result;
import com.gdmu.mapper.CompanyUserMapper;
import com.gdmu.service.AIRecallAuditService;
import com.gdmu.service.CompanyTagService;
import com.gdmu.service.CompanyUserService;
import com.gdmu.service.SmsService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/company")
public class CompanyRegisterController {
    
    @Autowired
    private CompanyUserService companyUserService;
    
    @Autowired
    private CompanyUserMapper companyUserMapper;
    
    @Autowired
    private SmsService smsService;
    
    @Autowired
    private CompanyTagService companyTagService;
    
    @Autowired
    private AIRecallAuditService aiRecallAuditService;
    
    @PostMapping("/register")
    public Result register(@Valid @RequestBody CompanyRegisterDTO registerDTO) {
        try {
            log.info("收到企业注册申请: {}", registerDTO.getCompanyName());
            
            String phone = registerDTO.getPhone();
            String verifyCode = registerDTO.getVerifyCode();
            
            if (!smsService.verifyCode(phone, verifyCode)) {
                return Result.error("验证码错误或已过期");
            }
            
            CompanyUser existUser = companyUserMapper.findByUsername(registerDTO.getCompanyName());
            if (existUser != null) {
                return Result.error("企业名称已被注册");
            }
            
            CompanyUser companyUser = new CompanyUser();
            companyUser.setCompanyName(registerDTO.getCompanyName());
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
            companyUser.setCooperationMode(registerDTO.getCooperationMode());

            String legalIdCard = registerDTO.getLegalIdCardFront();
            if (registerDTO.getLegalIdCardBack() != null && !registerDTO.getLegalIdCardBack().isEmpty()) {
                legalIdCard += "," + registerDTO.getLegalIdCardBack();
            }
            companyUser.setLegalIdCard(legalIdCard);

            companyUser.setIsInternshipBase(registerDTO.getIsInternshipBase() != null ? registerDTO.getIsInternshipBase() : 0);
            companyUser.setPlaquePhoto(registerDTO.getPlaquePhoto());
            companyUser.setAcceptBackup(registerDTO.getAcceptBackup() != null ? registerDTO.getAcceptBackup() : 0);
            companyUser.setMaxBackupStudents(registerDTO.getMaxBackupStudents() != null ? registerDTO.getMaxBackupStudents() : 0);
            
            String username = registerDTO.getCompanyName();
            String password = "123456";
            companyUser.setUsername(username);
            companyUser.setPassword(password);
            companyUser.setRole("ROLE_COMPANY");
            
            companyUser.setAuditStatus(0);
            companyUser.setStatus(0);
            companyUser.setApplyTime(new Date());
            companyUser.setRegisterTime(new Date());
            companyUser.setCreateTime(new Date());
            companyUser.setUpdateTime(new Date());
            
            int result = companyUserMapper.insert(companyUser);
            if (result > 0) {
                try {
                    companyTagService.updateCompanyTag(companyUser.getId());
                } catch (Exception tagException) {
                    log.warn("更新企业标签失败，但不影响注册: {}", tagException.getMessage());
                }
                log.info("企业注册申请提交成功: {}", registerDTO.getCompanyName());
                return Result.success("注册申请提交成功，请等待管理员审核");
            } else {
                return Result.error("注册申请提交失败");
            }
        } catch (Exception e) {
            log.error("企业注册申请失败: {}", e.getMessage(), e);
            return Result.error("注册申请提交失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/send-verify-code")
    public Result sendVerifyCode(@RequestBody Map<String, String> request) {
        try {
            String phone = request.get("phone");
            if (phone == null || phone.isEmpty()) {
                return Result.error("手机号不能为空");
            }
            
            if (!phone.matches("^1[3-9]\\d{9}$")) {
                return Result.error("手机号格式不正确");
            }
            
            if (!smsService.checkRateLimit(phone)) {
                return Result.error("发送过于频繁，请稍后再试");
            }
            
            boolean success = smsService.sendVerifyCode(phone);
            
            if (success) {
                return Result.success("验证码发送成功");
            } else {
                return Result.error("发送验证码失败");
            }
        } catch (Exception e) {
            log.error("发送验证码失败: {}", e.getMessage(), e);
            return Result.error("发送验证码失败: " + e.getMessage());
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
            
            Map<String, Object> data = new java.util.HashMap<>();
            data.put("id", companyUser.getId());
            data.put("status", companyUser.getAuditStatus());
            data.put("recallStatus", companyUser.getRecallStatus());
            data.put("companyName", companyUser.getCompanyName());
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
            data.put("maxBackupStudents", companyUser.getMaxBackupStudents());
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

            return Result.success(data);
        } catch (Exception e) {
            log.error("检查企业注册状态失败: {}", e.getMessage(), e);
            return Result.error("检查企业注册状态失败");
        }
    }
    
    @PostMapping("/recall")
    public Result recallApplication(@RequestBody Map<String, String> request) {
        try {
            String companyName = request.get("companyName");
            String recallReason = request.get("recallReason");
            
            if (recallReason == null || recallReason.trim().isEmpty()) {
                return Result.error("撤回原因不能为空");
            }
            
            CompanyUser companyUser = companyUserMapper.findByUsername(companyName);
            
            if (companyUser == null) {
                return Result.error("企业不存在");
            }
            
            if (companyUser.getAuditStatus() != 0) {
                return Result.error("只能追回正在审核中的申请");
            }
            
            if (companyUser.getRecallStatus() != 0) {
                return Result.error("已有撤回申请正在审核中");
            }
            
            companyUser.setRecallStatus(1);
            companyUser.setRecallReason(recallReason);
            companyUser.setRecallApplyTime(new java.util.Date());
            companyUser.setUpdateTime(new java.util.Date());
            int result = companyUserMapper.update(companyUser);
            
            if (result > 0) {
                log.info("企业注册撤回申请提交成功: {}, 原因: {}", companyName, recallReason);
                
                final Long companyId = companyUser.getId();
                final String finalRecallReason = recallReason;
                
                CompletableFuture.runAsync(() -> {
                    try {
                        log.info("开始系统审核撤回申请，企业ID: {}", companyId);
                        
                        boolean approved = isStandardRecallReason(finalRecallReason);
                        
                        CompanyUser userToUpdate = companyUserMapper.findById(companyId);
                        if (userToUpdate != null) {
                            if (approved) {
                                userToUpdate.setRecallStatus(2);
                                userToUpdate.setRecallAuditTime(new Date());
                                companyUserMapper.update(userToUpdate);
                                log.info("系统审核通过撤回申请，企业ID: {}", companyId);
                            } else {
                                userToUpdate.setRecallReviewerId(-1L);
                                userToUpdate.setUpdateTime(new java.util.Date());
                                companyUserMapper.update(userToUpdate);
                                log.info("系统审核转人工审核，企业ID: {}", companyId);
                            }
                        }
                    } catch (Exception e) {
                        log.error("系统审核失败，转人工审核，企业ID: {}", companyId, e);
                    }
                });
                
                return Result.success("撤回申请已提交，系统正在审核中，请稍后查看结果");
            } else {
                return Result.error("提交撤回申请失败");
            }
        } catch (Exception e) {
            log.error("提交企业注册撤回申请失败: {}", e.getMessage(), e);
            return Result.error("提交撤回申请失败: " + e.getMessage());
        }
    }
    
    private boolean isStandardRecallReason(String recallReason) {
        if (recallReason == null || recallReason.isEmpty()) {
            return false;
        }
        
        return recallReason.contains("联系人姓名填写错误") ||
               recallReason.contains("联系电话填写错误") ||
               recallReason.contains("联系邮箱填写错误") ||
               recallReason.contains("手机号填写错误") ||
               recallReason.contains("企业地址填写错误") ||
               recallReason.contains("企业介绍内容不完整") ||
               recallReason.contains("营业执照图片不清晰") ||
               recallReason.contains("法人身份证照片缺失") ||
               recallReason.contains("实习基地牌匾照片未上传");
    }
    
    @PutMapping("/update/{id}")
    public Result updateCompany(@PathVariable Long id, @RequestBody Map<String, Object> updateData) {
        try {
            CompanyUser existingUser = companyUserMapper.findById(id);
            if (existingUser == null) {
                return Result.error("企业不存在");
            }
            
            if (existingUser.getRecallStatus() != 2) {
                return Result.error("只能更新已批准撤回的申请");
            }
            
            String phone = (String) updateData.get("phone");
            String verifyCode = (String) updateData.get("verifyCode");
            
            if (phone == null || phone.isEmpty()) {
                return Result.error("手机号不能为空");
            }
            
            if (verifyCode == null || verifyCode.isEmpty()) {
                return Result.error("验证码不能为空");
            }
            
            if (!smsService.verifyCode(phone, verifyCode)) {
                return Result.error("验证码错误或已过期");
            }
            
            CompanyUser companyUser = new CompanyUser();
            companyUser.setId(id);

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
                companyUser.setEmail((String) updateData.get("email"));
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
            if (updateData.containsKey("cooperationMode")) {
                companyUser.setCooperationMode((String) updateData.get("cooperationMode"));
            }
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
            if (updateData.containsKey("maxBackupStudents")) {
                companyUser.setMaxBackupStudents(((Number) updateData.get("maxBackupStudents")).intValue());
            }
            
            companyUser.setAuditStatus(0);
            companyUser.setRecallStatus(0);
            companyUser.setRecallReason(null);
            companyUser.setRecallApplyTime(null);
            companyUser.setRecallAuditTime(null);
            companyUser.setRecallReviewerId(null);
            companyUser.setRecallAuditRemark(null);
            companyUser.setApplyTime(new java.util.Date());
            companyUser.setUpdateTime(new java.util.Date());
            
            int result = companyUserMapper.update(companyUser);
            
            if (result > 0) {
                log.info("企业注册信息更新成功: {}", existingUser.getCompanyName());
                return Result.success("更新成功");
            } else {
                return Result.error("更新失败");
            }
        } catch (Exception e) {
            log.error("更新企业注册信息失败: {}", e.getMessage(), e);
            return Result.error("更新失败: " + e.getMessage());
        }
    }
}
