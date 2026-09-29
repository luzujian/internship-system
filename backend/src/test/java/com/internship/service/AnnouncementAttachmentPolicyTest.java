package com.internship.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.entity.User;
import com.internship.utils.CurrentHolder;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AnnouncementAttachmentPolicyTest {
    final AnnouncementAttachmentPolicy policy = new AnnouncementAttachmentPolicy("synthetic-test-secret-only");
    final ObjectMapper json = new ObjectMapper();
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp;
    Map<String,Object> legacy() throws Exception {
        var file = Map.<String,Object>of("name", "旧附件.pdf", "url", "https://fixture.invalid/old.pdf");
        var manifest = temp.resolve("manifest.json");
        java.nio.file.Files.writeString(manifest, json.writeValueAsString(List.of(Map.of(
                "announcementId", 91, "name", file.get("name"), "url", file.get("url")))));
        org.springframework.test.util.ReflectionTestUtils.setField(policy, "legacyManifest", manifest.toString());
        policy.loadLegacyManifest();
        return file;
    }
    @Test void legacyBindingCanBeRetainedAndDownloadedOnlyOnOriginalAnnouncement() throws Exception {
        var file = legacy(); String saved = json.writeValueAsString(List.of(file));
        policy.validate(saved, saved, 91L);
        policy.verifyDownload(91L, file);
        assertThrows(AccessDeniedException.class, () -> policy.verifyDownload(92L, file));
        assertThrows(AccessDeniedException.class, () -> policy.validate(saved, saved, 92L));
        assertThrows(AccessDeniedException.class, () -> policy.validate(saved, null));
        assertThrows(AccessDeniedException.class, () -> policy.validate(saved, "[]", 91L));
        assertThrows(AccessDeniedException.class, () -> policy.validate(saved, saved));
        policy.validate(json.writeValueAsString(List.of(file, uploaded())), saved, 91L);
    }
    @Test void legacyBindingCannotChangeNameUrlOrHideInvalidReceipt() throws Exception {
        var file = legacy(); String saved = json.writeValueAsString(List.of(file));
        for (String field : List.of("name", "url", "receipt")) {
            var changed = new HashMap<>(file); changed.put(field, "changed");
            String next = json.writeValueAsString(List.of(changed));
            assertThrows(AccessDeniedException.class, () -> policy.validate(next, saved, 91L));
            assertThrows(AccessDeniedException.class, () -> policy.verifyDownload(91L, changed));
        }
        var invalid = new HashMap<>(file); invalid.put("receipt", null);
        assertThrows(AccessDeniedException.class, () -> policy.verifyDownload(91L, invalid));
    }
    @Test void malformedOrMissingConfiguredManifestStopsInitialization() throws Exception {
        var path = temp.resolve("missing.json");
        org.springframework.test.util.ReflectionTestUtils.setField(policy, "legacyManifest", path.toString());
        assertThrows(IllegalStateException.class, policy::loadLegacyManifest);
        for (String input : List.of("null", "{}", "[null]", "[{\"announcementId\":0}]")) {
            java.nio.file.Files.writeString(path, input);
            assertThrows(IllegalStateException.class, policy::loadLegacyManifest);
        }
    }
    @Test void ambiguousDuplicateNamesAreRejected() throws Exception {
        var file = uploaded(); String next = json.writeValueAsString(List.of(file, file));
        assertThrows(AccessDeniedException.class, () -> policy.validate(next, null));
    }
    void actor(long id, String role) {var u = new User();u.setId(id);u.setRole(role);CurrentHolder.setUser(u);}
    @AfterEach void cleanup() {CurrentHolder.remove();}
    Map<String,Object> uploaded() {
        actor(47,"ROLE_TEACHER_COLLEGE");
        return new HashMap<>(Map.of("name","测试.txt","url","https://fixture.invalid/files/own.txt",
                "receipt",policy.issue("测试.txt","https://fixture.invalid/files/own.txt")));
    }
    @Test void ownUploadAndExistingBindingRemainValid() throws Exception {
        var file = uploaded();String saved=json.writeValueAsString(List.of(file));
        policy.validate(saved,null);assertEquals("TEACHER:47",policy.verify(file));
        actor(1,"ROLE_ADMIN");policy.validate(saved,saved);
    }
    @Test void otherActorCannotReuseReceiptEvenWithSameNumericId() throws Exception {
        var file=uploaded();String saved=json.writeValueAsString(List.of(file));
        actor(48,"ROLE_TEACHER_COLLEGE");assertThrows(AccessDeniedException.class,()->policy.validate(saved,null));
        actor(47,"ROLE_ADMIN");assertThrows(AccessDeniedException.class,()->policy.validate(saved,null));
    }
    @Test void alteredUrlNameAndSignatureAreRejected() {
        for(String key:List.of("url","name","receipt")) {var file=uploaded();file.put(key,file.get(key)+"changed");assertThrows(AccessDeniedException.class,()->policy.verify(file));}
    }
    @Test void unsignedForeignObjectsAndMalformedListsAreRejected() throws Exception {
        actor(47,"ROLE_TEACHER_COLLEGE");
        for(String input:List.of("null","{}","[null]","[\"url\"]", "[{\"name\":\"private.pdf\",\"url\":\"student/private.pdf\"}]"))
            assertThrows(AccessDeniedException.class,()->policy.validate(input,null));
        policy.validate("[]",null);
    }
    @Test void independentKeyRotationCanRetainOldReceipts() {
        var file=uploaded();
        var rotated=new AnnouncementAttachmentPolicy("new-attachment-key");
        assertThrows(AccessDeniedException.class,()->rotated.verify(file));
        org.springframework.test.util.ReflectionTestUtils.setField(rotated,"previousSecrets","synthetic-test-secret-only");
        assertEquals("TEACHER:47",rotated.verify(file));
    }
}
