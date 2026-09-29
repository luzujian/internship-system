package com.internship.service;

import com.internship.service.impl.EmailServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 邮件通道可用性实测（阶段五自动化任务的邮件动作前置条件）。
 * 验证 application.yml 中的邮件配置确实被正确绑定，isConfigured() 反映真实可用性。
 *
 * 注意：本测试需启动完整 Spring 上下文（会连接数据源），默认不纳入常规构建。
 * 手动验证：mvn test -Dtest=EmailChannelAvailabilityTest
 * 结论（2026-09-17 实测）：mailHost=smtp.exmail.qq.com, mailPort=465,
 *   mailUsername=luzujian@deepintern.wecom.work, password 已注入 → isConfigured()=true。
 * 仅读取绑定字段，不触发真实发信，无副作用。
 */
@SpringBootTest
@org.junit.jupiter.api.Disabled("需完整 Spring 上下文（连数据源），默认跳过；手动运行验证邮件通道")
class EmailChannelAvailabilityTest {

    @Autowired(required = false)
    private EmailServiceImpl emailService;

    @Test
    void emailChannelConfigurationIsBoundFromYaml() throws Exception {
        assertNotNull(emailService, "EmailServiceImpl 应能被 Spring 装配");

        Field host = EmailServiceImpl.class.getDeclaredField("mailHost");
        Field port = EmailServiceImpl.class.getDeclaredField("mailPort");
        Field username = EmailServiceImpl.class.getDeclaredField("mailUsername");
        Field password = EmailServiceImpl.class.getDeclaredField("mailPassword");
        for (Field f : new Field[]{host, port, username, password}) f.setAccessible(true);

        String h = (String) host.get(emailService);
        Integer p = (Integer) port.get(emailService);
        String u = (String) username.get(emailService);
        String pw = (String) password.get(emailService);

        System.out.println("mailHost=" + h);
        System.out.println("mailPort=" + p);
        System.out.println("mailUsername=" + u);
        System.out.println("mailPassword.length=" + (pw == null ? 0 : pw.length()));

        assertEquals("smtp.exmail.qq.com", h, "邮件主机应绑定 yml 配置");
        assertEquals(465, p);
        assertEquals("luzujian@deepintern.wecom.work", u, "邮件账号应绑定 yml 硬编码值");
        assertNotNull(pw, "MAIL_PASSWORD 应从 .env 注入");
        assertFalse(pw.isBlank(), "MAIL_PASSWORD 不应为空");
        assertTrue(emailService.isConfigured(), "四项齐全时 isConfigured() 应为 true");
    }
}
