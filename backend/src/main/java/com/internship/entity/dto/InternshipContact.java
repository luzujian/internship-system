package com.internship.entity.dto;

import com.internship.entity.CompanyUser;
import com.internship.entity.Position;

/** 岗位查询已包含其联系人；未指定岗位联系人时，Mapper 返回企业基础联系人。 */
public record InternshipContact(String name, String phone) {
    public static InternshipContact resolve(Long companyId, CompanyUser company, Position position) {
        if (companyId != null && position != null && companyId.equals(position.getCompanyId())) {
            return new InternshipContact(position.getContactPerson(), position.getContactPhone());
        }
        if (companyId != null && company != null && companyId.equals(company.getId())) {
            return new InternshipContact(company.getContactPerson(), company.getContactPhone());
        }
        return new InternshipContact(null, null);
    }
}
