package com.gdmu.service;

import com.gdmu.config.SmsConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SmsService {

    @Autowired
    private SmsConfig smsConfig;

    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;

    private static final String RATE_LIMIT_PREFIX = "sms:limit:";
    private static final int RATE_LIMIT_SECONDS = 60;
    private static final int MAX_REQUESTS_PER_MINUTE = 3;

    /**
     * 发送短信验证码（使用阿里云短信认证服务API）
     */
    public boolean sendVerifyCode(String phone) {
        if (!smsConfig.getEnabled()) {
            log.warn("短信服务未启用，使用模拟发送");
            return mockSendVerifyCode(phone);
        }

        if (!isValidPhone(phone)) {
            log.error("手机号格式不正确: {}", phone);
            return false;
        }

        try {
            if (smsConfig.getDebug()) {
                // 调试模式：模拟发送，不调用真实API
                String code = generateVerifyCode();
                log.info("短信验证码（调试模式）: 手机号={}, 验证码={}", phone, code);
                return true;
            }

            return sendSmsVerifyCode(phone);
        } catch (Exception e) {
            log.error("发送短信验证码失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 校验短信验证码（使用阿里云短信认证服务API）
     */
    public boolean verifyCode(String phone, String code) {
        if (phone == null || code == null) {
            return false;
        }

        if (!smsConfig.getEnabled()) {
            // 调试模式跳过验证
            return true;
        }

        try {
            return checkSmsVerifyCode(phone, code);
        } catch (Exception e) {
            log.error("校验短信验证码失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 调用 SendSmsVerifyCode API 发送验证码
     */
    private boolean sendSmsVerifyCode(String phone) throws Exception {
        com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config()
            .setAccessKeyId(smsConfig.getAccessKeyId())
            .setAccessKeySecret(smsConfig.getAccessKeySecret());
        config.endpoint = "dypnsapi.aliyuncs.com";

        com.aliyun.dypnsapi20170525.Client client = new com.aliyun.dypnsapi20170525.Client(config);

        com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest request =
            new com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest()
                .setSchemeName(smsConfig.getSchemeName())
                .setPhoneNumber(phone)
                .setSignName(smsConfig.getSignName())
                .setTemplateCode(smsConfig.getTemplateCode())
                .setTemplateParam("{\"code\":\"##code##\",\"min\":\"5\"}")
                .setCodeLength(smsConfig.getCodeLength() != null ? Long.valueOf(smsConfig.getCodeLength()) : 6L)
                .setValidTime(smsConfig.getValidTime() != null ? Long.valueOf(smsConfig.getValidTime()) : 300L);

        com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponse response = client.sendSmsVerifyCode(request);

        String apiCode = response.getBody().getCode();
        if ("OK".equals(apiCode)) {
            log.info("短信验证码发送成功: 手机号={}, 请求ID={}", phone, response.getBody().getRequestId());
            return true;
        } else {
            log.error("短信验证码发送失败: 手机号={}, 错误码={}, 错误信息={}", phone, apiCode, response.getBody().getMessage());
            return false;
        }
    }

    /**
     * 调用 CheckSmsVerifyCode API 校验验证码
     */
    private boolean checkSmsVerifyCode(String phone, String code) throws Exception {
        com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config()
            .setAccessKeyId(smsConfig.getAccessKeyId())
            .setAccessKeySecret(smsConfig.getAccessKeySecret());
        config.endpoint = "dypnsapi.aliyuncs.com";

        com.aliyun.dypnsapi20170525.Client client = new com.aliyun.dypnsapi20170525.Client(config);

        com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeRequest request =
            new com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeRequest()
                .setSchemeName(smsConfig.getSchemeName())
                .setPhoneNumber(phone)
                .setVerifyCode(code);

        com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeResponse response = client.checkSmsVerifyCode(request);

        // 先用 code 字段判断，OK 表示验证通过
        String apiCode = response.getBody().getCode();
        if ("OK".equals(apiCode)) {
            log.info("短信验证码校验成功: 手机号={}", phone);
            return true;
        } else {
            log.warn("短信验证码校验失败: 手机号={}, 错误码={}, 错误信息={}", phone, apiCode, response.getBody().getMessage());
            return false;
        }
    }

    /**
     * 模拟发送（调试模式）
     */
    private boolean mockSendVerifyCode(String phone) {
        log.info("模拟发送短信验证码: 手机号={}", phone);
        return true;
    }

    /**
     * 生成6位数字验证码
     */
    private String generateVerifyCode() {
        int code = (int) ((Math.random() * 9 + 1) * 100000);
        return String.valueOf(code);
    }

    /**
     * 手机号格式校验
     */
    private boolean isValidPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return false;
        }
        return phone.matches("^1[3-9]\\d{9}$");
    }

    /**
     * 限流检查
     */
    public boolean checkRateLimit(String phone) {
        if (redisTemplate == null) {
            return true;
        }

        String key = RATE_LIMIT_PREFIX + phone;
        try {
            String count = redisTemplate.opsForValue().get(key);
            if (count == null) {
                redisTemplate.opsForValue().set(key, "1", RATE_LIMIT_SECONDS, TimeUnit.SECONDS);
                return true;
            }
            int currentCount = Integer.parseInt(count);
            if (currentCount >= MAX_REQUESTS_PER_MINUTE) {
                return false;
            }
            redisTemplate.opsForValue().increment(key);
            return true;
        } catch (Exception e) {
            log.warn("限流检查失败: {}", e.getMessage());
            return true;
        }
    }
}
