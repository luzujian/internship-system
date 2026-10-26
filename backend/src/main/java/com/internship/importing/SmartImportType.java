package com.internship.importing;

import com.internship.agent.business.AgentException;
import java.util.*;

/** The same field contract is used by page uploads and the administrator assistant. */
public enum SmartImportType {
    STUDENT("student", "学生", "user:student:add", List.of(
            field("学号", true, "学生学号", "学生编号", "学籍号", "studentid", "学生账号"),
            field("姓名", true, "学生姓名", "名字", "name"), field("性别", false, "gender", "sex"),
            field("年级", true, "入学年份", "入学年级", "grade"),
            field("专业", true, "专业名称", "专业ID", "专业编号", "major"),
            field("班级", true, "班级名称", "行政班", "班级ID", "班级编号", "class"),
            field("手机号", false, "电话", "联系电话", "手机", "phone"), field("邮箱", false, "电子邮箱", "email"))),
    TEACHER("teacher", "教师", "user:teacher:add", List.of(
            field("工号", true, "教师工号", "教师编号", "教工号", "职工号", "teacherid"),
            field("姓名", true, "教师姓名", "老师姓名", "name"),
            field("教师类型", true, "身份", "教师身份", "类型", "职务"),
            field("院系", true, "学院", "学院名称", "院系名称", "院系ID"),
            field("系", false, "系室", "系名称", "系室名称", "系室ID"),
            field("手机号", false, "电话", "联系电话", "手机"), field("邮箱", false, "电子邮箱", "email"))),
    COMPANY("company", "企业", "user:company:add", List.of(
            field("企业名称", true, "公司名称", "单位名称", "实习单位", "企业", "公司"),
            field("联系人", true, "联系人姓名", "负责人", "联系人员"),
            field("联系电话", true, "联系人电话", "联系人手机", "电话", "手机", "联系方式"),
            field("联系邮箱", false, "联系人邮箱", "电子邮箱", "邮箱"),
            field("企业地址", true, "单位地址", "公司地址", "地址"),
            field("所在城市", false, "城市"), field("企业简介", false, "简介", "公司简介"),
            field("统一社会信用代码", false, "信用代码"))),
    CLASS("class", "班级", "class:add", List.of(
            field("班级名称", true, "班级", "行政班", "名称"),
            field("专业", true, "专业名称", "专业ID", "专业编号"), field("年级", true, "入学年份", "入学年级"),
            field("负责教师", false, "负责老师", "辅导员", "辅导员工号", "teacherid"))),
    DEPARTMENT_MAJOR("department_major", "院系专业", "major:add", List.of(
            field("院系名称", true, "院系", "学院", "学院名称"),
            field("系名称", false, "系", "系室", "所属系", "系室名称"), field("专业名称", true, "专业", "名称"))),
    POSITION_CATEGORY("position_category", "岗位类别", "position-category:add", List.of(
            field("类别名称", true, "岗位类别", "类别", "分类"), field("岗位名称", false, "岗位", "职位名称", "职位"),
            field("岗位要求", false, "要求", "任职要求"), field("企业名称", false, "公司名称", "单位名称"))),
    ANNOUNCEMENT("announcement", "公告", "announcement:add", List.of(
            field("标题", true, "公告标题", "通知标题"), field("内容", true, "公告内容", "正文", "通知内容"),
            field("状态", false, "发布状态"), field("目标群体", true, "目标类型", "通知对象", "受众"),
            field("目标值", false, "目标专业", "目标教师类别")));

    public record Field(String name, boolean required, List<String> aliases) {}
    public final String key, label, permission;
    public final List<Field> fields;
    SmartImportType(String key, String label, String permission, List<Field> fields) {
        this.key = key; this.label = label; this.permission = permission; this.fields = fields;
    }
    private static Field field(String name, boolean required, String... aliases) {
        return new Field(name, required, List.of(aliases));
    }
    public static SmartImportType parse(String key) {
        return Arrays.stream(values()).filter(t -> t.key.equals(key)).findFirst()
                .orElseThrow(() -> new AgentException("IMPORT_TYPE_REQUIRED", "请选择要导入的数据类型。", 400));
    }
    public Set<String> fieldNames() {
        var out = new LinkedHashSet<String>(); fields.forEach(f -> out.add(f.name())); return out;
    }
    public String matchHeader(String text) {
        String normalized = normalize(text).replaceAll("必填|选填|\\*", "");
        return fields.stream().filter(f -> normalize(f.name()).equals(normalized)
                || f.aliases().stream().anyMatch(a -> normalize(a).equals(normalized)))
                .map(Field::name).findFirst().orElse(null);
    }
    public static String normalize(String text) {
        return Objects.toString(text, "").trim().toLowerCase(Locale.ROOT)
                .replaceAll("[\\s　_（）()：:·-]", "");
    }
}
