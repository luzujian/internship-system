package com.internship.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.converter.WordToHtmlConverter;
import org.springframework.stereotype.Component;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** 使用 LibreOffice 将旧版 Word .doc 转为可在浏览器中预览的 PDF。 */
@Slf4j
@Component
public class LegacyWordPreviewConverter {
    /** Convert legacy Word bytes without relying on LibreOffice in the application container. */
    public String convertToHtml(byte[] docBytes) throws IOException {
        try (HWPFDocument word = new HWPFDocument(new ByteArrayInputStream(docBytes))) {
            var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            WordToHtmlConverter converter = new WordToHtmlConverter(document);
            converter.processDocument(word);

            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.METHOD, "html");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter output = new StringWriter();
            transformer.transform(new DOMSource(converter.getDocument()), new StreamResult(output));
            return output.toString();
        } catch (Exception e) {
            throw new IOException("无法解析旧版 Word 文档", e);
        }
    }


    public byte[] convertToPdf(byte[] docBytes) throws IOException {
        Path tempDirectory = Files.createTempDirectory("appraisal_doc_preview_");
        try {
            Path sourceFile = tempDirectory.resolve("preview.doc");
            Path outputFile = tempDirectory.resolve("preview.pdf");
            Path userProfile = tempDirectory.resolve("lo-profile");
            Files.write(sourceFile, docBytes);

            String officeCommand = resolveOfficeCommand();
            Process process = new ProcessBuilder(
                    officeCommand,
                    "-env:UserInstallation=" + userProfile.toUri(),
                    "--headless",
                    "--nologo",
                    "--nodefault",
                    "--nofirststartwizard",
                    "--nolockcheck",
                    "--convert-to", "pdf:writer_pdf_Export",
                    "--outdir", tempDirectory.toString(),
                    sourceFile.toString())
                    .redirectErrorStream(true)
                    .start();

            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("LibreOffice 转换 .doc 超时");
            }
            String processOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new IOException("LibreOffice 转换 .doc 失败: " + processOutput);
            }
            if (!Files.isRegularFile(outputFile)) {
                throw new IOException("LibreOffice 未生成 PDF: " + processOutput);
            }

            byte[] pdfBytes = Files.readAllBytes(outputFile);
            if (pdfBytes.length < 5
                    || !new String(pdfBytes, 0, 5, StandardCharsets.US_ASCII).startsWith("%PDF-")) {
                throw new IOException("LibreOffice 输出不是有效 PDF");
            }
            return pdfBytes;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("LibreOffice 转换被中断", e);
        } finally {
            deleteDirectory(tempDirectory);
        }
    }

    private String resolveOfficeCommand() throws IOException {
        List<String> candidates = new ArrayList<>();
        String envConfiguredCommand = System.getProperty("LIBREOFFICE_BIN");
        if (envConfiguredCommand == null || envConfiguredCommand.isBlank()) {
            envConfiguredCommand = System.getProperty("file.preview.office-command");
        }
        if (envConfiguredCommand == null || envConfiguredCommand.isBlank()) {
            envConfiguredCommand = System.getenv("LIBREOFFICE_BIN");
        }
        if (envConfiguredCommand != null && !envConfiguredCommand.isBlank()) {
            candidates.add(envConfiguredCommand);
        }
        candidates.addAll(Arrays.asList(
                "C:\\Program Files\\LibreOffice\\program\\soffice.com",
                "C:\\Program Files\\LibreOffice\\program\\soffice.exe"
        ));
        candidates.addAll(Arrays.asList("soffice.com", "soffice.exe", "soffice", "libreoffice"));

        for (String candidate : candidates) {
            Process process = null;
            try {
                process = new ProcessBuilder(candidate, "--headless", "--version")
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start();
                if (process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0) {
                    return candidate;
                }
                if (process.isAlive()) process.destroyForcibly();
            } catch (IOException e) {
                log.debug("LibreOffice 命令不可用: {}", candidate);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("检测 LibreOffice 命令被中断", e);
            } finally {
                if (process != null && process.isAlive()) process.destroyForcibly();
            }
        }
        throw new IOException("未找到 LibreOffice，请安装 LibreOffice 或设置 LIBREOFFICE_BIN");
    }

    private void deleteDirectory(Path directory) {
        if (directory == null || !Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("清理鉴定表预览临时文件失败: {}", path, e);
                }
            });
        } catch (IOException e) {
            log.warn("清理鉴定表预览临时目录失败: {}", directory, e);
        }
    }
}
