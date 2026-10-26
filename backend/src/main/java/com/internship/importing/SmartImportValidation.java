package com.internship.importing;

import com.internship.entity.*;
import com.internship.entity.Class;
import java.util.*;
import java.util.function.Function;

/** All decisions that permit a write are deterministic and use current business data. */
public final class SmartImportValidation {
    private SmartImportValidation() {}
    public record Issue(String field, String message) {}
    public record InputRow(String id, String file, String sheet, int row, Map<String,String> values) {}
    public record CheckedRow(String id, String file, String sheet, int row, Map<String,String> values,
                             String status, List<Issue> issues, List<String> notes) {}
    public static List<CheckedRow> check(SmartImportType type, List<InputRow> input, SmartImportCatalog.Snapshot data) {
        List<CheckedRow> out = new ArrayList<>();
        for (var row : input) out.add(checkOne(type,row,data));
        Map<String,List<Integer>> groups = new LinkedHashMap<>();
        for (int i = 0; i < out.size(); i++) {
            String key = key(type,out.get(i).values());
            if (!key.isBlank()) groups.computeIfAbsent(key,k -> new ArrayList<>()).add(i);
        }
        for (var group : groups.values()) {
            if (group.size() < 2) continue;
            var first = out.get(group.getFirst());
            boolean conflict = group.stream().anyMatch(i -> !out.get(i).values().equals(first.values()));
            for (int i : group) {
                var row = out.get(i);
                if (conflict) {
                    var issues = new ArrayList<>(row.issues()); issues.add(new Issue("", "文件内同一标识有不同内容，请核对后保留正确的一行。"));
                    out.set(i, new CheckedRow(row.id(),row.file(),row.sheet(),row.row(),row.values(),"BLOCKED",issues,row.notes()));
                } else if (i != group.getFirst() && !"BLOCKED".equals(row.status())) {
                    var notes = new ArrayList<>(row.notes()); notes.add("文件内完全重复，保留首次出现的数据。");
                    out.set(i, new CheckedRow(row.id(),row.file(),row.sheet(),row.row(),row.values(),"SKIPPED",row.issues(),notes));
                }
            }
        }
        if (type == SmartImportType.COMPANY) {
            Map<String,List<Integer>> companyGroups = new LinkedHashMap<>();
            for (int i = 0; i < out.size(); i++) companyGroups.computeIfAbsent(SmartImportType.normalize(text(out.get(i).values(),"企业名称")),k -> new ArrayList<>()).add(i);
            for (var group : companyGroups.values()) for (String field : List.of("企业名称","企业地址","所在城市","企业简介","统一社会信用代码")) {
                if (group.stream().map(i -> text(out.get(i).values(),field)).filter(s -> !s.isBlank()).distinct().count() < 2) continue;
                for (int i : group) {
                    var row = out.get(i); var issues = new ArrayList<>(row.issues());
                    issues.add(new Issue(field,"同一企业的“" + field + "”在文件中不一致，请核对后统一。"));
                    out.set(i,new CheckedRow(row.id(),row.file(),row.sheet(),row.row(),row.values(),"BLOCKED",issues,row.notes()));
                }
            }
        }
        return out;
    }
    static CheckedRow checkOne(SmartImportType type, InputRow row, SmartImportCatalog.Snapshot data) {
        Map<String,String> v = new LinkedHashMap<>(row.values()); List<Issue> issues = new ArrayList<>(); List<String> notes = new ArrayList<>();
        boolean existing = false;
        switch(type) {
            case STUDENT -> {
                Class clazz = resolve(data.classes(),v,"班级",c -> c.getId().toString(),Class::getName,true,issues,notes);
                Major major = resolve(data.majors(),v,"专业",m -> m.getId().toString(),Major::getName,false,issues,notes);
                if (clazz != null) {
                    if (major == null && text(v,"专业").isBlank() && clazz.getMajorId() != null) {
                        major = data.majors().stream().filter(m -> m.getId().equals(clazz.getMajorId())).findFirst().orElse(null);
                        if (major != null) { v.put("专业",major.getId().toString()); notes.add("专业来自该班级在系统中的所属专业。"); }
                    }
                    if (major != null && !Objects.equals(major.getId(),clazz.getMajorId())) issues.add(new Issue("专业","专业与班级在系统中的归属不一致。"));
                    if (text(v,"年级").isBlank() && clazz.getGrade() != null) { v.put("年级",clazz.getGrade().toString()); notes.add("年级来自该班级在系统中的年级。"); }
                }
                year(v,issues);
                if (clazz != null && !text(v,"年级").isBlank() && !Objects.equals(text(v,"年级"),Objects.toString(clazz.getGrade(),"")))
                    issues.add(new Issue("年级","年级与班级在系统中的年级不一致。"));
                String gender = text(v,"性别");
                if (!gender.isBlank()) {
                    String normalized = switch(gender.toUpperCase(Locale.ROOT)) { case "男","男性","M","MALE","1" -> "男"; case "女","女性","F","FEMALE","2" -> "女"; default -> ""; };
                    if (normalized.isBlank()) issues.add(new Issue("性别","性别无法确定，请选择男或女。")); else v.put("性别",normalized);
                }
                identifier(v,"学号",issues);
                StudentUser saved = data.students().values().stream().filter(s -> text(v,"学号").equalsIgnoreCase(s.getStudentId())).findFirst().orElse(null);
                if (saved != null) {
                    existing = true;
                    if (!Objects.equals(saved.getName(),text(v,"姓名"))) issues.add(new Issue("姓名","同一学号已存在，但姓名不同；不会覆盖已有账号。"));
                    if (major != null && !Objects.equals(saved.getMajorId(),major.getId())) issues.add(new Issue("专业","已有学生的专业不同，请人工核对；不会覆盖。"));
                    if (clazz != null && !Objects.equals(saved.getClassId(),clazz.getId())) issues.add(new Issue("班级","已有学生的班级不同，请人工核对；不会覆盖。"));
                    if (saved.getGrade() != null && !text(v,"年级").isBlank() && !saved.getGrade().toString().equals(text(v,"年级"))) issues.add(new Issue("年级","已有学生的年级不同，请人工核对；不会覆盖。"));
                    if (saved.getGender() != null && !text(v,"性别").isBlank() && !Objects.equals(saved.getGender(),"男".equals(text(v,"性别")) ? 1 : 2)) issues.add(new Issue("性别","已有学生的性别不同，请人工核对；不会覆盖。"));
                    conflict(v,"手机号",saved.getPhone(),issues); conflict(v,"邮箱",saved.getEmail(),issues);
                    notes.add("该学号已存在，保留原账号、密码和实习记录。");
                }
            }
            case TEACHER -> {
                Department department = resolve(data.departments(),v,"院系",d -> d.getId().toString(),Department::getName,false,issues,notes);
                String teacherType = switch(text(v,"教师类型")) { case "学院教师","学院老师","COLLEGE" -> "COLLEGE"; case "系室教师","系室老师","DEPARTMENT" -> "DEPARTMENT"; case "辅导员","COUNSELOR" -> "COUNSELOR"; default -> ""; };
                if (!teacherType.isBlank()) v.put("教师类型",teacherType);
                else if (!text(v,"教师类型").isBlank()) issues.add(new Issue("教师类型","教师类型须为学院教师、系室教师或辅导员。"));
                Division division = resolve(data.divisions(),v,"系",d -> d.getId().toString(),Division::getName,false,issues,notes);
                if ("DEPARTMENT".equals(teacherType) && division == null && text(v,"系").isBlank()) issues.add(new Issue("系","系室教师必须提供所属系。"));
                if (division != null && department != null && !Objects.equals(division.getDepartmentId(),department.getId())) issues.add(new Issue("系","该系不属于所选学院。"));
                if (division != null && !"DEPARTMENT".equals(teacherType)) issues.add(new Issue("系","仅系室教师可填写所属系，请核对教师类型。"));
                identifier(v,"工号",issues);
                var saved = data.teachers().stream().filter(t -> text(v,"工号").equalsIgnoreCase(t.getTeacherUserId())).findFirst().orElse(null);
                if (saved != null) {
                    existing = true;
                    if (!Objects.equals(saved.getName(),text(v,"姓名")) || !Objects.equals(saved.getTeacherType(),teacherType)
                            || (department != null && !Objects.equals(saved.getDepartmentId(),department.getId().toString())))
                        issues.add(new Issue("工号","已有工号的姓名、类型或学院不同；不会覆盖已有账号。"));
                    if (division != null && !Objects.equals(saved.getDivisionId(),division.getId().toString())) issues.add(new Issue("系","已有教师的系室不同，请人工核对；不会覆盖。"));
                    conflict(v,"手机号",saved.getPhone(),issues); conflict(v,"邮箱",saved.getEmail(),issues);
                    notes.add("工号已存在，保留原教师账号。");
                }
            }
            case COMPANY -> {
                existing = data.companies().stream().anyMatch(c -> SmartImportType.normalize(text(v,"企业名称")).equals(SmartImportType.normalize(c.getCompanyName()))
                        || text(v,"企业名称").equals(c.getUsername()));
                if (existing) notes.add("企业已存在，本次智能导入保留已有企业及联系人。");
                v.put("联系电话",text(v,"联系电话").replaceAll("\\s+", ""));
                if (!text(v,"联系电话").isBlank() && !text(v,"联系电话").matches("[+0-9()（）\\-转]{7,20}")) issues.add(new Issue("联系电话","联系人电话格式异常，请核对；应为7至20个字符。"));
                email(v,"联系邮箱",issues);
            }
            case CLASS -> {
                resolve(data.majors(),v,"专业",m -> m.getId().toString(),Major::getName,false,issues,notes); year(v,issues);
                TeacherUser teacher = resolve(data.teachers().stream().filter(t -> "COUNSELOR".equals(t.getTeacherType())).toList(),v,"负责教师",
                        TeacherUser::getTeacherUserId,TeacherUser::getName,false,issues,notes);
                existing = data.classes().stream().anyMatch(c -> SmartImportType.normalize(c.getName()).equals(SmartImportType.normalize(text(v,"班级名称"))));
                if (existing) notes.add("班级名称已存在，保留现有班级及辅导员关联。");
            }
            case DEPARTMENT_MAJOR -> {
                var parents = SmartImportCatalog.match(data.departments(),text(v,"院系名称"),d -> d.getId().toString(),Department::getName,false);
                if (parents.size() > 1) issues.add(new Issue("院系名称","系统有多个同名院系，请先核实。"));
                Long parentId = parents.size() == 1 ? parents.getFirst().getId() : null;
                Major saved = null;
                if (parents.size() == 1) {
                    var parent = parents.getFirst(); v.put("院系名称",parent.getName());
                    saved = data.majors().stream().filter(m -> Objects.equals(m.getDepartmentId(),parent.getId())
                            && SmartImportType.normalize(m.getName()).equals(SmartImportType.normalize(text(v,"专业名称")))).findFirst().orElse(null);
                    existing = saved != null;
                } else if (!text(v,"院系名称").isBlank()) notes.add("将新增院系：" + text(v,"院系名称"));
                var divisions = data.divisions().stream().filter(d -> Objects.equals(d.getDepartmentId(),parentId)).toList();
                String divisionName = text(v,"系名称");
                if (divisionName.isBlank() && !existing) {
                    if (divisions.size() == 1) {
                        v.put("系名称",divisions.getFirst().getName()); notes.add("所属系来自该院系在系统中唯一的系。");
                    } else issues.add(new Issue("系名称","新增专业必须明确所属系，请补充系名称；不会猜测归属。"));
                } else if (!divisionName.isBlank()) {
                    var matches = SmartImportCatalog.match(divisions,divisionName,d -> d.getId().toString(),Division::getName,false);
                    if (matches.size() > 1) issues.add(new Issue("系名称","该院系中有多个同名系，请先核实。"));
                    else if (matches.size() == 1) {
                        var division = matches.getFirst(); v.put("系名称",division.getName());
                        if (saved != null && !Objects.equals(saved.getDivisionId(),division.getId())) issues.add(new Issue("系名称","已有专业所属系不同，请人工核对；不会覆盖。"));
                    } else if (existing) issues.add(new Issue("系名称","已有专业所属系不同，请人工核对；不会覆盖。"));
                    else if (divisionName.matches("\\d+")) issues.add(new Issue("系名称","所填系编号不属于该院系，请核对。"));
                    else notes.add("将在所填院系下新增系：" + divisionName);
                }
                if (existing) notes.add("该院系内的专业已存在，保留原专业。");
            }
            case POSITION_CATEGORY -> {
                if (!text(v,"岗位名称").isBlank()) {
                    if (text(v,"岗位名称").length() < 2) issues.add(new Issue("岗位名称","岗位名称至少需要2个字符。"));
                    CompanyUser company = resolve(data.companies(),v,"企业名称",c -> c.getId().toString(),CompanyUser::getCompanyName,false,issues,notes);
                    if (text(v,"企业名称").isBlank()) issues.add(new Issue("企业名称","岗位必须关联一个现有企业，请补充企业名称。"));
                    var category = data.categories().stream().filter(c -> SmartImportType.normalize(c.getName()).equals(SmartImportType.normalize(text(v,"类别名称")))).findFirst().orElse(null);
                    if (category != null && company != null) existing = data.positions().stream().anyMatch(p -> Objects.equals(p.getCategoryId(),category.getId())
                            && Objects.equals(p.getCompanyId(),company.getId()) && text(v,"岗位名称").equals(p.getPositionName()));
                    notes.add("新增岗位将以暂停状态保存，请在岗位页面核对后启用。");
                } else existing = data.categories().stream().anyMatch(c -> SmartImportType.normalize(c.getName()).equals(SmartImportType.normalize(text(v,"类别名称"))));
                if (existing) notes.add("类别或该企业下的岗位已存在，保留原记录。");
                if (text(v,"类别名称").length() < 2 || text(v,"类别名称").length() > 50) issues.add(new Issue("类别名称","类别名称应为2至50个字符。"));
            }
            case ANNOUNCEMENT -> {
                if (!text(v,"标题").isBlank() && text(v,"标题").length() < 2) issues.add(new Issue("标题","公告标题至少需要2个字符。"));
                if (text(v,"状态").isBlank()) { v.put("状态","DRAFT"); notes.add("未指定发布状态，以草稿保存。"); }
                String status = switch(text(v,"状态")) { case "DRAFT","草稿" -> "DRAFT"; case "PUBLISHED","已发布" -> "PUBLISHED"; case "EXPIRED","已过期" -> "EXPIRED"; default -> ""; };
                if (status.isBlank()) issues.add(new Issue("状态","公告状态须为草稿、已发布或已过期。")); else v.put("状态",status);
                String target = switch(text(v,"目标群体")) { case "ALL","全体师生" -> "ALL"; case "STUDENT","全体学生" -> "STUDENT"; case "TEACHER","全体教师" -> "TEACHER"; case "COMPANY","企业用户" -> "COMPANY"; case "MAJOR","特定专业学生" -> "MAJOR"; case "TEACHER_TYPE","特定教师类别" -> "TEACHER_TYPE"; default -> ""; };
                if (target.isBlank() && !text(v,"目标群体").isBlank()) issues.add(new Issue("目标群体","无法确定通知对象，请明确选择。"));
                else if (!target.isBlank()) v.put("目标群体",target);
                if ("MAJOR".equals(target)) {
                    resolve(data.majors(),v,"目标值",m -> m.getId().toString(),Major::getName,false,issues,notes);
                    if (text(v,"目标值").isBlank()) issues.add(new Issue("目标值","特定专业公告必须指定专业。"));
                } else if ("TEACHER_TYPE".equals(target) && !Set.of("COLLEGE","DEPARTMENT","COUNSELOR").contains(text(v,"目标值")))
                    issues.add(new Issue("目标值","请填写 COLLEGE、DEPARTMENT 或 COUNSELOR。"));
                else if (!"TEACHER_TYPE".equals(target) && !text(v,"目标值").isBlank()) issues.add(new Issue("目标值","此通知对象不使用目标值，请清空并核对。"));
                existing = data.announcements().stream().anyMatch(a -> text(v,"标题").equals(a.getTitle()) && text(v,"内容").equals(a.getContent()));
                if (existing) notes.add("相同标题与内容的公告已存在，保留原公告。");
            }
        }
        for (var field : type.fields) if (field.required() && text(v,field.name()).isBlank()) issues.add(new Issue(field.name(),"缺少" + field.name() + "，请补充后重新校验。"));
        for (String field : v.keySet()) {
            int limit = switch(field) {
                case "姓名","联系人","所在城市","统一社会信用代码" -> 50;
                case "手机号","联系电话" -> 20;
                case "邮箱","联系邮箱","班级名称","院系名称","系名称","专业名称","岗位名称" -> 100;
                case "企业名称","企业地址","标题" -> 255;
                default -> 16000;
            };
            if (text(v,field).length() > limit) issues.add(new Issue(field,field + "超过" + limit + "个字符，请核对字段对应关系。"));
        }
        phone(v,"手机号",issues); email(v,"邮箱",issues);
        return new CheckedRow(row.id(),row.file(),row.sheet(),row.row(),v,issues.isEmpty() ? existing ? "SKIPPED" : "READY" : "BLOCKED",issues,notes);
    }
    static String text(Map<String,String> row, String field) { return Objects.toString(row.get(field),"").trim(); }
    private static void year(Map<String,String> row, List<Issue> issues) {
        String raw = text(row,"年级"); if (raw.isBlank()) return;
        String year = raw.replaceAll("级$", "");
        if (year.matches("\\d{2}") && raw.endsWith("级")) year = "20" + year;
        if (!year.matches("20\\d{2}")) issues.add(new Issue("年级","年级应为四位入学年份，如2023。")); else row.put("年级",year);
    }
    private static void identifier(Map<String,String> row,String field,List<Issue> issues) {
        String value = text(row,field);
        if (!value.isBlank() && !value.matches("[A-Za-z0-9_-]{1,32}")) issues.add(new Issue(field,field + "格式异常，请核对原表；不会猜测或补齐编号。"));
    }
    private static void phone(Map<String,String> row,String field,List<Issue> issues) {
        String value = text(row,field); if (!value.isBlank() && !value.matches("[+\\d()（） -]{5,30}")) issues.add(new Issue(field,"电话格式异常，请核对是否对应了正确的列。"));
    }
    private static void email(Map<String,String> row,String field,List<Issue> issues) {
        String value = text(row,field); if (!value.isBlank() && !value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) issues.add(new Issue(field,"邮箱格式异常，请核对。"));
    }
    private static void conflict(Map<String,String> row,String field,String saved,List<Issue> issues) {
        if (saved != null && !saved.isBlank() && !text(row,field).isBlank() && !saved.trim().equals(text(row,field)))
            issues.add(new Issue(field,"已有账号的" + field + "不同，请人工核对；不会覆盖。"));
    }
    private static <T> T resolve(List<T> list,Map<String,String> row,String field,Function<T,String> id,Function<T,String> name,
                                 boolean alias,List<Issue> issues,List<String> notes) {
        String value = text(row,field); if (value.isBlank()) return null;
        List<T> matches = SmartImportCatalog.match(list,value,id,name,alias);
        if (matches.size() != 1) { issues.add(new Issue(field,matches.isEmpty() ? "系统中找不到“" + value + "”，请选择已有记录或先到管理页面建立。" : "“" + value + "”对应多个记录，请明确选择。")); return null; }
        T result = matches.getFirst(); row.put(field,id.apply(result));
        if (!value.equals(id.apply(result)) && !value.equals(name.apply(result))) notes.add(field + "“" + value + "”对应“" + name.apply(result) + "”。");
        return result;
    }
    static String key(SmartImportType type,Map<String,String> row) {
        return switch(type) {
            case STUDENT -> text(row,"学号").toLowerCase(Locale.ROOT); case TEACHER -> text(row,"工号").toLowerCase(Locale.ROOT);
            case COMPANY -> SmartImportType.normalize(text(row,"企业名称")) + "|" + SmartImportType.normalize(text(row,"联系人")) + "|" + text(row,"联系电话");
            case CLASS -> SmartImportType.normalize(text(row,"班级名称"));
            case DEPARTMENT_MAJOR -> SmartImportType.normalize(text(row,"院系名称")) + "|" + SmartImportType.normalize(text(row,"专业名称"));
            case POSITION_CATEGORY -> SmartImportType.normalize(text(row,"类别名称")) + "|" + SmartImportType.normalize(text(row,"岗位名称")) + "|" + text(row,"企业名称");
            case ANNOUNCEMENT -> SmartImportType.normalize(text(row,"标题"));
        };
    }
}
