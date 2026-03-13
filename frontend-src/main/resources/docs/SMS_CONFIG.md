# 短信服务配置说明

## 功能说明

系统已实现完整的手机号验证码功能，支持两种模式：

### 1. 模拟模式（默认）
- 短信服务未启用时使用
- 验证码会在控制台日志中输出
- 适合开发和测试环境

### 2. 真实模式
- 需要配置真实的短信服务
- 支持阿里云短信、腾讯云短信等
- 适合生产环境

## 配置步骤

### 方式一：使用阿里云短信服务

1. 登录阿里云控制台，开通短信服务
2. 创建签名和模板
3. 获取AccessKey ID和AccessKey Secret
4. 修改`application.yml`配置：

```yaml
sms:
  enabled: true
  debug: false
  access-key-id: 你的AccessKey ID
  access-key-secret: 你的AccessKey Secret
  sign-name: 实习管理系统
  template-code: SMS_123456789
```

### 方式二：使用腾讯云短信服务

1. 登录腾讯云控制台，开通短信服务
2. 创建签名和模板
3. 获取Secret ID和Secret Key
4. 修改`SmsService.java`中的`sendSms`方法，集成腾讯云SDK

### 方式三：使用其他短信服务

根据短信服务商提供的SDK，修改`SmsService.java`中的`sendSms`方法。

## Redis配置

验证码存储在Redis中，需要配置Redis连接：

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      database: 0
      timeout: 3000
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0
          max-wait: -1ms
```

## 功能特性

1. **验证码生成**：6位数字随机验证码
2. **有效期**：5分钟
3. **频率限制**：每分钟最多发送3次
4. **验证码验证**：验证成功后自动删除
5. **调试模式**：开发时可在控制台查看验证码

## 测试方法

### 开发环境测试

1. 确保Redis服务已启动
2. 保持`sms.enabled: false`（默认）
3. 在注册页面输入手机号
4. 点击"发送验证码"
5. 在控制台日志中查看验证码
6. 输入验证码完成注册

### 生产环境测试

1. 配置真实的短信服务参数
2. 设置`sms.enabled: true`
3. 设置`sms.debug: false`
4. 测试短信发送功能

## 注意事项

1. 短信服务需要实名认证
2. 短信签名需要审核
3. 短信模板需要审核
4. 注意短信费用控制
5. 生产环境务必关闭调试模式
