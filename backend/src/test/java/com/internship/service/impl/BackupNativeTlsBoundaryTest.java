package com.internship.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import java.io.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Executes real backup service process paths against a disposable loopback TLS fixture, never a database. */
@EnabledIfEnvironmentVariable(named="SECURITY_NATIVE_MYSQL_TESTS", matches="true")
class BackupNativeTlsBoundaryTest {
    @TempDir Path temp;
    @ParameterizedTest
    @CsvSource({"dump,match", "dump,mismatch", "dump,untrusted", "dump,no-tls", "restore,match", "restore,mismatch", "restore,untrusted", "restore,no-tls"})
    void validatesIdentityBeforeAuthentication(String operation,String scenario) throws Exception {
        Path fixture=Path.of("src/test/security/mysql_tls_boundary.py").toAbsolutePath();
        var process=new ProcessBuilder("python",fixture.toString(),"--work-dir",temp.toString(),"--serve-case",scenario).redirectErrorStream(true).start();
        try(var output=new BufferedReader(new InputStreamReader(process.getInputStream(),java.nio.charset.StandardCharsets.UTF_8))) {
            var json=new ObjectMapper();var address=json.readTree(output.readLine());
            var service=new BackupServiceImpl();
            ReflectionTestUtils.setField(service,"datasourceUrl","jdbc:mysql://127.0.0.1:"+address.path("port").asInt()+"/fixture_database");
            ReflectionTestUtils.setField(service,"datasourceUsername","tls_fixture");
            ReflectionTestUtils.setField(service,"datasourcePassword","synthetic-fixture-password");
            ReflectionTestUtils.setField(service,"backupTlsCa",address.path("ca").asText());
            Path sql=temp.resolve("fixture.sql");Files.writeString(sql,"SELECT 1;\n");
            // The fixture deliberately rejects authentication after TLS. No SQL reaches any database.
            var failure=assertThrows(RuntimeException.class,()->ReflectionTestUtils.invokeMethod(service,
                    operation.equals("dump")?"executeMysqlDump":"executeMysqlRestore","fixture_database",sql.toString()));
            assertTrue(process.waitFor(15,TimeUnit.SECONDS),"TLS fixture timed out");
            var result=json.readTree(output.readLine());
            assertEquals(scenario.equals("match"),result.path("authenticated").asBoolean());
            assertEquals(0,process.exitValue());
            if(scenario.equals("match")) assertTrue(failure.getMessage().contains("fixture completed"),failure.getMessage());
        } finally {process.destroyForcibly();}
    }
}
