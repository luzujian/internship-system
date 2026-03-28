package com.gdmu.controller;

import com.gdmu.service.SmsService;
import com.gdmu.entity.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/sms")
@Tag(name = "短信服务")
public class SmsController {

    @Autowired
    private SmsService smsService;

    @PostMapping("/send-verify-code")
    @Operation(summary = "发送手机验证码")
    public Result sendVerifyCode(@RequestBody Map<String, String> request) {
        String phone = request.get("phone");
        if (phone == null || phone.isEmpty()) {
            return Result.error("手机号不能为空");
        }

        if (!smsService.checkRateLimit(phone)) {
            return Result.error("发送过于频繁，请稍后再试");
        }

        boolean success = smsService.sendVerifyCode(phone);
        if (success) {
            return Result.success("验证码已发送");
        } else {
            return Result.error("验证码发送失败，请稍后再试");
        }
    }
}
