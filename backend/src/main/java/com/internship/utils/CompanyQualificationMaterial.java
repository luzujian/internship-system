package com.internship.utils;

import com.internship.entity.CompanyUser;

/** Select only the three documented qualification fields from a loaded company record. */
public final class CompanyQualificationMaterial {
    private CompanyQualificationMaterial() { }

    public static String storedUrl(CompanyUser company, String kind, int index) {
        if (company == null || kind == null || index < 0) return null;
        return switch (kind) {
            case "businessLicense" -> index == 0 ? company.getBusinessLicense() : null;
            case "plaquePhoto" -> index == 0 ? company.getPlaquePhoto() : null;
            case "legalIdCard" -> {
                if (index > 1 || company.getLegalIdCard() == null) yield null;
                String[] cards = company.getLegalIdCard().split(",", -1);
                yield index < cards.length ? cards[index].trim() : null;
            }
            default -> null;
        };
    }
}
