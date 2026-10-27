package com.internship.controller;

import com.internship.entity.Result;
import com.internship.service.OnlineUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/online-users")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('log:login:view')")
public class OnlineUserController {
    private final OnlineUserService service;
    public OnlineUserController(OnlineUserService service) { this.service = service; }

    @GetMapping("/summary")
    public ResponseEntity<Result> summary() {
        try { return ResponseEntity.ok(Result.success(service.summary())); }
        catch (Exception e) { return unavailable(); }
    }

    @GetMapping
    public ResponseEntity<Result> list(@RequestParam(defaultValue = "") String keyword,
                       @RequestParam(defaultValue = "") String role,
                       @RequestParam(defaultValue = "") String status,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "20") int pageSize) {
        try { return ResponseEntity.ok(Result.success(service.list(keyword, role, status, page, pageSize))); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Result.error(e.getMessage())); }
        catch (Exception e) { return unavailable(); }
    }

    private ResponseEntity<Result> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new Result(503, "在线状态暂时不可用", null));
    }
}
