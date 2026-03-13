package com.gdmu.service;

import com.gdmu.config.SmsConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SmsService {
    
    @Autowired
    private SmsConfig smsConfig;
    
    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;
    
    private static final String SMS_CODE_PREFIX = "sms:code:";
    private static final int CODE_EXPIRE_MINUTES = 5;
    
    private final Map<String, String> memoryStorage = new HashMap<>();
    private final Map<String, Long> memoryExpireTime = new HashMap<>();
    
    public String generateVerifyCode() {
        Random random = new Random();
        return String.format("%06d", random.nextInt(1000000));
    }
    
    public boolean sendVerifyCode(String phone) {
        if (!smsConfig.getEnabled()) {
            log.warn("短信服务未启用，使用模拟发送");
            return mockSendVerifyCode(phone);
        }
        
        if (!isValidPhone(phone)) {
            log.error("手机号格式不正确: {}", phone);
            return false;
        }
        
        String code = generateVerifyCode();
        String key = SMS_CODE_PREFIX + phone;
        
        try {
            if (redisTemplate != null) {
                try {
                    redisTemplate.opsForValue().set(key, code, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);
                } catch (Exception e) {
                    log.warn("Redis连接失败，降级到内存存储: {}", e.getMessage());
                    memoryStorage.put(key, code);
                    memoryExpireTime.put(key, System.currentTimeMillis() + CODE_EXPIRE_MINUTES * 60 * 1000L);
                }
            } else {
                memoryStorage.put(key, code);
                memoryExpireTime.put(key, System.currentTimeMillis() + CODE_EXPIRE_MINUTES * 60 * 1000L);
            }
            
            if (smsConfig.getDebug()) {
                log.info("短信验证码（调试模式）: 手机号={}, 验证码={}", phone, code);
                return true;
            }
            
            return sendSms(phone, code);
        } catch (Exception e) {
            log.error("发送短信验证码失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    public boolean verifyCode(String phone, String code) {
        if (phone == null || code == null) {
            return false;
        }
        
        String key = SMS_CODE_PREFIX + phone;
        
        if (redisTemplate != null) {
            try {
                String savedCode = redisTemplate.opsForValue().get(key);
                if (savedCode == null) {
                    return false;
                }
                
                if (savedCode.equals(code)) {
                    redisTemplate.delete(key);
                    return true;
                }
                
                return false;
            } catch (Exception e) {
                log.warn("Redis连接失败，降级到内存验证: {}", e.getMessage());
                String savedCode = memoryStorage.get(key);
                Long expireTime = memoryExpireTime.get(key);
                
                if (savedCode == null || expireTime == null) {
                    return false;
                }
                
                if (System.currentTimeMillis() > expireTime) {
                    memoryStorage.remove(key);
                    memoryExpireTime.remove(key);
                    return false;
                }
                
                if (savedCode.equals(code)) {
                    memoryStorage.remove(key);
                    memoryExpireTime.remove(key);
                    return true;
                }
                
                return false;
            }
        } else {
            String savedCode = memoryStorage.get(key);
            Long expireTime = memoryExpireTime.get(key);
            
            if (savedCode == null || expireTime == null) {
                return false;
            }
            
            if (System.currentTimeMillis() > expireTime) {
                memoryStorage.remove(key);
                memoryExpireTime.remove(key);
                return false;
            }
            
            if (savedCode.equals(code)) {
                memoryStorage.remove(key);
                memoryExpireTime.remove(key);
                return true;
            }
            
            return false;
        }
    }
    
    private boolean sendSms(String phone, String code) {
        try {
            log.info("发送短信: 手机号={}, 验证码={}", phone, code);
            
            return true;
        } catch (Exception e) {
            log.error("发送短信失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    private boolean mockSendVerifyCode(String phone) {
        String code = generateVerifyCode();
        String key = SMS_CODE_PREFIX + phone;
        
        if (redisTemplate != null) {
            redisTemplate.opsForValue().set(key, code, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        } else {
            memoryStorage.put(key, code);
            memoryExpireTime.put(key, System.currentTimeMillis() + CODE_EXPIRE_MINUTES * 60 * 1000L);
        }
        
        log.info("模拟发送短信验证码: 手机号={}, 验证码={}", phone, code);
        return true;
    }
    
    private boolean isValidPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return false;
        }
        return phone.matches("^1[3-9]\\d{9}$");
    }
    
    public boolean checkRateLimit(String phone) {
        if (redisTemplate == null) {
            return true;
        }
        
        String key = SMS_CODE_PREFIX + phone + ":limit";
        String count = redisTemplate.opsForValue().get(key);
        
        if (count == null) {
            redisTemplate.opsForValue().set(key, "1", 1, TimeUnit.MINUTES);
            return true;
        }
        
        int currentCount = Integer.parseInt(count);
        if (currentCount >= 3) {
            return false;
        }
        
        redisTemplate.opsForValue().increment(key);
        return true;
    }
}
