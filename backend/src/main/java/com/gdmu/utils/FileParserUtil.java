package com.gdmu.utils;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Component
public class FileParserUtil {

    public String parseFile(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        String fileExtension = getFileExtension(originalFilename).toLowerCase();

        return switch (fileExtension) {
            case "docx" -> parseWordDocx(file);
            case "doc" -> parseWordDoc(file);
            case "pdf" -> parsePdf(file);
            case "txt" -> parseText(file);
            default -> throw new IllegalArgumentException("不支持的文件格式: " + fileExtension);
        };
    }

    private String parseWordDocx(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream();
             XWPFDocument document = new XWPFDocument(inputStream)) {

            List<XWPFParagraph> paragraphs = document.getParagraphs();
            StringBuilder content = new StringBuilder();

            for (XWPFParagraph paragraph : paragraphs) {
                String text = paragraph.getText();
                if (text != null && !text.trim().isEmpty()) {
                    content.append(text).append("\n");
                }
            }

            return content.toString().trim();
        }
    }

    private String parseWordDoc(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream();
             org.apache.poi.hwpf.HWPFDocument document = new org.apache.poi.hwpf.HWPFDocument(inputStream)) {

            org.apache.poi.hwpf.extractor.WordExtractor extractor = new org.apache.poi.hwpf.extractor.WordExtractor(document);
            return extractor.getText().trim();
        }
    }

    private String parsePdf(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] bytes = inputStream.readAllBytes();
            try (PDDocument document = Loader.loadPDF(bytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                return stripper.getText(document).trim();
            }
        }
    }

    private String parseText(MultipartFile file) throws IOException {
        return new String(file.getBytes()).trim();
    }

    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1);
    }

    public boolean isSupportedFile(String filename) {
        String extension = getFileExtension(filename).toLowerCase();
        return extension.equals("docx") || extension.equals("doc") || 
               extension.equals("pdf") || extension.equals("txt");
    }
}
