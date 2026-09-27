package com.internship.controller;

import com.internship.utils.AliyunOSSOperator;
import com.internship.utils.LegacyWordPreviewConverter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileUploadControllerDocPreviewTest {

    @Test
    void legacyDocConversionAcceptsOnlyOleBytesAndDoesNotReadOss() throws Exception {
        FileUploadController controller = new FileUploadController();
        AliyunOSSOperator ossOperator = mock(AliyunOSSOperator.class);
        LegacyWordPreviewConverter converter = mock(LegacyWordPreviewConverter.class);
        ReflectionTestUtils.setField(controller, "aliyunOSSOperator", ossOperator);
        ReflectionTestUtils.setField(controller, "legacyWordPreviewConverter", converter);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(post("/api/upload/preview-legacy-doc")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content("not a Word file"))
                .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(converter, ossOperator);

        byte[] doc = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1, 0};
        when(converter.convertToHtml(any(byte[].class))).thenReturn("<p>preview</p>");
        mvc.perform(post("/api/upload/preview-legacy-doc")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM).content(doc))
                .andExpect(status().isOk())
                .andExpect(content().string("<p>preview</p>"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        verifyNoInteractions(ossOperator);
    }

    @Test
    void legacyDocConversionRejectsOversizedBodyBeforeParsing() {
        FileUploadController controller = new FileUploadController();
        LegacyWordPreviewConverter converter = mock(LegacyWordPreviewConverter.class);
        ReflectionTestUtils.setField(controller, "legacyWordPreviewConverter", converter);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContentLengthLong()).thenReturn(10L * 1024 * 1024 + 1);

        assertEquals(413, controller.previewLegacyDoc(request).getStatusCode().value());
        verifyNoInteractions(converter);
    }

    @Test
    void previewDocRejectsOrdinaryKeyWithoutAReferencedBusinessRecord() {
        FileUploadController controller = new FileUploadController();
        AliyunOSSOperator ossOperator = mock(AliyunOSSOperator.class);
        LegacyWordPreviewConverter converter = mock(LegacyWordPreviewConverter.class);
        ReflectionTestUtils.setField(controller, "aliyunOSSOperator", ossOperator);
        ReflectionTestUtils.setField(controller, "legacyWordPreviewConverter", converter);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(
                org.springframework.web.servlet.HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE))
                .thenReturn("/api/upload/preview-docx/resources/sample.doc");

        ResponseEntity<byte[]> response = controller.previewDocxFile(request);

        assertEquals(403, response.getStatusCode().value());
        verifyNoInteractions(ossOperator, converter);
    }

    @Test
    void previewDocEndpointRejectsAnOrdinaryHttpRequestBeforeOssAccess() throws Exception {
        FileUploadController controller = new FileUploadController();
        AliyunOSSOperator ossOperator = mock(AliyunOSSOperator.class);
        LegacyWordPreviewConverter converter = mock(LegacyWordPreviewConverter.class);
        ReflectionTestUtils.setField(controller, "aliyunOSSOperator", ossOperator);
        ReflectionTestUtils.setField(controller, "legacyWordPreviewConverter", converter);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/api/upload/preview-docx/resources/sample.doc"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(ossOperator, converter);
    }
}
