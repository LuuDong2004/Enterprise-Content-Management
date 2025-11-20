package com.vn.ecm.ocr.log;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class OcrFileDescriptorService {

    private final String tessDataPath;

    public OcrFileDescriptorService() {
        String env =  System.getenv("TESSDATA_PREFIX");
        this.tessDataPath = (env != null && !env.isBlank())
                ? env
                : "C:\\Program Files\\Tesseract-OCR\\";
    }

    public String extractText(File file) throws TesseractException {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath("C:/Program Files/Tesseract-OCR/tessdata");
        tesseract.setLanguage("vie");
        tesseract.setPageSegMode(3);
        return tesseract.doOCR(file);
    }
}
