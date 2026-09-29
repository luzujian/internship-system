package com.internship.utils;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.PutObjectRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AliyunOSSOperatorResourceReadTest {
    private static final String ENDPOINT = "https://oss-cn-beijing.aliyuncs.com";
    private static final String BUCKET = "test-bucket";
    private static final String KEY = "resources/doc/2026/09/11111111-1111-1111-1111-111111111111.pdf";
    private static final String URL = "https://" + BUCKET + ".oss-cn-beijing.aliyuncs.com/" + KEY;

    @Test
    void localCanReadOnlyTheConfiguredBucketResourceSelectedByRecord() throws Exception {
        RecordingOperator oss = configuredOperator();

        assertArrayEquals(new byte[]{1, 2, 3}, oss.downloadResourceDocumentFile(URL));
        assertEquals(ENDPOINT, oss.endpoint);
        assertEquals(BUCKET, oss.bucket);
        assertEquals(KEY, oss.objectName);

        assertThrows(IllegalArgumentException.class, () -> oss.downloadResourceDocumentFile(
                "https://other-bucket.oss-cn-beijing.aliyuncs.com/" + KEY));
        assertThrows(IllegalArgumentException.class, () -> oss.downloadResourceDocumentFile(
                URL + "?download=1"));
        assertThrows(IllegalArgumentException.class, () -> oss.downloadResourceDocumentFile(
                "https://" + BUCKET + ".oss-cn-beijing.aliyuncs.com/backups/database.sql"));
        assertThrows(IllegalArgumentException.class, () -> oss.downloadResourceDocumentFile(
                "http://" + BUCKET + ".oss-cn-beijing.aliyuncs.com/" + KEY));
        assertThrows(IllegalArgumentException.class, () -> oss.downloadResourceDocumentFile(
                "https://" + BUCKET + ".oss-cn-beijing.aliyuncs.com/resources/doc/../" + KEY));
        assertEquals(KEY, oss.objectName, "rejected URLs must not reach OSS");
    }

    @Test
    void genericReadAndResourceReadUseTheSameConfiguredOssPath() throws Exception {
        RecordingOperator oss = configuredOperator();
        assertArrayEquals(new byte[]{1, 2, 3}, oss.downloadFile(URL));
        assertEquals(KEY, oss.objectName);
        assertArrayEquals(new byte[]{1, 2, 3}, oss.downloadResourceDocumentFile(URL));
        assertEquals(KEY, oss.objectName);
    }

    @Test
    void uploadsDeletesAndRecordBoundReadsUseMockOssWithoutLocalOnlyDenial() throws Exception {
        AliyunOSSOperator oss = configuredOperator();
        OSS client = mock(OSS.class);
        OSSObject object = mock(OSSObject.class);
        when(object.getObjectContent()).thenReturn(new ByteArrayInputStream(new byte[]{7, 8}));
        when(client.getObject(eq(BUCKET), anyString())).thenReturn(object);
        try (var ignored = mockConstruction(OSSClientBuilder.class,
                (builder, context) -> when(builder.build(anyString(), anyString(), anyString())).thenReturn(client))) {
            assertTrue(oss.upload(new byte[]{1}, "file.pdf").startsWith(
                    "https://" + BUCKET + ".oss-cn-beijing.aliyuncs.com/files/"));
            assertEquals(URL, oss.uploadWithObjectName(new byte[]{1}, KEY));
            assertArrayEquals(new byte[]{7, 8}, oss.downloadRecordAttachment(
                    "https://" + BUCKET + ".oss-cn-beijing.aliyuncs.com/"
                            + "student/applications/1/files/2026/09/11111111-1111-1111-1111-111111111111.pdf", 1L));
            oss.deleteFile(URL);
        }
        verify(client, times(2)).putObject(any(PutObjectRequest.class));
        verify(client).getObject(eq(BUCKET), startsWith("student/applications/1/files/"));
        verify(client).deleteObject(BUCKET, KEY);
    }

    private RecordingOperator configuredOperator() {
        var properties = new AliyunOSSProperties();
        properties.setEndpoint(ENDPOINT);
        properties.setBucketName(BUCKET);
        properties.setAccessKeyId("test-id");
        properties.setAccessKeySecret("test-secret");
        var oss = new RecordingOperator();
        ReflectionTestUtils.setField(oss, "aliyunOSSProperties", properties);
        return oss;
    }

    private static class RecordingOperator extends AliyunOSSOperator {
        private String endpoint;
        private String bucket;
        private String objectName;

        @Override
        protected byte[] downloadObject(String endpoint, String bucketName, String objectName) {
            this.endpoint = endpoint;
            this.bucket = bucketName;
            this.objectName = objectName;
            return new byte[]{1, 2, 3};
        }
    }
}
