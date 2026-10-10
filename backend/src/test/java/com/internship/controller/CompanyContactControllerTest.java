package com.internship.controller;

import com.internship.entity.CompanyContact;
import com.internship.entity.User;
import com.internship.exception.GlobalExceptionHandler;
import com.internship.service.CompanyContactService;
import com.internship.utils.CurrentHolder;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CompanyContactControllerTest {
    private final CompanyContactService service = mock(CompanyContactService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CompanyContactController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    @AfterEach void clear() { CurrentHolder.remove(); }
    private void identity(String role) { User user = new User(); user.setId(100L); user.setRole(role); CurrentHolder.setUser(user); }
    @Test void deniesMissingOrWrongRoleForContactReadsAndWrites() throws Exception {
        mvc.perform(get("/api/company/contacts")).andExpect(status().isForbidden());
        identity("ROLE_STUDENT");
        mvc.perform(delete("/api/company/contacts/10")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void derivesCompanyFromSessionAndUsesPathIdRatherThanClientBodyIdentity() throws Exception {
        identity("ROLE_COMPANY");
        mvc.perform(put("/api/company/contacts/10").contentType("application/json").content("""
            {"id":999,"companyId":200,"name":"联系人甲","phone":"13800000001","email":"one@example.test"}
            """)).andExpect(status().isOk());
        verify(service).save(eq(100L), eq(10L), any(CompanyContact.class));
        mvc.perform(get("/api/company/contacts?companyId=200")).andExpect(status().isOk());
        verify(service).list(100L);
    }
    @Test void rejectsInvalidPhoneAndEmailBeforeBusinessWrites() throws Exception {
        identity("ROLE_COMPANY");
        mvc.perform(post("/api/company/contacts").contentType("application/json").content("""
            {"name":"联系人甲","phone":"x","email":"bad"}
            """)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
