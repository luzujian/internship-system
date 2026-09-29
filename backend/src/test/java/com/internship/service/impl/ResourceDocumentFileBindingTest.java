package com.internship.service.impl;
import com.internship.entity.ResourceDocument;
import com.internship.mapper.ResourceDocumentMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ResourceDocumentFileBindingTest {
    @Test void metadataEditCannotReplaceOwnedFile() {
        var mapper = mock(ResourceDocumentMapper.class);
        var existing = new ResourceDocument(); existing.setFileUrl("own-file");
        when(mapper.findById(1L)).thenReturn(existing);
        var edit = new ResourceDocument(); edit.setId(1L); edit.setTitle("new title");
        edit.setPublisher("teacher"); edit.setPublisherRole("TEACHER"); edit.setStatus("DRAFT");
        edit.setFileUrl("another-owners-file");
        new ResourceDocumentServiceImpl(mapper).update(edit);
        assertEquals("own-file", edit.getFileUrl());
        assertEquals("new title", edit.getTitle());
        verify(mapper).update(edit);
    }
}
