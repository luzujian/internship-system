package com.internship.service.automation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.config.LocalRuntimeGuard;
import com.internship.service.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.util.Map;

/** Local acceptance uses an isolated mailbox. It never contacts production SMTP. */
@Service
public class AutomationMailTransport {
    private final LocalRuntimeGuard local;
    private final EmailService email;
    private final ObjectMapper json;
    private final boolean sink;
    public record Outcome(String status, String detail, boolean retryable) {}
    public AutomationMailTransport(LocalRuntimeGuard local,EmailService email,ObjectMapper json,
            @Value("${teacher.auto-task.local-mail-sink:false}") boolean sink){this.local=local;this.email=email;this.json=json;this.sink=sink;}
    /** 本地模式优先隔离邮箱；关闭隔离时回退到真实邮件（受 mail.allow-local-send 开关约束，见 EmailServiceImpl.isConfigured）。 */
    public boolean available(){return local.isLocal()?(sink||email.isConfigured()):email.isConfigured();}
    public boolean isolated(){return local.isLocal()&&sink;}
    public Outcome send(String deliveryId,String recipient,String subject,String body){
        return send(deliveryId, recipient, subject, body, "DeepIntern");
    }
    public Outcome send(String deliveryId,String recipient,String subject,String body,String senderName){
        String sender = (senderName == null || senderName.isBlank()) ? "DeepIntern" : senderName.trim();
        if(!available())return new Outcome("FAILED","邮件服务未配置或当前环境禁止外发。",false);
        if(recipient==null||!recipient.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))return new Outcome("FAILED","学生未绑定有效邮箱。",false);
        if(isolated()){
            try{
                Path dir=Path.of(System.getProperty("user.dir"),"target","automation-mail");Files.createDirectories(dir);
                Path target=dir.resolve(deliveryId+".json");
                // A receipt named by immutable delivery ID also makes the test transport idempotent.
                if(!Files.exists(target))Files.writeString(target,json.writeValueAsString(Map.of("to",recipient,"subject",subject,"body",body,"sender",sender,"deliveryId",deliveryId)),StandardOpenOption.CREATE_NEW);
                return new Outcome("SENT","已投递到本地隔离测试邮箱，未向真实学生发送。",false);
            }catch(FileAlreadyExistsException alreadyAccepted){return new Outcome("SENT","已投递到本地隔离测试邮箱，未向真实学生发送。",false);}
            catch(Exception e){return new Outcome("FAILED","本地测试邮箱暂不可写。",true);}
        }
        EmailService.DeliveryOutcome result=email.sendWithReceipt(recipient,subject,body,deliveryId,sender);
        return new Outcome(result.status(),result.detail(),result.retryable());
    }
}