package com.internship.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/api/ai/encouragement")
public class AIEncouragementController {

    private static final Logger log = LoggerFactory.getLogger(AIEncouragementController.class);
    private static final Random random = new Random();

    // 鼓励语列表
    private static final List<Map<String, String>> ENCOURAGEMENTS = Arrays.asList(
            Map.of("text", "今天辛苦了，喝杯水休息一下吧~", "icon", "☕"),
            Map.of("text", "您的工作做得真棒，继续加油！", "icon", "💪"),
            Map.of("text", "微微疲惫是正常的，记得照顾好自己~", "icon", "🌸"),
            Map.of("text", "每一份付出都会有回报的！", "icon", "✨"),
            Map.of("text", "今天又解决了这么多问题，太厉害了！", "icon", "🎉"),
            Map.of("text", "工作再忙，也要记得按时吃饭哦~", "icon", "🍜"),
            Map.of("text", "您是这个团队不可或缺的一员！", "icon", "⭐"),
            Map.of("text", "累了就休息一下，效率会更高的~", "icon", "💤"),
            Map.of("text", "窗外的风景很美，站起来伸个懒腰吧~", "icon", "🌿"),
            Map.of("text", "您今天又进步了一点，为您点赞！", "icon", "👍"),
            Map.of("text", "保持好心情，事情会越来越顺利的~", "icon", "🌈"),
            Map.of("text", "您认真工作的样子真好看！", "icon", "😍"),
            Map.of("text", "别忘了给自己一个小奖励哦~", "icon", "🎁"),
            Map.of("text", "今天的阳光很温暖，您也是~", "icon", "🌞"),
            Map.of("text", "您解决难题的能力超乎想象！", "icon", "🦸"),
            Map.of("text", "给自己泡杯热茶，犒劳一下吧~", "icon", "🍵"),
            Map.of("text", "您的努力每个人都看在眼里呢！", "icon", "👀"),
            Map.of("text", "事情一件一件做，您做得很好！", "icon", "📋"),
            Map.of("text", "休息是为了走更远的路~", "icon", "🛤️"),
            Map.of("text", "相信您，一定可以做到的！", "icon", "🤝"),
            Map.of("text", "您是学生心中最棒的老师！", "icon", "🏫"),
            Map.of("text", "每一天都要好好爱自己哦~", "icon", "❤️"),
            Map.of("text", "工作虽忙，也别忘了陪陪家人~", "icon", "👨‍👩‍👧"),
            Map.of("text", "您的坚持真的非常了不起！", "icon", "🏆")
    );

    /**
     * 获取随机鼓励语
     * @param role 角色类型: teacher, admin
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getEncouragement(@RequestParam(defaultValue = "teacher") String role) {
        try {
            Map<String, String> encouragement = ENCOURAGEMENTS.get(random.nextInt(ENCOURAGEMENTS.size()));

            Map<String, Object> result = new HashMap<>();
            result.put("code", 200);
            result.put("data", encouragement);

            log.info("为角色 {} 获取鼓励语成功", role);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("获取鼓励语异常: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("code", 500);
            errorResponse.put("message", "服务暂时不可用");

            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}
