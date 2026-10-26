package com.internship.importing;

import com.internship.agent.business.AgentException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.*;

/** No web resource mapping or public URL exists for this directory. Java owns all access. */
@Component
public class PrivateImportAttachmentStorage {
    public record Attachment(String id, String name, long size, String sha256, String storageKey) {}
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private final Path root;

    @Autowired
    public PrivateImportAttachmentStorage(@Value("${agent.import.private-dir:./agent-private/imports}") String directory,
                                          @Value("${file.upload.path:./uploads}") String publicDirectory) {
        this(Path.of(directory), Path.of(publicDirectory));
    }
    PrivateImportAttachmentStorage(Path directory, Path publicDirectory) {
        root = directory.toAbsolutePath().normalize();
        Path publicRoot = publicDirectory.toAbsolutePath().normalize();
        if (root.startsWith(publicRoot) || publicRoot.startsWith(root))
            throw new IllegalArgumentException("agent.import.private-dir must be separate from the public upload directory");
        for (Path webRoot : List.of(Path.of("src/main/resources/static"),Path.of("src/main/resources/public"),
                Path.of("backend/src/main/resources/static"),Path.of("backend/src/main/resources/public"))) {
            if (root.startsWith(webRoot.toAbsolutePath().normalize()))
                throw new IllegalArgumentException("Private import files cannot be stored in a web resource directory");
        }
    }

    public List<Attachment> persist(String contextId, List<MultipartFile> files) {
        UUID.fromString(contextId);
        if (files == null || files.isEmpty() || files.size() > 5) throw invalid();
        Path staging = null;
        try {
            assertNoLinks(root); Files.createDirectories(root); privatePermissions(root,true);
            staging = Files.createTempDirectory(root,".upload-"); privatePermissions(staging,true);
            List<Attachment> attachments = new ArrayList<>(); long total = 0;
            for (MultipartFile file : files) {
                String name = Objects.toString(file.getOriginalFilename(),"").replace('\\','/');
                name = name.substring(name.lastIndexOf('/') + 1);
                String lower = name.toLowerCase(Locale.ROOT);
                String extension = lower.endsWith(".xlsx") ? ".xlsx" : lower.endsWith(".xls") ? ".xls" : null;
                if (extension == null || name.length() > 512 || file.isEmpty()) throw invalid();
                String fileId = UUID.randomUUID().toString(); Path target = staging.resolve(fileId + extension);
                MessageDigest digest = MessageDigest.getInstance("SHA-256"); long size = 0;
                try (InputStream input = file.getInputStream(); FileChannel output = FileChannel.open(target,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
                    privatePermissions(target,false); byte[] bytes = new byte[8192]; int count;
                    while ((count = input.read(bytes)) != -1) {
                        size += count; total += count; if (total > MAX_BYTES) throw invalid();
                        digest.update(bytes,0,count); ByteBuffer buffer = ByteBuffer.wrap(bytes,0,count);
                        while (buffer.hasRemaining()) output.write(buffer);
                    }
                    output.force(true);
                }
                if (size == 0) throw invalid();
                attachments.add(new Attachment(fileId,name,size,HexFormat.of().formatHex(digest.digest()),contextId + "/" + fileId + extension));
            }
            Path destination = workspace(contextId);
            Files.move(staging,destination,StandardCopyOption.ATOMIC_MOVE); staging = null;
            return List.copyOf(attachments);
        } catch (AgentException e) { throw e; }
        catch (Exception e) { AgentException failure=unavailable();failure.initCause(e);throw failure; }
        finally { if (staging != null) deleteFlat(staging); }
    }

    /** Used only to roll back a newly created workspace, never to remove business receipts. */
    public void rollbackNew(String contextId) {
        try { Path directory = workspace(contextId); assertNoLinks(directory); deleteFlat(directory); }
        catch (Exception ignored) { /* A failed cleanup is an orphan, never an exposed public file. */ }
    }
    private Path workspace(String id) {
        UUID.fromString(id); Path directory = root.resolve(id).normalize();
        if (!directory.startsWith(root) || !directory.getParent().equals(root)) throw new IllegalArgumentException("Invalid private workspace");
        return directory;
    }
    private static void assertNoLinks(Path path) throws java.io.IOException {
        for (Path current = path; current != null; current = current.getParent())
            if (Files.isSymbolicLink(current)) throw new java.io.IOException("Private attachment directories cannot be symbolic links");
    }
    private static void privatePermissions(Path path,boolean directory) throws java.io.IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix"))
            Files.setPosixFilePermissions(path,PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
        // Windows inherits the configured service-account directory ACL; no public file route is registered.
    }
    private static void deleteFlat(Path directory) {
        if (!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS)) return;
        try (var children = Files.list(directory)) {
            for (Path child : children.toList()) if (!Files.isDirectory(child,LinkOption.NOFOLLOW_LINKS)) Files.deleteIfExists(child);
            Files.deleteIfExists(directory);
        } catch (Exception ignored) { }
    }
    private static AgentException invalid() { return new AgentException("IMPORT_INVALID","请上传1至5个非空 Excel 文件，总大小不超过10MB。",400); }
    private static AgentException unavailable() { return new AgentException("IMPORT_STORAGE_UNAVAILABLE","附件私有存储暂时不可用，请保留文件后重试。",503); }
}
