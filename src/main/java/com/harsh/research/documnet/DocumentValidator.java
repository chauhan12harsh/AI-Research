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

@Component
public class DocumentValidator {

    private final LimitProperties limitProperties;
    private Tika tika = new Tika();

    public DocumentValidator(LimitProperties limitProperties) {
        this.limitProperties = limitProperties;
    }

    public record ValidateUpload(
            SourceType sourceType,
            String mimeType,
            long sizeBytes,
            String checksumSha256
    ){}

    public ValidateUpload validate(MultipartFile file) throws Exception {

        if(file.isEmpty()){
            throw new Exception("The uploaded File is empty");
        }

        if(file.getSize() > limitProperties.maxFileBytes()){
            throw new Exception("The uploaded file is too large");
        }

        if(!"pdf".equals(extensionOf(file.getOriginalFilename()))){
            throw new Exception("The uploaded file is not a pdf file");
        }


        String detectMime = detectMime(file);
        if(!"application/pdf".equals(detectMime)){
            throw new Exception("The file's content doesn't matches its extension");
        }

        assertReadablePdf(file);

        String checksum = sha256(file);

         ValidateUpload validateUpload = new ValidateUpload(
                SourceType.PDF,
                detectMime,
                file.getSize(),
                checksum
        );

        return validateUpload;
    }

    private String extensionOf(String filename){
        if(filename == null || !filename.contains(".")){
            return "";
        }

        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    private String detectMime(MultipartFile file) throws Exception {

        try (InputStream in = file.getInputStream()) {

            return tika.detect(in, file.getOriginalFilename());

        } catch (IOException e) {
            throw new Exception("this file is not supported");
        }
    }

    private void assertReadablePdf(MultipartFile file) throws Exception {

        try {
            byte[] bytes = file.getBytes();

            try (PDDocument document = Loader.loadPDF(bytes)){

                if(document.isEncrypted()){
                    throw new Exception("This pdf is password protected");
                }

                if(document.getNumberOfPages() > limitProperties.maxPdfPages()){
                    throw new Exception("This pdf has more than " + limitProperties.maxPdfPages() + "pages");
                }


            }catch (InvalidPasswordException e){
                throw new Exception("This pdf is password protected");
            }

        } catch (IOException e) {
            throw new Exception("This pdf is password protected");
        }
    }

    private String sha256(MultipartFile file) throws Exception {

        try(InputStream in = file.getInputStream()){

            MessageDigest digest = MessageDigest.getInstance(
                    "SHA-256"
            );

            try(DigestInputStream digestInputStream = new DigestInputStream(in, digest)){
                digestInputStream.transferTo(
                        OutputStream.nullOutputStream()
                );
            }

            return HexFormat.of().formatHex(digest.digest());

        } catch (IOException | NoSuchAlgorithmException e) {
            throw new Exception("Could not read uploaded file");
        }
    }

}
