package com.internship.importing;

import com.internship.agent.business.AgentException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PrivateImportAttachmentStorageTest {
    @TempDir Path directory;
    @Test void originalExcelIsRetainedUnderOpaquePrivateKeyWithExactHash() throws Exception {
        byte[] bytes;
        try(var workbook=new XSSFWorkbook();var output=new ByteArrayOutputStream()){
            workbook.createSheet("名单").createRow(0).createCell(0).setCellValue("00123");workbook.write(output);bytes=output.toByteArray();
        }
        Path root=directory.resolve("private");var storage=new PrivateImportAttachmentStorage(root,directory.resolve("uploads"));String id=UUID.randomUUID().toString();
        var manifest=storage.persist(id,List.of(new MockMultipartFile("files","../../名单.xlsx","application/octet-stream",bytes)));
        var file=manifest.getFirst();assertEquals("名单.xlsx",file.name());assertEquals(bytes.length,file.size());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),file.sha256());
        assertArrayEquals(bytes,Files.readAllBytes(root.resolve(file.storageKey())));assertFalse(file.storageKey().contains("名单"));
        assertFalse(Files.exists(directory.resolve("uploads")));storage.rollbackNew(id);assertFalse(Files.exists(root.resolve(id)));
    }
    @Test void publicUploadDirectoryAndTraversalAreRejected() {
        Path uploads=directory.resolve("uploads");assertThrows(IllegalArgumentException.class,()->new PrivateImportAttachmentStorage(uploads.resolve("agent-private"),uploads));
        var storage=new PrivateImportAttachmentStorage(directory.resolve("private"),uploads);
        assertDoesNotThrow(()->storage.rollbackNew("../../uploads"));assertFalse(Files.exists(uploads));
    }
    @Test void oversizedOrInvalidUploadLeavesNoCommittedDirectory() throws Exception {
        Path root=directory.resolve("private");var storage=new PrivateImportAttachmentStorage(root,directory.resolve("uploads"));String id=UUID.randomUUID().toString();
        assertThrows(AgentException.class,()->storage.persist(id,List.of(new MockMultipartFile("files","名单.xlsx","x",new byte[10*1024*1024+1]))));
        assertFalse(Files.exists(root.resolve(id)));try(var contents=Files.list(root)){assertEquals(0,contents.count());}
    }
}
