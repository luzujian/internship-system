package com.gdmu.service.impl;

import com.gdmu.entity.AIModel;
import com.gdmu.service.AIModelService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AIModelServiceImpl 集成测试
 * 验证编辑 AI 模型时的最大 Token 数和温度参数编辑功能是否正常
 */
@SpringBootTest
public class AIModelEditFunctionalityTest {

    private static final Logger log = LoggerFactory.getLogger(AIModelEditFunctionalityTest.class);

    @Autowired
    private AIModelService aiModelService;

    /**
     * 测试更新 AI 模型的 maxTokens 字段
     */
    @Test
    public void testUpdateMaxTokens() {
        Long modelId = 1L;
        Integer newMaxTokens = 65536;

        // 获取当前模型信息
        AIModel existingModel = aiModelService.findById(modelId);
        assertNotNull(existingModel, "模型 ID 1 应该存在");

        log.info("更新前的 maxTokens: {}", existingModel.getMaxTokens());

        // 更新 maxTokens
        existingModel.setMaxTokens(newMaxTokens);
        int result = aiModelService.update(existingModel);

        // 验证更新成功
        assertTrue(result > 0, "更新操作应该返回成功");

        // 验证数据库中的值已更新
        AIModel updatedModel = aiModelService.findById(modelId);
        assertNotNull(updatedModel);
        assertEquals(newMaxTokens, updatedModel.getMaxTokens(), "maxTokens 应该被更新为 " + newMaxTokens);

        log.info("✅ testUpdateMaxTokens 测试通过！");
        log.info("   更新后的 maxTokens: {}", updatedModel.getMaxTokens());
    }

    /**
     * 测试更新 AI 模型的 temperature 字段
     */
    @Test
    public void testUpdateTemperature() {
        Long modelId = 1L;
        BigDecimal newTemperature = new BigDecimal("1.50");

        // 获取当前模型信息
        AIModel existingModel = aiModelService.findById(modelId);
        assertNotNull(existingModel, "模型 ID 1 应该存在");

        log.info("更新前的 temperature: {}", existingModel.getTemperature());

        // 更新 temperature
        existingModel.setTemperature(newTemperature);
        int result = aiModelService.update(existingModel);

        // 验证更新成功
        assertTrue(result > 0, "更新操作应该返回成功");

        // 验证数据库中的值已更新
        AIModel updatedModel = aiModelService.findById(modelId);
        assertNotNull(updatedModel);
        assertEquals(0, newTemperature.compareTo(updatedModel.getTemperature()),
            "temperature 应该被更新为 " + newTemperature);

        log.info("✅ testUpdateTemperature 测试通过！");
        log.info("   更新后的 temperature: {}", updatedModel.getTemperature());
    }

    /**
     * 测试同时更新 maxTokens 和 temperature
     */
    @Test
    public void testUpdateBothMaxTokensAndTemperature() {
        Long modelId = 1L;
        Integer newMaxTokens = 128000;
        BigDecimal newTemperature = new BigDecimal("0.95");

        // 获取当前模型信息
        AIModel existingModel = aiModelService.findById(modelId);
        assertNotNull(existingModel, "模型 ID 1 应该存在");

        log.info("更新前的 maxTokens: {}, temperature: {}",
            existingModel.getMaxTokens(), existingModel.getTemperature());

        // 同时更新 maxTokens 和 temperature
        existingModel.setMaxTokens(newMaxTokens);
        existingModel.setTemperature(newTemperature);
        int result = aiModelService.update(existingModel);

        // 验证更新成功
        assertTrue(result > 0, "更新操作应该返回成功");

        // 验证数据库中的值已更新
        AIModel updatedModel = aiModelService.findById(modelId);
        assertNotNull(updatedModel);
        assertEquals(newMaxTokens, updatedModel.getMaxTokens(), "maxTokens 应该被更新");
        assertEquals(0, newTemperature.compareTo(updatedModel.getTemperature()),
            "temperature 应该被更新");

        log.info("✅ testUpdateBothMaxTokensAndTemperature 测试通过！");
        log.info("   更新后的 maxTokens: {}", updatedModel.getMaxTokens());
        log.info("   更新后的 temperature: {}", updatedModel.getTemperature());
    }
}
