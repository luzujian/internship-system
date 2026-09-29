package com.internship.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Path;
import java.nio.file.Files;
import java.sql.DriverManager;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Explicitly opted-in administrator test, restricted to a new disposable loopback database. */
@EnabledIfEnvironmentVariable(named="SECURITY_LOCAL_RESTORE_TEST", matches="true")
class BackupLocalRoundTripTest {
    @TempDir Path temp;
    @Test void nativeServiceDumpAndRestorePreserveData() throws Exception {
        String password=System.getenv("SECURITY_LOCAL_MYSQL_PASSWORD");
        String database="codex_backup_test_"+UUID.randomUUID().toString().replace("-", "");
        String url="jdbc:mysql://127.0.0.1:3306/?sslMode=REQUIRED&allowPublicKeyRetrieval=false";
        try(var connection=DriverManager.getConnection(url,"root",password);var statement=connection.createStatement()) {
            statement.execute("CREATE DATABASE "+database+" CHARACTER SET utf8mb4");
            try {
                statement.execute("CREATE TABLE "+database+".sample (id INT PRIMARY KEY, payload VARCHAR(200), optional_value VARCHAR(20))");
                statement.execute("INSERT INTO "+database+".sample VALUES (1,'中文与 emoji 😀',NULL),(2,'quotes: '' and slash: /','present')");
                String expected;
                try(var rows=statement.executeQuery("SELECT SHA2(GROUP_CONCAT(CONCAT(id,HEX(payload),IFNULL(HEX(optional_value),'NULL')) ORDER BY id),256) FROM "+database+".sample")) {assertTrue(rows.next());expected=rows.getString(1);}
                var service=new BackupServiceImpl();
                ReflectionTestUtils.setField(service,"datasourceUrl",url.replace("/?","/"+database+"?"));
                ReflectionTestUtils.setField(service,"datasourceUsername","root");
                ReflectionTestUtils.setField(service,"datasourcePassword",password);
                ReflectionTestUtils.setField(service,"backupTlsCa",System.getenv("BACKUP_TLS_CA"));
                Path dump=temp.resolve("round-trip.sql");
                ReflectionTestUtils.invokeMethod(service,"executeMysqlDump",database,dump.toString());
                statement.execute("DELETE FROM "+database+".sample");
                ReflectionTestUtils.invokeMethod(service,"executeMysqlRestore",database,dump.toString());
                try(var rows=statement.executeQuery("SELECT COUNT(*),SHA2(GROUP_CONCAT(CONCAT(id,HEX(payload),IFNULL(HEX(optional_value),'NULL')) ORDER BY id),256) FROM "+database+".sample")) {
                    assertTrue(rows.next());assertEquals(2,rows.getInt(1));assertEquals(expected,rows.getString(2));
                }
                Path invalid=temp.resolve("invalid.sql");
                Files.writeString(invalid,"INSERT INTO sample VALUES (3,'before error',NULL);\nINSERT INTO missing_table VALUES (1);\nINSERT INTO sample VALUES (4,'after error',NULL);\n");
                assertThrows(RuntimeException.class,()->ReflectionTestUtils.invokeMethod(service,"executeMysqlRestore",database,invalid.toString()));
                try(var rows=statement.executeQuery("SELECT COUNT(*) FROM "+database+".sample WHERE id=4")) {assertTrue(rows.next());assertEquals(0,rows.getInt(1));}
            } finally {statement.execute("DROP DATABASE "+database);}
        }
    }
}
