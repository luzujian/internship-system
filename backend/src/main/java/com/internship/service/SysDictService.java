package com.internship.service;

import com.internship.entity.SysDict;
import com.internship.mapper.SysDictMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统字典服务：从 sys_dict 表加载枚举/状态码/标签映射。
 * 启动时全量缓存，提供按类型/编码查询，避免多处硬编码枚举。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SysDictService {
    private final SysDictMapper mapper;
    private final Map<String, List<SysDict>> cacheByType = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> labelCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() { refresh(); }

    public synchronized void refresh() {
        try {
            List<SysDict> all = mapper.findAll();
            Map<String, List<SysDict>> byType = new LinkedHashMap<>();
            Map<String, Map<String, String>> labels = new LinkedHashMap<>();
            for (SysDict d : all) {
                byType.computeIfAbsent(d.getDictType(), k -> new ArrayList<>()).add(d);
                labels.computeIfAbsent(d.getDictType(), k -> new LinkedHashMap<>()).put(d.getDictCode(), d.getDictLabel());
            }
            cacheByType.clear(); cacheByType.putAll(byType);
            labelCache.clear(); labelCache.putAll(labels);
            log.info("系统字典已加载：{} 种类型，{} 条记录", byType.size(), all.size());
        } catch (Exception e) {
            log.warn("系统字典加载失败，使用已有缓存：{}", e.getMessage());
        }
    }

    public List<SysDict> byType(String type) {
        return cacheByType.getOrDefault(type, List.of());
    }

    public List<String> codesByType(String type) {
        return byType(type).stream().map(SysDict::getDictCode).toList();
    }

    public String label(String type, String code) {
        return labelCache.getOrDefault(type, Map.of()).getOrDefault(code, code);
    }

    public Map<String, String> labelsByType(String type) {
        return Map.copyOf(labelCache.getOrDefault(type, Map.of()));
    }
}
