package com.internship.service;

import com.internship.entity.CompanyContact;
import com.internship.entity.CompanyUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 新企业的联系人、账号绑定及首位招聘联系人同时保存。 */
@Service
@RequiredArgsConstructor
public class CompanyRegistrationService {
    private final CompanyUserService companyUserService;
    private final CompanyContactService companyContactService;

    @Transactional(rollbackFor = Exception.class)
    public int register(CompanyUser company) {
        company.setContactPerson(company.getContactPerson().trim());
        company.setContactPhone(company.getContactPhone().replaceAll("\\s+", ""));
        company.setContactEmail(company.getContactEmail().trim());
        company.setPhone(company.getContactPhone());
        company.setEmail(company.getContactEmail());
        int result = companyUserService.insert(company);
        if (result > 0) {
            CompanyContact contact = new CompanyContact();
            contact.setName(company.getContactPerson());
            contact.setPhone(company.getContactPhone());
            contact.setEmail(company.getContactEmail());
            companyContactService.save(company.getId(), null, contact);
        }
        return result;
    }
}
