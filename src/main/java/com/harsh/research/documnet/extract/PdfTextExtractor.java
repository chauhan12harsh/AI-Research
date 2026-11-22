package com.harsh.research.documnet.extract;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;


@Component
public class PdfTextExtractor {
    public ExtractionResult extract(InputStream inputStream) throws IOException {

        ByteArrayOutputStream buffer =  new ByteArrayOutputStream();

        inputStream.transferTo(buffer);

        byte[] bytes = buffer.toByteArray();

        try(PDDocument document = Loader.loadPDF(bytes)) {

            if(document.isEncrypted()){
                throw new IOException("Pdf is encrypted");
            }

            int pageCount = document.getNumberOfPages();

            PDFTextStripper stripper = new PDFTextStripper();

            StringBuilder text = new StringBuilder();

            for (int page = 1; page <= pageCount ; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String extractedText = stripper.getText(document);

                if(page > 1){
                    text.append("\f");
                }
                text.append(extractedText);
            }

            return ExtractionResult.of(text.toString(),pageCount);
            
        }catch(InvalidPasswordException e){
            throw new IOException("PDF is password protected");
        }

    }

}
