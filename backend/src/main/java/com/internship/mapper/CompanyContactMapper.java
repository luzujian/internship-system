package com.internship.mapper;

import com.internship.entity.CompanyContact;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface CompanyContactMapper {
    @Select("SELECT id FROM company_users WHERE id = #{companyId} FOR UPDATE")
    Long lockCompany(Long companyId);

    @Select("SELECT cc.*, (SELECT COUNT(*) FROM position p WHERE p.contact_id = cc.id) AS position_count " +
            "FROM company_contact cc WHERE cc.company_id = #{companyId} ORDER BY cc.id")
    List<CompanyContact> findByCompanyId(Long companyId);

    @Select("SELECT * FROM company_contact WHERE id = #{id} AND company_id = #{companyId}")
    CompanyContact findOwned(@Param("id") Long id, @Param("companyId") Long companyId);

    @Insert("INSERT INTO company_contact (company_id, name, phone, email) VALUES (#{companyId}, #{name}, #{phone}, #{email})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CompanyContact contact);

    @Update("UPDATE company_contact SET name = #{name}, phone = #{phone}, email = #{email}, update_time = NOW() " +
            "WHERE id = #{id} AND company_id = #{companyId}")
    int update(CompanyContact contact);

    @Select("SELECT COUNT(*) FROM position WHERE contact_id = #{id}")
    long countPositions(Long id);

    @Delete("DELETE FROM company_contact WHERE id = #{id} AND company_id = #{companyId}")
    int delete(@Param("id") Long id, @Param("companyId") Long companyId);
}
