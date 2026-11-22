package com.harsh.research.documnet;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class DocumentValidator {

    private final LimitProperties limitProperties;
    private final Tika tika = new Tika();

    public DocumentValidator(LimitProperties limitProperties) {
        this.limitProperties = limitProperties;
    }

    public record ValidateUpload(
            SourceType sourceType,
            String mimeType,
            long sizeBytes,
            String checksumSha256
    ) {}

    public ValidateUpload validate(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("The uploaded file is empty");
        }

        if (file.getSize() > limitProperties.maxFileBytes()) {
            throw new IllegalArgumentException("The uploaded file is too large");
        }

        if (!"pdf".equals(extensionOf(file.getOriginalFilename()))) {
            throw new IllegalArgumentException("Only PDF files are supported");
        }

        String detectedMimeType = detectMime(file);
        if (!"application/pdf".equalsIgnoreCase(detectedMimeType)) {
            throw new IllegalArgumentException(
                    "The file content does not match the PDF extension"
            );
        }

        assertReadablePdf(file);

        return new ValidateUpload(
                SourceType.PDF,
                detectedMimeType,
                file.getSize(),
                sha256(file)
        );
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String detectMime(MultipartFile file) throws IOException {
        try (InputStream input = file.getInputStream()) {
            return tika.detect(input, file.getOriginalFilename());
        }
    }

    private void assertReadablePdf(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();

        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) {
                throw new IllegalArgumentException(
                        "Password-protected PDF files are not supported"
                );
            }

            if (document.getNumberOfPages() > limitProperties.maxPdfPages()) {
                throw new IllegalArgumentException(
                        "PDF exceeds the maximum of "
                                + limitProperties.maxPdfPages() + " pages"
                );
            }
        } catch (InvalidPasswordException exception) {
            throw new IllegalArgumentException(
                    "Password-protected PDF files are not supported",
                    exception
            );
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "The uploaded PDF is corrupt or cannot be opened",
                    exception
            );
        }
    }

    private String sha256(MultipartFile file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            try (InputStream input = file.getInputStream();
                 DigestInputStream digestInput = new DigestInputStream(input, digest)) {
                digestInput.transferTo(OutputStream.nullOutputStream());
            }

            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
