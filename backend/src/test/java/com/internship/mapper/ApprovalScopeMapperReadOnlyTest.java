package com.internship.mapper;

import com.internship.entity.TeacherUser;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in SELECT-only verification against existing data; never starts Spring or changes the database. */
@EnabledIfEnvironmentVariable(named = "APPROVAL_SCOPE_DB_TESTS", matches = "true")
class ApprovalScopeMapperReadOnlyTest {
    private record Student(long id, long majorId, long classId) { }

    @Test
    void counselorFiltersStayWithinPersistedClassRelations() throws Exception {
        String url = System.getenv("APPROVAL_SCOPE_DB_URL");
        String password = System.getenv("APPROVAL_SCOPE_DB_PASSWORD");
        assertNotNull(url);
        assertNotNull(password);
        Configuration configuration = new Configuration(new Environment("approval-read-only",
                new JdbcTransactionFactory(), new UnpooledDataSource("com.mysql.cj.jdbc.Driver", url, "user", password)));
        configuration.setLogImpl(NoLoggingImpl.class);
        configuration.setDefaultStatementTimeout(10);
        configuration.addMapper(TeacherAutomationScopeMapper.class);

        try (SqlSession session = new SqlSessionFactoryBuilder().build(configuration).openSession(false)) {
            Connection connection = session.getConnection();
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            List<Student> students = new ArrayList<>();
            Map<Long, Set<Long>> assignedClasses = new HashMap<>();
            List<Long> counselors = new ArrayList<>();
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(10);
                try (ResultSet rows = statement.executeQuery("SELECT id,major_id,class_id FROM student_users WHERE status=1")) {
                    while (rows.next()) students.add(new Student(rows.getLong(1), rows.getLong(2), rows.getLong(3)));
                }
                try (ResultSet rows = statement.executeQuery("SELECT counselor_id,class_id FROM class_counselor_relation")) {
                    while (rows.next()) assignedClasses.computeIfAbsent(rows.getLong(1), ignored -> new HashSet<>()).add(rows.getLong(2));
                }
                try (ResultSet rows = statement.executeQuery("SELECT id FROM teacher_users WHERE teacher_type='COUNSELOR' AND status=1")) {
                    while (rows.next()) counselors.add(rows.getLong(1));
                }
            }
            assertTrue(!counselors.isEmpty(), "Existing counselor accounts are required for verification");
            TeacherAutomationScopeMapper mapper = session.getMapper(TeacherAutomationScopeMapper.class);
            int filterChecks = 0;
            for (Long counselorId : counselors) {
                TeacherUser teacher = new TeacherUser();
                teacher.setId(counselorId);
                teacher.setTeacherType("COUNSELOR");
                Set<Long> classes = assignedClasses.getOrDefault(counselorId, Set.of());
                List<Student> owned = students.stream().filter(student -> classes.contains(student.classId())).toList();
                assertEquals(ids(owned), mapper.studentIds(teacher));
                assertEquals(ids(owned), mapper.filteredStudentIds(teacher, null, null));
                for (Long classId : classes) {
                    List<Student> inClass = owned.stream().filter(student -> student.classId() == classId).toList();
                    assertEquals(ids(inClass), mapper.filteredStudentIds(teacher, null, classId));
                    for (Long majorId : inClass.stream().map(Student::majorId).distinct().toList()) {
                        assertEquals(ids(inClass.stream().filter(student -> student.majorId() == majorId).toList()),
                                mapper.filteredStudentIds(teacher, majorId, classId));
                        filterChecks++;
                    }
                    assertEquals(List.of(), mapper.filteredStudentIds(teacher, Long.MAX_VALUE, classId));
                    filterChecks++;
                }
                for (Long majorId : owned.stream().map(Student::majorId).distinct().toList()) {
                    assertEquals(ids(owned.stream().filter(student -> student.majorId() == majorId).toList()),
                            mapper.filteredStudentIds(teacher, majorId, null));
                    filterChecks++;
                }
                Long outsideClass = students.stream().map(Student::classId).filter(id -> !classes.contains(id)).findFirst().orElse(Long.MAX_VALUE);
                assertEquals(List.of(), mapper.filteredStudentIds(teacher, null, outsideClass));
                filterChecks++;
            }
            TeacherUser noClasses = new TeacherUser();
            noClasses.setId(Long.MAX_VALUE);
            noClasses.setTeacherType("COUNSELOR");
            assertEquals(List.of(), mapper.studentIds(noClasses));
            assertEquals(List.of(), mapper.filteredStudentIds(noClasses, null, null));
            assertEquals(List.of(), mapper.filteredStudentIds(noClasses, null, students.getFirst().classId()));
            System.out.printf("SELECT-only verification: %d counselors and %d scope/filter combinations passed.%n", counselors.size(), filterChecks);
        }
    }

    private List<Long> ids(List<Student> students) {
        return students.stream().map(Student::id).sorted().toList();
    }
}
