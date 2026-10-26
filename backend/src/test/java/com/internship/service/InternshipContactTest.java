package com.internship.service;

import com.internship.entity.CompanyUser;
import com.internship.entity.Position;
import com.internship.entity.dto.InternshipContact;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InternshipContactTest {
    private CompanyUser company() {
        CompanyUser company = new CompanyUser();
        company.setId(20L); company.setContactPerson("企业联系人");
        company.setContactPhone("02088888888"); company.setPhone("13333333333");
        return company;
    }

    private Position position() {
        Position position = new Position();
        position.setCompanyId(20L); position.setContactPerson("岗位负责人");
        position.setContactPhone("13800000000");
        return position;
    }

    @Test void linkedPositionUsesItsContactAsAPair() {
        assertEquals(new InternshipContact("岗位负责人", "13800000000"),
                InternshipContact.resolve(20L, company(), position()));
    }

    @Test void unlinkedPositionUsesCompanyBusinessContact() {
        assertEquals(new InternshipContact("企业联系人", "02088888888"),
                InternshipContact.resolve(20L, company(), null));
    }

    @Test void anotherCompanyPositionCannotExposeItsContact() {
        Position foreign = position(); foreign.setCompanyId(99L);
        assertEquals(new InternshipContact("企业联系人", "02088888888"),
                InternshipContact.resolve(20L, company(), foreign));
    }

    @Test void missingCompanyCanStillUseAnOwnedPositionContact() {
        assertEquals(new InternshipContact("岗位负责人", "13800000000"),
                InternshipContact.resolve(20L, null, position()));
    }

    @Test void missingPositionPhoneDoesNotMixInAnotherContactsPhone() {
        Position position = position(); position.setContactPhone(null);
        assertEquals(new InternshipContact("岗位负责人", null),
                InternshipContact.resolve(20L, company(), position));
    }

    @Test void unavailableOrForeignCompanyReturnsNoContact() {
        assertEquals(new InternshipContact(null, null), InternshipContact.resolve(99L, company(), null));
        assertEquals(new InternshipContact(null, null), InternshipContact.resolve(20L, null, null));
    }
}
