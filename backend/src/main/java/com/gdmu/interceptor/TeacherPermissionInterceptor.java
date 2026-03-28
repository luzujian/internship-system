package com.gdmu.interceptor;

import com.gdmu.anno.RequireTeacherPermission;
import com.gdmu.entity.TeacherPermission;
import com.gdmu.entity.TeacherRole;
import com.gdmu.entity.TeacherUser;
import com.gdmu.service.TeacherPermissionService;
import com.gdmu.service.TeacherRoleService;
import com.gdmu.service.TeacherUserService;
import com.gdmu.utils.CurrentHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

@Slf4j
@Component
public class TeacherPermissionInterceptor implements HandlerInterceptor {
    
    @Autowired
    private TeacherRoleService teacherRoleService;
    
    @Autowired
    private TeacherPermissionService teacherPermissionService;
    
    @Autowired
    private TeacherUserService teacherUserService;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        RequireTeacherPermission permissionAnnotation = handlerMethod.getMethodAnnotation(RequireTeacherPermission.class);
        
        if (permissionAnnotation == null) {
            return true;
        }
        
        String[] requiredPermissions = permissionAnnotation.value();
        if (requiredPermissions.length == 0) {
            return true;
        }
        
        String username = CurrentHolder.getUser() != null ? CurrentHolder.getUser().getUsername() : null;
        if (username == null) {
            log.warn("用户未登录，拒绝访问");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"用户未登录\"}");
            return false;
        }
        
        TeacherUser teacherUser = teacherUserService.findByTeacherUserId(username);
        if (teacherUser == null) {
            log.warn("教师用户不存在: {}", username);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"教师用户不存在\"}");
            return false;
        }
        
        if (teacherUser.getRole() == null || !teacherUser.getRole().equals("ROLE_TEACHER")) {
            log.warn("用户不是教师角色: {}", username);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"用户不是教师角色\"}");
            return false;
        }
        
        String teacherType = teacherUser.getTeacherType();
        if (teacherType == null) {
            log.warn("教师类型未设置: {}", username);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"教师类型未设置\"}");
            return false;
        }
        
        TeacherRole teacherRole = teacherRoleService.findByRoleCode(teacherType);
        if (teacherRole == null) {
            log.warn("教师角色不存在: {}", teacherType);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"教师角色不存在\"}");
            return false;
        }
        
        List<String> userPermissions = teacherPermissionService.getPermissionCodesByRoleId(teacherRole.getId());
        
        for (String requiredPermission : requiredPermissions) {
            if (!userPermissions.contains(requiredPermission)) {
                log.warn("教师 {} 缺少权限: {}", username, requiredPermission);
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":403,\"message\":\"" + permissionAnnotation.message() + "\"}");
                return false;
            }
        }
        
        return true;
    }
}
