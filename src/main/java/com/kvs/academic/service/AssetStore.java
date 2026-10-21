package com.kvs.academic.service;

import com.kvs.academic.enums.AssetType;
import com.kvs.core.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * File store for e-learning assets (US-06005): PDF notes, MP4 lecture videos, EPUB e-books and SCORM packages.
 * The declared type must match the file's leading bytes, a SCORM package must contain imsmanifest.xml, and file names are
 * generated server-side so client-supplied names never reach the file system.
 */
@Component
@Slf4j
public class AssetStore {

    /** Content type → asset type and stored extension. */
    static final Map<String, Object[]> TYPES = Map.of(
            "application/pdf", new Object[]{AssetType.PDF, ".pdf"},
            "video/mp4", new Object[]{AssetType.VIDEO, ".mp4"},
            "application/epub+zip", new Object[]{AssetType.EBOOK, ".epub"},
            "application/zip", new Object[]{AssetType.SCORM, ".zip"},
            "application/x-zip-compressed", new Object[]{AssetType.SCORM, ".zip"});

    private final Path basePath;
    private final long maxFileSizeBytes;

    public AssetStore(@Value("${academic.asset-store.base-path:./lms-assets}") String basePath,
                      @Value("${academic.asset-store.max-file-size-bytes:524288000}") long maxFileSizeBytes) {
        this.basePath = Path.of(basePath).toAbsolutePath().normalize();
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    /** The asset type a content type stores as, or a 400 listing the accepted types. */
    public AssetType typeOf(String contentType) {
        Object[] type = TYPES.get(contentType);
        if (type == null) {
            throw new BadRequestException("Unsupported file type " + contentType + ". Allowed types: " + TYPES.keySet(), "UNSUPPORTED_FILE_TYPE");
        }
        return (AssetType) type[0];
    }

    public StoredAsset store(String tenantId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file must not be empty");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new BadRequestException("Uploaded file exceeds the maximum size of " + maxFileSizeBytes + " bytes", "FILE_TOO_LARGE");
        }
        AssetType type = typeOf(file.getContentType());
        if (!signatureMatches(file, type)) {
            throw new BadRequestException("File content does not match the declared type " + file.getContentType(), "CONTENT_MISMATCH");
        }
        if (type == AssetType.SCORM && !hasScormManifest(file)) {
            throw new BadRequestException("A SCORM package must contain imsmanifest.xml at its root", "INVALID_SCORM_PACKAGE");
        }
        String tenantSegment = tenantId.replaceAll("[^A-Za-z0-9_-]", "_");
        Path relative = Path.of(tenantSegment, UUID.randomUUID() + (String) TYPES.get(file.getContentType())[1]);
        Path target = basePath.resolve(relative).normalize();
        if (!target.startsWith(basePath)) {
            throw new BadRequestException("Invalid asset storage path");
        }
        try {
            Files.createDirectories(target.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new DigestInputStream(file.getInputStream(), digest)) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            log.debug("Stored LMS asset {} ({} bytes)", relative, file.getSize());
            return new StoredAsset(type, relative.toString().replace('\\', '/'), HexFormat.of().formatHex(digest.digest()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store the asset", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public byte[] read(String relativePath) {
        Path target = basePath.resolve(relativePath).normalize();
        if (!target.startsWith(basePath)) {
            throw new BadRequestException("Invalid asset storage path");
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new UncheckedIOException("The stored file could not be read", e);
        }
    }

    private static boolean signatureMatches(MultipartFile file, AssetType type) {
        try (InputStream in = file.getInputStream()) {
            byte[] header = in.readNBytes(12);
            return switch (type) {
                case PDF -> startsWith(header, new byte[]{'%', 'P', 'D', 'F'});
                // ISO base media file: bytes 4-7 are "ftyp"
                case VIDEO -> header.length >= 8 && Arrays.equals(Arrays.copyOfRange(header, 4, 8), new byte[]{'f', 't', 'y', 'p'});
                case EBOOK, SCORM -> startsWith(header, new byte[]{'P', 'K', 3, 4});
                default -> false;
            };
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the uploaded file", e);
        }
    }

    private static boolean startsWith(byte[] header, byte[] expected) {
        return header.length >= expected.length && Arrays.equals(Arrays.copyOf(header, expected.length), expected);
    }

    private static boolean hasScormManifest(MultipartFile file) {
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("imsmanifest.xml".equals(entry.getName())) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    public record StoredAsset(AssetType type, String relativePath, String checksumSha256) {
    }
}
