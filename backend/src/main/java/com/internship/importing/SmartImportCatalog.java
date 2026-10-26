package com.internship.importing;

import com.internship.entity.*;
import com.internship.entity.Class;
import com.internship.mapper.*;
import com.internship.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class SmartImportCatalog {
    private final MajorService majors;
    private final DepartmentService departments;
    private final DivisionService divisions;
    private final ClassService classes;
    private final TeacherUserMapper teachers;
    private final CompanyUserMapper companies;
    private final PositionCategoryService categories;
    private final PositionService positions;
    private final StudentUserMapper students;
    private final AnnouncementMapper announcements;
    public record Option(String value, String name, String label) {}
    public record Snapshot(List<Major> majors, List<Department> departments, List<Division> divisions,
                           List<Class> classes, List<TeacherUser> teachers, List<CompanyUser> companies,
                           List<PositionCategory> categories, List<Position> positions,
                           Map<String,StudentUser> students, List<Announcement> announcements) {
        public Map<String,List<Option>> options(SmartImportType type) {
            Map<String,List<Option>> out = new LinkedHashMap<>();
            switch(type) {
                case STUDENT, CLASS -> {
                    out.put("专业", majors.stream().map(m -> new Option(m.getId().toString(), m.getName(),
                            m.getName() + "（" + departmentName(m.getDepartmentId()) + " / ID " + m.getId() + "）")).toList());
                    if (type == SmartImportType.STUDENT) out.put("班级", classes.stream().map(c -> new Option(c.getId().toString(), c.getName(),
                            c.getName() + "（" + majorName(c.getMajorId()) + " / " + c.getGrade() + "）")).toList());
                    else out.put("负责教师", teachers.stream().filter(t -> "COUNSELOR".equals(t.getTeacherType()))
                            .map(t -> new Option(t.getTeacherUserId(), t.getName(), t.getName() + "（" + t.getTeacherUserId() + "）")).toList());
                    out.put("性别", List.of(new Option("男","男","男"),new Option("女","女","女")));
                }
                case TEACHER -> {
                    out.put("院系", departments.stream().map(d -> new Option(d.getId().toString(),d.getName(),d.getName())).toList());
                    out.put("系", divisions.stream().map(d -> new Option(d.getId().toString(),d.getName(),d.getName() + "（" + departmentName(d.getDepartmentId()) + "）")).toList());
                    out.put("教师类型", List.of(new Option("COLLEGE","学院教师","学院教师"),new Option("DEPARTMENT","系室教师","系室教师"),new Option("COUNSELOR","辅导员","辅导员")));
                }
                case POSITION_CATEGORY -> out.put("企业名称", companies.stream().map(c -> new Option(c.getId().toString(),c.getCompanyName(),c.getCompanyName())).toList());
                case ANNOUNCEMENT -> {
                    out.put("状态",List.of(new Option("DRAFT","草稿","草稿"),new Option("PUBLISHED","已发布","已发布"),new Option("EXPIRED","已过期","已过期")));
                    out.put("目标群体",List.of(new Option("ALL","全体师生","全体师生"),new Option("STUDENT","全体学生","全体学生"),
                            new Option("TEACHER","全体教师","全体教师"),new Option("COMPANY","企业用户","企业用户"),
                            new Option("MAJOR","特定专业学生","特定专业学生"),new Option("TEACHER_TYPE","特定教师类别","特定教师类别")));
                }
                default -> { }
            }
            return out;
        }
        String departmentName(Long id) { return departments.stream().filter(d -> Objects.equals(d.getId(),id)).map(Department::getName).findFirst().orElse("未配置学院"); }
        String majorName(Long id) { return majors.stream().filter(m -> Objects.equals(m.getId(),id)).map(Major::getName).findFirst().orElse("未配置专业"); }
    }
    public Snapshot load(SmartImportType type, List<Map<String,String>> rows) {
        boolean student = type == SmartImportType.STUDENT, teacher = type == SmartImportType.TEACHER;
        boolean linked = student || teacher || type == SmartImportType.CLASS || type == SmartImportType.DEPARTMENT_MAJOR || type == SmartImportType.ANNOUNCEMENT;
        var studentIds = rows.stream().map(r -> r.getOrDefault("学号", "")).filter(s -> !s.isBlank()).distinct().toList();
        List<StudentUser> existing = student && !studentIds.isEmpty() ? students.findByStudentIds(studentIds) : List.of();
        Map<String,StudentUser> existingMap = new HashMap<>(); existing.forEach(s -> existingMap.put(s.getStudentId(),s));
        return new Snapshot(linked ? safe(majors.findAll()) : List.of(), linked ? safe(departments.findAll()) : List.of(),
                teacher || type == SmartImportType.DEPARTMENT_MAJOR ? safe(divisions.findAll()) : List.of(), student || type == SmartImportType.CLASS ? safe(classes.findAllWithStudentCount()) : List.of(),
                teacher || type == SmartImportType.CLASS ? safe(teachers.findAll()) : List.of(),
                type == SmartImportType.COMPANY || type == SmartImportType.POSITION_CATEGORY ? safe(companies.findAll()) : List.of(),
                type == SmartImportType.POSITION_CATEGORY ? safe(categories.findAll()) : List.of(),
                type == SmartImportType.POSITION_CATEGORY ? safe(positions.findAll()) : List.of(), existingMap,
                type == SmartImportType.ANNOUNCEMENT ? safe(announcements.findAll()) : List.of());
    }
    static <T> List<T> safe(List<T> value) { return value == null ? List.of() : value; }
    static <T> List<T> match(List<T> list, String value, Function<T,String> id, Function<T,String> name, boolean classAlias) {
        if (value == null || value.isBlank()) return List.of();
        var byId = list.stream().filter(t -> value.equals(id.apply(t))).toList();
        if (!byId.isEmpty()) return byId;
        String key = classAlias ? classKey(value) : SmartImportType.normalize(value);
        return list.stream().filter(t -> key.equals(classAlias ? classKey(name.apply(t)) : SmartImportType.normalize(name.apply(t)))).toList();
    }
    static String classKey(String text) {
        return SmartImportType.normalize(text).replaceAll("(级)[莞湛东]", "$1").replaceAll("(?<!\\d)0+(\\d+)(班)", "$1$2");
    }
}
