package com.internship.importing;

import com.internship.entity.*;
import com.internship.entity.Class;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.internship.importing.SmartImportValidation.*;

class SmartImportValidationTest {
    static SmartImportCatalog.Snapshot data(List<Major> majors,List<Class> classes,Map<String,StudentUser> students) {
        Department dept = new Department(); dept.setId(24L); dept.setName("医学技术学院");
        return new SmartImportCatalog.Snapshot(majors,List.of(dept),List.of(),classes,List.of(),List.of(),List.of(),List.of(),students,List.of());
    }
    static Major major(long id,String name) { Major m = new Major(); m.setId(id); m.setName(name); m.setDepartmentId(24L); return m; }
    static Class clazz(long id,String name,long major,int grade) { Class c = new Class(); c.setId(id); c.setName(name); c.setMajorId(major); c.setGrade(grade); return c; }
    static InputRow row(int number,Map<String,String> values) { return new InputRow("0:0:" + number,"教师名单.xlsx","名单",number,values); }
    static SmartImportCatalog.Snapshot students() { return data(List.of(major(39,"生物医学工程")),List.of(clazz(7,"23级生工7班",39,2023)),Map.of()); }
    static Map<String,String> student(String id) { return new LinkedHashMap<>(Map.of("学号",id,"姓名","测试学生","班级","23级莞生工07班","性别","女")); }
    @Test void resolvesUniqueClassAliasAndDerivesOnlyItsStoredMajorAndYear() {
        var checked = check(SmartImportType.STUDENT,List.of(row(3,student("00123456789"))),students()).getFirst();
        assertEquals("READY",checked.status()); assertEquals("00123456789",checked.values().get("学号"));
        assertEquals("39",checked.values().get("专业")); assertEquals("7",checked.values().get("班级")); assertEquals("2023",checked.values().get("年级"));
    }
    @Test void missingIdentityAndUnknownGenderAreReportedWithoutGuessing() {
        var values = student(""); values.put("性别","未知");
        var checked = check(SmartImportType.STUDENT,List.of(row(8,values)),students()).getFirst();
        assertEquals("BLOCKED",checked.status()); assertTrue(checked.issues().stream().anyMatch(i -> i.field().equals("学号")));
        assertTrue(checked.issues().stream().anyMatch(i -> i.field().equals("性别"))); assertEquals("",checked.values().get("学号")); assertEquals(8,checked.row());
    }
    @Test void unknownClassAndAmbiguousMajorNeverCreateReferences() {
        var v = student("123"); v.put("班级","不存在的班级"); v.put("专业","同名专业"); v.put("年级","2023");
        var d = data(List.of(major(39,"同名专业"),major(40,"同名专业")),List.of(),Map.of());
        var checked = check(SmartImportType.STUDENT,List.of(row(2,v)),d).getFirst();
        assertEquals("BLOCKED",checked.status()); assertTrue(checked.issues().stream().anyMatch(i -> i.message().contains("多个")));
    }
    @Test void mismatchedMajorAndYearBlockTheStudent() {
        var v = student("123"); v.put("专业","40"); v.put("年级","2024");
        var d = data(List.of(major(39,"生物医学工程"),major(40,"其他专业")),students().classes(),Map.of());
        var checked = check(SmartImportType.STUDENT,List.of(row(2,v)),d).getFirst();
        assertEquals("BLOCKED",checked.status()); assertTrue(checked.issues().stream().anyMatch(i -> i.field().equals("专业")));
        assertTrue(checked.issues().stream().anyMatch(i -> i.field().equals("年级")));
    }
    @Test void exactInFileDuplicatesAreSkippedButBothConflictingIdentitiesAreHeld() {
        var v = student("123");
        var exact = check(SmartImportType.STUDENT,List.of(row(2,v),row(3,v)),students());
        assertEquals(List.of("READY","SKIPPED"),exact.stream().map(CheckedRow::status).toList());
        var other = new LinkedHashMap<>(v); other.put("姓名","另一个学生");
        var conflicting = check(SmartImportType.STUDENT,List.of(row(2,v),row(3,other)),students());
        assertTrue(conflicting.stream().allMatch(r -> "BLOCKED".equals(r.status())));
    }
    @Test void existingStudentIsPreservedAndDifferentNameIsHeld() {
        StudentUser saved = new StudentUser(); saved.setStudentId("123"); saved.setName("测试学生"); saved.setMajorId(39L); saved.setClassId(7L); saved.setPassword("original-hash");
        var d = data(students().majors(),students().classes(),Map.of("123",saved));
        assertEquals("SKIPPED",check(SmartImportType.STUDENT,List.of(row(2,student("123"))),d).getFirst().status());
        var v = student("123"); v.put("姓名","另一身份");
        assertEquals("BLOCKED",check(SmartImportType.STUDENT,List.of(row(2,v)),d).getFirst().status()); assertEquals("original-hash",saved.getPassword());
    }
    @Test void teacherNeedsAnExplicitRoleAndDepartmentTeachersNeedDivision() {
        var v = new LinkedHashMap<>(Map.of("工号","t123","姓名","教师甲","院系","医学技术学院"));
        assertEquals("BLOCKED",check(SmartImportType.TEACHER,List.of(row(2,v)),students()).getFirst().status());
        v.put("教师类型","辅导员"); assertEquals("READY",check(SmartImportType.TEACHER,List.of(row(2,v)),students()).getFirst().status());
        v.put("教师类型","系室教师"); assertEquals("BLOCKED",check(SmartImportType.TEACHER,List.of(row(2,v)),students()).getFirst().status());
    }
    @Test void classRequiresAStoredMajorAndYear() {
        var checked = check(SmartImportType.CLASS,List.of(row(2,Map.of("班级名称","新班级","专业","生物医学工程","年级","23级"))),students()).getFirst();
        assertEquals("READY",checked.status()); assertEquals("2023",checked.values().get("年级"));
    }
    @Test void departmentMajorCanReuseTheParentButPreservesExistingMajor() {
        assertEquals("READY",check(SmartImportType.DEPARTMENT_MAJOR,List.of(row(2,Map.of("院系名称","医学技术学院","系名称","检验系","专业名称","新专业"))),students()).getFirst().status());
        assertEquals("SKIPPED",check(SmartImportType.DEPARTMENT_MAJOR,List.of(row(2,Map.of("院系名称","医学技术学院","专业名称","生物医学工程"))),students()).getFirst().status());
    }
    @Test void positionsWithoutCompanyAreHeldWhileCategoryOnlyRowsAreValid() {
        assertEquals("READY",check(SmartImportType.POSITION_CATEGORY,List.of(row(2,Map.of("类别名称","软件开发"))),students()).getFirst().status());
        var checked = check(SmartImportType.POSITION_CATEGORY,List.of(row(2,Map.of("类别名称","软件开发","岗位名称","后端开发"))),students()).getFirst();
        assertEquals("BLOCKED",checked.status()); assertTrue(checked.issues().stream().anyMatch(i -> i.field().equals("企业名称")));
    }
    @Test void announcementsDefaultToDraftAndNeverInventAnAudience() {
        var v = new LinkedHashMap<>(Map.of("标题","实习安排","内容","测试通知"));
        assertEquals("BLOCKED",check(SmartImportType.ANNOUNCEMENT,List.of(row(2,v)),students()).getFirst().status());
        v.put("目标群体","全体学生"); var checked = check(SmartImportType.ANNOUNCEMENT,List.of(row(2,v)),students()).getFirst();
        assertEquals("READY",checked.status()); assertEquals("DRAFT",checked.values().get("状态"));
    }
    @Test void companyMissingAddressAndInvalidContactPhoneAreHeld() {
        var checked = check(SmartImportType.COMPANY,List.of(row(2,Map.of("企业名称","测试公司","联系人","联系人甲","联系电话","abc"))),students()).getFirst();
        assertEquals("BLOCKED",checked.status()); assertTrue(checked.issues().stream().anyMatch(i -> i.field().equals("企业地址")));
    }
    @Test void existingGradeOrContactConflictsAreHeldEvenWithTheSameName() {
        StudentUser saved = new StudentUser(); saved.setStudentId("123"); saved.setName("测试学生"); saved.setMajorId(39L); saved.setClassId(7L);
        saved.setGrade(2024); saved.setPhone("13800000001"); var v = student("123"); v.put("手机号","13800000002");
        var checked = check(SmartImportType.STUDENT,List.of(row(2,v)),data(students().majors(),students().classes(),Map.of("123",saved))).getFirst();
        assertEquals("BLOCKED",checked.status()); assertTrue(checked.issues().stream().anyMatch(i -> i.field().equals("年级")));
        assertTrue(checked.issues().stream().anyMatch(i -> i.field().equals("手机号")));
    }
    @Test void newMajorWithoutAnUnambiguousDivisionIsHeld() {
        var checked = check(SmartImportType.DEPARTMENT_MAJOR,List.of(row(2,Map.of("院系名称","医学技术学院","专业名称","新专业"))),students()).getFirst();
        assertEquals("BLOCKED",checked.status()); assertTrue(checked.issues().stream().anyMatch(i -> i.field().equals("系名称")));
        Division division = new Division(); division.setId(3L); division.setName("检验系"); division.setDepartmentId(24L);
        var d = students(); var linked = new SmartImportCatalog.Snapshot(d.majors(),d.departments(),List.of(division),d.classes(),d.teachers(),d.companies(),d.categories(),d.positions(),d.students(),d.announcements());
        var resolved = check(SmartImportType.DEPARTMENT_MAJOR,List.of(row(2,Map.of("院系名称","医学技术学院","专业名称","新专业"))),linked).getFirst();
        assertEquals("READY",resolved.status()); assertEquals("检验系",resolved.values().get("系名称"));
    }
    @Test void companyContactsCanShareACompanyButConflictingProfilesAreHeld() {
        var first = Map.of("企业名称","测试公司","联系人","甲","联系电话","13800000001","企业地址","广州");
        var second = new LinkedHashMap<>(first); second.put("联系人","乙"); second.put("联系电话","13800000002");
        assertTrue(check(SmartImportType.COMPANY,List.of(row(2,first),row(3,second)),students()).stream().allMatch(r -> "READY".equals(r.status())));
        second.put("企业地址","深圳");
        var conflicting = check(SmartImportType.COMPANY,List.of(row(2,first),row(3,second)),students());
        assertTrue(conflicting.stream().allMatch(r -> "BLOCKED".equals(r.status()) && r.issues().stream().anyMatch(i -> i.field().equals("企业地址"))));
    }
    @Test void oversizedNamesAreReportedBeforeWritingAndCaseVariantsShareIdentity() {
        var longName = student("123"); longName.put("姓名","名".repeat(51));
        var checked = check(SmartImportType.STUDENT,List.of(row(2,longName)),students()).getFirst();
        assertEquals("BLOCKED",checked.status()); assertTrue(checked.issues().stream().anyMatch(i -> i.field().equals("姓名")));
        var conflicting = check(SmartImportType.STUDENT,List.of(row(2,student("ABC1")),row(3,student("abc1"))),students());
        assertTrue(conflicting.stream().allMatch(r -> "BLOCKED".equals(r.status())));
    }
}
