package com.vn.ecm.ocr.log;

import io.jmix.core.entity.annotation.JmixId;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.JmixProperty;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@JmixEntity
public class OcrFileDescriptor {

    @JmixId
    private UUID id;

    private String fileName;

    private String extractedText;

    @NotNull
    @JmixProperty(mandatory = true)
    private OcrFileDescriptor ocrfiledescriptor;

    public void setOcrfiledescriptor(OcrFileDescriptor ocrfiledescriptor) {
        this.ocrfiledescriptor = ocrfiledescriptor;
    }

    public OcrFileDescriptor getOcrfiledescriptor() {
        return ocrfiledescriptor;
    }

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

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

}