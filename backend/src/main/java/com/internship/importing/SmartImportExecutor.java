package com.internship.importing;

import com.internship.agent.business.AgentException;
import com.internship.agent.business.AgentTypes.Actor;
import com.internship.entity.*;
import com.internship.entity.Class;
import com.internship.mapper.StudentUserMapper;
import com.internship.mapper.StudentInternshipStatusMapper;
import com.internship.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import static com.internship.importing.SmartImportValidation.text;

/** Called only inside AgentActionService's transaction after current-data validation. */
@Service
@RequiredArgsConstructor
public class SmartImportExecutor {
    private final StudentUserService students;
    private final TeacherUserService teachers;
    private final CompanyUserService companies;
    private final AnnouncementService announcements;
    private final ClassService classes;
    private final DepartmentService departments;
    private final MajorService majors;
    private final DivisionService divisions;
    private final PositionCategoryService categories;
    private final PositionService positions;
    private final StudentUserMapper studentMapper;
    private final StudentInternshipStatusMapper statuses;

    public Map<String,Object> execute(Actor actor,SmartImportType type,List<SmartImportValidation.InputRow> rows,SmartImportCatalog.Snapshot data) {
        List<Map<String,Object>> normalized = new ArrayList<>();
        for (var row : rows) {
            Map<String,Object> map = new LinkedHashMap<>(row.values());
            map.put("_rowNum",row.row());
            if (type == SmartImportType.COMPANY) map.put("_createOnly",true);
            if (type == SmartImportType.ANNOUNCEMENT) { map.put("发布人",actor.name()); map.put("发布人身份","ADMIN"); }
            normalized.add(map);
        }
        Map<String,Object> result;
        switch(type) {
            case STUDENT -> {
                result = students.importFromExcel(normalized);
                // The legacy importer logs status initialization errors. Smart imports must be atomic.
                for (var row : rows) {
                    StudentUser saved = studentMapper.findByStudentId(text(row.values(),"学号"));
                    if (saved == null || saved.getId() == null || statuses.findByStudentId(saved.getId()) == null)
                        throw new AgentException("IMPORT_INCOMPLETE","学生账号或初始实习状态未完整保存，本次导入已回滚。",409);
                }
            }
            case TEACHER -> result = teachers.importFromExcel(normalized);
            case COMPANY -> result = companies.importFromExcel(normalized);
            case ANNOUNCEMENT -> result = announcements.importFromExcel(normalized);
            case CLASS -> {
                for (var row : rows) {
                    var v = row.values(); Class c = new Class(); c.setName(text(v,"班级名称")); c.setMajorId(Long.valueOf(text(v,"专业")));
                    c.setGrade(Integer.valueOf(text(v,"年级"))); c.setTeacherId(text(v,"负责教师")); requireInsert(classes.save(c));
                }
                result = counts(rows.size());
            }
            case DEPARTMENT_MAJOR -> {
                Map<String,Long> parents = new HashMap<>(); data.departments().forEach(d -> parents.put(d.getName(),d.getId()));
                Map<String,Long> divisionIds = new HashMap<>(); data.divisions().forEach(d -> divisionIds.put(d.getDepartmentId() + "|" + d.getName(),d.getId()));
                for (var row : rows) {
                    var v = row.values(); String name = text(v,"院系名称"); Long id = parents.get(name);
                    if (id == null) {
                        Department d = new Department(); d.setName(name); requireInsert(departments.addDepartment(d));
                        if (d.getId() == null) throw incomplete(); id = d.getId(); parents.put(name,id);
                    }
                    String divisionName = text(v,"系名称"); String divisionKey = id + "|" + divisionName;
                    Long divisionId = divisionIds.get(divisionKey);
                    if (divisionId == null) {
                        Division division = new Division(); division.setName(divisionName); division.setDepartmentId(id);
                        requireInsert(divisions.addDivision(division)); if (division.getId() == null) throw incomplete();
                        divisionId = division.getId(); divisionIds.put(divisionKey,divisionId);
                    }
                    Major m = new Major(); m.setName(text(v,"专业名称")); m.setDepartmentId(id); m.setDivisionId(divisionId); requireInsert(majors.save(m));
                }
                result = counts(rows.size());
            }
            case POSITION_CATEGORY -> {
                Map<String,Long> parents = new HashMap<>(); data.categories().forEach(c -> parents.put(c.getName(),c.getId()));
                for (var row : rows) {
                    var v = row.values(); String name = text(v,"类别名称"); Long id = parents.get(name);
                    if (id == null) {
                        PositionCategory category = new PositionCategory(); category.setName(name); category.setDescription("");
                        requireInsert(categories.insert(category)); if (category.getId() == null) throw incomplete(); id = category.getId(); parents.put(name,id);
                    }
                    if (!text(v,"岗位名称").isBlank()) {
                        Position position = new Position(); position.setCategoryId(id); position.setPositionName(text(v,"岗位名称"));
                        position.setRequirements(text(v,"岗位要求")); position.setCompanyId(Long.valueOf(text(v,"企业名称")));
                        position.setStatus("paused"); requireInsert(positions.insert(position));
                    }
                }
                result = counts(rows.size());
            }
            default -> throw new IllegalStateException("Unsupported import type");
        }
        int expected = type == SmartImportType.COMPANY ? (int)rows.stream().map(r -> text(r.values(),"企业名称").toLowerCase(Locale.ROOT)).distinct().count() : rows.size();
        if (((Number)result.getOrDefault("failCount",0)).intValue() != 0 || ((Number)result.getOrDefault("successCount",0)).intValue() != expected)
            throw new AgentException("IMPORT_CHANGED","导入期间数据发生变化或业务校验未通过，本次新增已回滚；请重新预览。",409);
        return new LinkedHashMap<>(result);
    }
    private static Map<String,Object> counts(int count) { return Map.of("successCount",count,"failCount",0,"failList",List.of()); }
    private static void requireInsert(int count) { if (count != 1) throw incomplete(); }
    private static AgentException incomplete() { return new AgentException("IMPORT_INCOMPLETE","数据未完整保存，本次导入已回滚。",409); }
}
