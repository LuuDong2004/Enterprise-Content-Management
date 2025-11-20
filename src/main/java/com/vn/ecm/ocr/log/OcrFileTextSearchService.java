package com.vn.ecm.ocr.log;

import com.vn.ecm.entity.FileDescriptor;
import com.vn.ecm.entity.SourceStorage;
import io.jmix.core.DataManager;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Coordinates OCR extraction, persistence in MongoDB, and full-text search
 * capabilities.
 */
@Service
public class OcrFileTextSearchService {

    private static final Logger log = LoggerFactory.getLogger(OcrFileTextSearchService.class);

    private static final Set<String> SUPPORTED_IMAGE_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "bmp", "tif", "tiff", "gif", "webp");

    private final OcrFileDescriptorService ocrFileDescriptorService;
    private final OcrFileDescriptorRepository ocrFileDescriptorRepository;
    private final DataManager dataManager;

    public OcrFileTextSearchService(OcrFileDescriptorService ocrFileDescriptorService,
            OcrFileDescriptorRepository ocrFileDescriptorRepository,
            DataManager dataManager) {
        this.ocrFileDescriptorService = ocrFileDescriptorService;
        this.ocrFileDescriptorRepository = ocrFileDescriptorRepository;
        this.dataManager = dataManager;
    }

    /**
     * Performs OCR for the provided file and saves/updates the MongoDB document for
     * full-text search.
     *
     * @return Optional text that was extracted and saved. Empty if extraction is
     *         skipped or fails.
     */
    public Optional<String> indexFile(FileDescriptor descriptor, File imageFile) {
        if (!isEligible(descriptor, imageFile)) {
            return Optional.empty();
        }
        String extractedText = extractText(imageFile);
        if (!StringUtils.hasText(extractedText)) {
            return Optional.empty();
        }
        OcrFileDescriptorDocument document = ocrFileDescriptorRepository
                .findById(descriptor.getId().toString())
                .orElseGet(OcrFileDescriptorDocument::new);

        document.setId(descriptor.getId().toString());
        document.setOcrFileDescriptorId(descriptor.getId().toString());
        document.setFileName(descriptor.getName());
        document.setExtractedText(extractedText.trim());

        ocrFileDescriptorRepository.save(document);
        return Optional.of(document.getExtractedText());
    }

    public Optional<String> getExtractedText(UUID fileDescriptorId) {
        if (fileDescriptorId == null) {
            return Optional.empty();
        }
        return ocrFileDescriptorRepository.findById(fileDescriptorId.toString())
                .map(OcrFileDescriptorDocument::getExtractedText)
                .filter(StringUtils::hasText);
    }

    public List<FileDescriptor> searchFilesByText(String freeText) {
        return searchFilesByText(freeText, null);
    }

    public List<FileDescriptor> searchFilesByText(String freeText, SourceStorage scopeStorage) {
        if (!StringUtils.hasText(freeText)) {
            return List.of();
        }
        List<OcrFileDescriptorDocument> documents = ocrFileDescriptorRepository.searchFullText(freeText);
        List<UUID> fileIds = documents.stream()
                .map(OcrFileDescriptorDocument::getOcrFileDescriptorId)
                .filter(StringUtils::hasText)
                .map(UUID::fromString)
                .collect(Collectors.toList());

        if (fileIds.isEmpty()) {
            return List.of();
        }

        return dataManager.load(FileDescriptor.class)
                .query("select f from FileDescriptor f where f.id in :ids and f.inTrash = false " +
                        "and (:storage is null or f.sourceStorage = :storage)")
                .parameter("ids", fileIds)
                .parameter("storage", scopeStorage)
                .list();
    }

    private String extractText(File imageFile) {
        try {
            return ocrFileDescriptorService.extractText(imageFile);
        } catch (TesseractException e) {
            log.warn("OCR extraction failed for file {}", imageFile, e);
            return null;
        }
    }

    private boolean isEligible(FileDescriptor descriptor, File imageFile) {
        if (descriptor == null || imageFile == null || !imageFile.exists()) {
            return false;
        }
        String extension = descriptor.getExtension();
        if (!StringUtils.hasText(extension)) {
            return false;
        }
        return SUPPORTED_IMAGE_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT));
    }
}
