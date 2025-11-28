package com.vn.ecm.ocr.log;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

@Service
public class OcrFileDescriptorService {

    private static final Logger log = LoggerFactory.getLogger(OcrFileDescriptorService.class);

    private final String tessDataPath;

    public OcrFileDescriptorService() {
        String env = System.getenv("TESSDATA_PREFIX");
        this.tessDataPath = (env != null && !env.isBlank())
                ? env
                : "C:\\Program Files\\Tesseract-OCR\\";
    }

    private Tesseract createTesseractInstance() {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessDataPath + "/tessdata");
        tesseract.setLanguage("vie");
        tesseract.setPageSegMode(3);
        return tesseract;
    }

    public String extractText(File file) throws TesseractException {
        Tesseract tesseract = createTesseractInstance();
        return tesseract.doOCR(file);
    }

    public String extractTextFromPdf(File pdfFile) throws IOException, TesseractException {
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            // Thử extract text trực tiếp từ PDF
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(document.getNumberOfPages());
            String extractedText = stripper.getText(document);
            // Kiểm tra xem PDF có text sẵn hay không
            // Nếu text được extract ít hoặc rỗng, có thể là PDF scan
            if (extractedText != null && extractedText.trim().length() > 50) {
                // PDF có text sẵn, trả về kết quả
                log.debug("PDF {} has text content, extracted {} characters", pdfFile.getName(),
                        extractedText.length());
                return extractedText;
            } else {
                // PDF có thể là scan/ảnh, cần OCR
                log.debug("PDF {} appears to be scanned/image-based, using OCR", pdfFile.getName());
                return extractTextFromPdfUsingOcr(document, pdfFile);
            }
        } catch (IOException e) {
            log.error("Error extracting text from PDF file: {}", pdfFile.getName(), e);
            throw e;
        }
    }

    // convert pdf sang ảnh
    private String extractTextFromPdfUsingOcr(PDDocument document, File pdfFile)
            throws IOException, TesseractException {
        PDFRenderer pdfRenderer = new PDFRenderer(document);
        StringBuilder fullText = new StringBuilder();
        Tesseract tesseract = createTesseractInstance();

        int totalPages = document.getNumberOfPages();
        log.debug("Processing {} pages of PDF {} using OCR", totalPages, pdfFile.getName());

        for (int page = 0; page < totalPages; page++) {
            try {
                // Render PDF page thành ảnh với DPI cao để OCR chính xác hơn
                BufferedImage image = pdfRenderer.renderImageWithDPI(page, 300);
                // OCR từng trang
                String pageText = tesseract.doOCR(image);
                if (pageText != null && !pageText.trim().isEmpty()) {
                    fullText.append(pageText);
                    if (page < totalPages - 1) {
                        fullText.append("\n\n"); // Thêm khoảng trắng giữa các trang
                    }
                }
                log.debug("OCR completed for page {}/{}", page + 1, totalPages);
            } catch (Exception e) {
                log.warn("Error OCR page {} of PDF {}: {}", page + 1, pdfFile.getName(), e.getMessage());
                // Tiếp tục với trang tiếp theo nếu có lỗi
            }
        }
        return fullText.toString();
    }
}
