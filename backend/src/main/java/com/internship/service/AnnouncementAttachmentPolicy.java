package com.internship.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.utils.CurrentHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Server-issued proof binds uploaded bytes to an actor, name, URL and announcement use. */
@Component
public class AnnouncementAttachmentPolicy {
    private final String secret;
    @Value("${announcement.attachments.previous-signing-keys:}")
    private String previousSecrets = "";
    private final ObjectMapper json = new ObjectMapper();
    @Value("${announcement.attachments.legacy-manifest:}")
    private String legacyManifest = "";
    private Set<LegacyBinding> legacyBindings = Set.of();
    private record LegacyBinding(Long announcementId, String name, String url) {}
    public AnnouncementAttachmentPolicy(@Value("${announcement.attachments.signing-key:}") String secret) { this.secret = secret == null ? "" : secret.trim(); }

    @jakarta.annotation.PostConstruct
    void loadLegacyManifest() {
        if (legacyManifest.isBlank()) return;
        try {
            List<LegacyBinding> entries = json.readValue(java.nio.file.Files.readString(java.nio.file.Path.of(legacyManifest)),
                    new com.fasterxml.jackson.core.type.TypeReference<>() {});
            if (entries == null || entries.stream().anyMatch(e -> e == null || e.announcementId() == null
                    || e.announcementId() <= 0 || e.name() == null || e.name().isBlank() || e.url() == null || e.url().isBlank()))
                throw new IllegalArgumentException("Invalid legacy attachment binding");
            legacyBindings = Set.copyOf(entries);
        } catch (Exception e) { throw new IllegalStateException("历史公告附件清单加载失败", e); }
    }

    private boolean isLegacy(Long id, Map<String,Object> item) {
        return id != null && !item.containsKey("receipt") && item.get("name") instanceof String name
                && item.get("url") instanceof String url && legacyBindings.contains(new LegacyBinding(id, name, url));
    }

    public void verifyDownload(Long announcementId, Map<String,Object> attachment) {
        if (!isLegacy(announcementId, attachment)) verify(attachment);
    }

    public String issue(String name, String url) {
        try {
            String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsBytes(
                    List.of("announcement-v1", actor(), name, url)));
            return payload + "." + sign(payload);
        } catch (Exception e) { throw new IllegalStateException("附件凭据生成失败", e); }
    }

    private String actor() {
        String role = CurrentHolder.getUserRole();
        Long id = CurrentHolder.getUserId();
        if (id == null || id <= 0 || !("ROLE_ADMIN".equals(role)
                || Set.of("ROLE_TEACHER_COLLEGE", "ROLE_TEACHER_DEPARTMENT", "ROLE_TEACHER_COUNSELOR").contains(role)))
            throw new AccessDeniedException("无权上传公告附件");
        return ("ROLE_ADMIN".equals(role) ? "ADMIN:" : "TEACHER:") + id;
    }

    private String sign(String payload) throws Exception {
        if (secret == null || secret.isBlank()) throw new IllegalStateException("请配置公告附件签名密钥");
        return sign(payload, secret);
    }

    private String sign(String payload, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    public String verify(Map<String, Object> attachment) {
        try {
            String receipt = (String) attachment.get("receipt");
            String[] parts = receipt.split("\\.", -1);
            if (parts.length != 2) throw new IllegalArgumentException();
            boolean valid = false;
            List<String> keys = new ArrayList<>(List.of(previousSecrets.split(",")));
            keys.add(secret);
            for (String key : keys) {
                if (key != null && !key.isBlank() && MessageDigest.isEqual(sign(parts[0], key.trim()).getBytes(StandardCharsets.US_ASCII), parts[1].getBytes(StandardCharsets.US_ASCII))) valid = true;
            }
            if (!valid) throw new IllegalArgumentException();
            List<?> payload = json.readValue(Base64.getUrlDecoder().decode(parts[0]), List.class);
            if (payload.size() != 4 || !"announcement-v1".equals(payload.get(0))
                    || !Objects.equals(payload.get(2), attachment.get("name")) || !Objects.equals(payload.get(3), attachment.get("url"))) throw new IllegalArgumentException();
            return (String) payload.get(1);
        } catch (Exception e) { throw new AccessDeniedException("附件来源不可验证，请重新上传", e); }
    }

    public void validate(String attachments, String previous) {
        validate(attachments, previous, null);
    }

    public void validate(String attachments, String previous, Long existingId) {
        if (attachments == null || attachments.isBlank()) return;
        try {
            List<Map<String,Object>> items = json.readValue(attachments, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            List<Map<String,Object>> old = previous == null || previous.isBlank() ? List.of()
                    : json.readValue(previous, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            Set<String> names = new HashSet<>();
            for (Map<String,Object> item : items) {
                if (!(item.get("name") instanceof String name) || name.isBlank() || !names.add(name))
                    throw new AccessDeniedException("附件名称不能为空或重复");
                if (isLegacy(existingId, item) && old.stream().anyMatch(prior ->
                        Objects.equals(prior.get("name"), item.get("name")) && Objects.equals(prior.get("url"), item.get("url")))) continue;
                String uploader = verify(item);
                boolean unchanged = old.stream().anyMatch(prior -> Objects.equals(prior.get("receipt"), item.get("receipt")));
                if (!unchanged && !actor().equals(uploader)) throw new AccessDeniedException("不能绑定他人上传的附件");
            }
        } catch (AccessDeniedException e) { throw e;
        } catch (Exception e) { throw new AccessDeniedException("附件格式无效", e); }
    }
}
