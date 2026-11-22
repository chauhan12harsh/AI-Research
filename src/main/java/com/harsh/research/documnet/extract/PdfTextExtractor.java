package com.harsh.research.documnet.extract;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

@Component
public class PdfTextExtractor {

    private static final int OCR_DPI = 200;
    private static final int MIN_NATIVE_TEXT_CHARACTERS = 24;

    @Value("${app.ocr.enabled:false}")
    private boolean ocrEnabled;

    @Value("${app.ocr.datapath:}")
    private String ocrDataPath;

    @Value("${app.ocr.language:eng}")
    private String ocrLanguage;

    @Value("${app.limits.max-pdf-pages:800}")
    private int maxPdfPages;

    public ExtractionResult extract(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        inputStream.transferTo(buffer);

        try (PDDocument document = Loader.loadPDF(buffer.toByteArray())) {
            if (document.isEncrypted()) {
                throw new IOException("PDF is encrypted");
            }

            int pageCount = document.getNumberOfPages();
            if (pageCount > maxPdfPages) {
                throw new IOException("PDF exceeds maximum page count");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = ocrEnabled ? new PDFRenderer(document) : null;
            Tesseract tesseract = ocrEnabled ? createTesseract() : null;
            StringBuilder text = new StringBuilder();

            for (int page = 1; page <= pageCount; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(document);

                // OCR only pages with little/no embedded text. This also supports
                // PDFs containing a mixture of scanned and digitally generated pages.
                if (ocrEnabled && pageText.strip().length() < MIN_NATIVE_TEXT_CHARACTERS) {
                    BufferedImage image = renderer.renderImageWithDPI(
                            page - 1, OCR_DPI, ImageType.GRAY);
                    try {
                        String recognized = tesseract.doOCR(image);
                        if (recognized != null && !recognized.isBlank()) {
                            pageText = recognized;
                        }
                    } catch (TesseractException exception) {
                        throw new IOException("OCR failed on PDF page " + page, exception);
                    } finally {
                        image.flush();
                    }
                }

                if (page > 1) {
                    text.append("\f");
                }
                text.append(pageText);
            }

            return ExtractionResult.of(text.toString(), pageCount);
        } catch (InvalidPasswordException exception) {
            throw new IOException("PDF is password protected", exception);
        }
    }

    private Tesseract createTesseract() throws IOException {
        Tesseract tesseract = new Tesseract();
        if (ocrDataPath == null || ocrDataPath.isBlank()) {
            throw new IOException("OCR is enabled but app.ocr.datapath is not configured");
        }
        tesseract.setDatapath(ocrDataPath);
        tesseract.setLanguage(ocrLanguage);
        return tesseract;
    }
}
