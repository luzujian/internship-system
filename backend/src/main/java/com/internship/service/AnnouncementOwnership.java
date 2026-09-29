package com.internship.service;

import com.internship.entity.Announcement;
import com.internship.entity.User;
import com.internship.utils.CurrentHolder;
import org.springframework.security.access.AccessDeniedException;
import java.util.Set;

/** Ownership is an account identity; publisher names remain presentation metadata. */
public final class AnnouncementOwnership {
    private AnnouncementOwnership() { }
    private static final Set<String> TEACHERS = Set.of("ROLE_TEACHER_COLLEGE", "ROLE_TEACHER_DEPARTMENT", "ROLE_TEACHER_COUNSELOR");
    public static void bindCreator(Announcement announcement) {
        User actor = CurrentHolder.getUser();
        if (actor == null || actor.getId() == null || actor.getId() <= 0 || actor.getRole() == null
                || !("ROLE_ADMIN".equals(actor.getRole()) || TEACHERS.contains(actor.getRole()))) {
            throw new AccessDeniedException("公告发布身份不可用");
        }
        announcement.setPublisherUserId(actor.getId());
        announcement.setPublisherUserType("ROLE_ADMIN".equals(actor.getRole()) ? "ADMIN" : "TEACHER");
    }
    public static boolean owns(Announcement announcement, User actor) {
        if (announcement == null || actor == null || actor.getRole() == null || actor.getId() == null
                || announcement.getPublisherUserId() == null) return false;
        String type = "ROLE_ADMIN".equals(actor.getRole()) ? "ADMIN" : TEACHERS.contains(actor.getRole()) ? "TEACHER" : null;
        return type != null && type.equals(announcement.getPublisherUserType())
                && actor.getId().equals(announcement.getPublisherUserId());
    }
}
