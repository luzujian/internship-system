package com.internship.utils;

import com.internship.entity.CompanyUser;
import com.internship.entity.PageResult;
import com.internship.entity.StudentApplication;
import org.springframework.beans.BeanUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Preserve material presence in API responses without returning stored OSS URLs. */
public final class MaterialMetadataRedactor {
    private MaterialMetadataRedactor() { }

    public static StudentApplication studentApplication(StudentApplication source) {
        if (source == null) return null;
        StudentApplication copy = new StudentApplication();
        BeanUtils.copyProperties(source, copy);
        if (source.getMaterials() != null) {
            Map<String, String> visible = new LinkedHashMap<>();
            source.getMaterials().forEach((key, url) -> visible.put(key, presence(url)));
            copy.setMaterials(visible);
        }
        return copy;
    }

    public static CompanyUser company(CompanyUser source) {
        if (source == null) return null;
        CompanyUser copy = new CompanyUser();
        BeanUtils.copyProperties(source, copy);
        copy.setBusinessLicense(presence(source.getBusinessLicense()));
        copy.setPlaquePhoto(presence(source.getPlaquePhoto()));
        if (source.getLegalIdCard() != null) {
            String[] cards = source.getLegalIdCard().split(",", -1);
            for (int i = 0; i < cards.length; i++) cards[i] = presence(cards[i]);
            copy.setLegalIdCard(String.join(",", cards));
        }
        return copy;
    }

    public static PageResult<StudentApplication> studentPage(PageResult<StudentApplication> page) {
        if (page == null) return null;
        List<StudentApplication> rows = page.getRows() == null ? List.of()
                : page.getRows().stream().map(MaterialMetadataRedactor::studentApplication).toList();
        return PageResult.build(page.getTotal(), rows, page.getPages(), page.getCurrent(), page.getPageSize());
    }

    public static PageResult<CompanyUser> companyPage(PageResult<CompanyUser> page) {
        if (page == null) return null;
        List<CompanyUser> rows = page.getRows() == null ? List.of()
                : page.getRows().stream().map(MaterialMetadataRedactor::company).toList();
        return PageResult.build(page.getTotal(), rows, page.getPages(), page.getCurrent(), page.getPageSize());
    }

    private static String presence(String value) {
        if (value == null || value.isBlank()) return value;
        java.util.regex.Matcher extension = java.util.regex.Pattern.compile("(?i)\\.([a-z0-9]{1,8})$").matcher(value);
        return extension.find() ? "uploaded." + extension.group(1).toLowerCase(java.util.Locale.ROOT) : "uploaded";
    }
}
