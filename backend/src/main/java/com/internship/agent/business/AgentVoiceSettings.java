package com.internship.agent.business;

import com.internship.entity.SystemConfig;
import com.internship.mapper.AIModelMapper;
import com.internship.service.SystemConfigService;
import java.net.URI;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Voice selection references the same masked model registry; no secrets in system_config. */
@Service
public class AgentVoiceSettings {
    private static final String KEY="agent.voice.model-id";
    private final AIModelMapper models;
    private final SystemConfigService configs;
    public AgentVoiceSettings(AIModelMapper models,SystemConfigService configs){this.models=models;this.configs=configs;}
    public record Connection(String model,String endpoint,String apiKey) {}
    public long selectedId(){
        var config=configs.findByConfigKey(KEY);
        return config==null?0:Long.parseLong(config.getConfigValue());
    }
    public Map<String,Object> selection(){
        long id=selectedId();var row=id==0?null:models.findById(id);
        return Map.of("modelId",id,"protocol",row!=null && com.internship.util.AIModelCapabilities.dictation(row.getModelCode())?"QWEN_OMNI_DICTATION":"QWEN_ASR_REALTIME");
    }
    public Connection resolve(){
        var config=configs.findByConfigKey(KEY);
        if(config==null)return null;
        long id=Long.parseLong(config.getConfigValue());
        if(id==0)throw unavailable("管理员尚未选择语音识别模型，可以继续文字输入。");
        return connection(id);
    }
    private Connection connection(long id){
        var row=models.findById(id);
        if(row==null || !Integer.valueOf(1).equals(row.getStatus()) || Integer.valueOf(1).equals(row.getDeleted()))
            throw unavailable("所选语音模型已停用或删除，请管理员重新选择。");
        String endpoint=row.getApiEndpoint();
        if(endpoint==null || row.getApiKey()==null || row.getApiKey().isBlank())throw unavailable("请先在模型配置中填写实时语音接口地址和密钥。");
        URI uri=URI.create(endpoint);
        if(com.internship.util.AIModelCapabilities.dictation(row.getModelCode())) {
            if(!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getQuery()!=null || uri.getUserInfo()!=null || uri.getFragment()!=null || uri.getPath().endsWith("/realtime"))
                throw unavailable("听写模型需要 HTTPS 对话接口地址。");
            return new Connection(row.getModelCode(),endpoint,row.getApiKey());
        }
        if(!com.internship.util.AIModelCapabilities.realtime(row.getModelCode()))
            throw unavailable("该模型尚不支持语音识别接入，请选择已支持的模型。");
        if(!"https".equals(uri.getScheme()) || uri.getHost()==null || !uri.getPath().endsWith("/realtime") || uri.getQuery()!=null || uri.getUserInfo()!=null || uri.getFragment()!=null)
            throw unavailable("当前语音接入支持 Qwen 实时识别协议；请填写以 https:// 开头、以 /realtime 结尾的接口地址，不能使用对话接口。");
        if(row.getModelCode()==null || !row.getModelCode().matches("[a-zA-Z0-9._-]+"))throw unavailable("语音模型名称无效。");
        return new Connection(row.getModelCode(),"wss"+endpoint.substring(5),row.getApiKey());
    }
    @Transactional public Map<String,Object> save(long id){
        if(id<0)throw unavailable("模型编号无效。");
        if(id>0)connection(id);
        var config=configs.findByConfigKey(KEY);boolean insert=config==null;
        if(insert){config=new SystemConfig();config.setConfigKey(KEY);config.setConfigName("AI 语音识别模型");config.setConfigType("number");}
        config.setConfigValue(Long.toString(id));config.setStatus(1);
        if(insert)configs.insert(config);else configs.update(config);
        return selection();
    }
    private static AgentException unavailable(String text){return new AgentException("VOICE_UNAVAILABLE",text,400);}
}
