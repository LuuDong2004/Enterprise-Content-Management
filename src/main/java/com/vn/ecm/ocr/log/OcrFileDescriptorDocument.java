package com.vn.ecm.ocr.log;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.HashIndexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * MongoDB document that stores OCR extraction metadata.
 * This class is intentionally separate from the {@link OcrFileDescriptor}
 * Jmix entity in order to persist OCR results in MongoDB while the UI
 * continues to work with standard Jmix DTOs/entities.
 */
@Document(collection = "ocrFileDescriptors")
public class OcrFileDescriptorDocument {

    @Id
    private String id;

    @HashIndexed
    private String ocrFileDescriptorId;

    @TextIndexed(weight = 2)
    private String fileName;

    @TextIndexed(weight = 5)
    private String extractedText;

    public String getExtractedText() {
        return extractedText;
    }

    public void setExtractedText(String extractedText) {
        this.extractedText = extractedText;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getOcrFileDescriptorId() {
        return ocrFileDescriptorId;
    }

    public void setOcrFileDescriptorId(String ocrFileDescriptorId) {
        this.ocrFileDescriptorId = ocrFileDescriptorId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}