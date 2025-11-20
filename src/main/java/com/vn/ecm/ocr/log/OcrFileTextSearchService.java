package com.vn.ecm.ocr.log;

import com.vn.ecm.entity.FileDescriptor;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.entity.SourceStorage;
import io.jmix.core.DataManager;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class OcrFileTextSearchService {

    private static final Logger log = LoggerFactory.getLogger(OcrFileTextSearchService.class);

    private static final Set<String> SUPPORTED_IMAGE_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "bmp", "tif", "tiff", "gif", "webp");

    private static final Set<String> SUPPORTED_PDF_EXTENSIONS = Set.of("pdf");

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

    public Optional<String> indexFile(FileDescriptor descriptor, File file) {
        if (!isEligible(descriptor, file)) {
            return Optional.empty();
        }
        String extractedText = extractText(descriptor, file);
        if (!StringUtils.hasText(extractedText)) {
            return Optional.empty();
        }
        OcrFileDescriptorDocument document = ocrFileDescriptorRepository
                .findById(descriptor.getId().toString())
                .orElseGet(OcrFileDescriptorDocument::new);

        document.setId(descriptor.getId().toString());
        document.setOcrFileDescriptorId(descriptor.getId().toString());
        document.setFileName(descriptor.getName());
        String trimmedText = extractedText.trim();
        document.setExtractedText(trimmedText);
        // Lưu thêm text không dấu để hỗ trợ tìm kiếm không dấu
        document.setExtractedTextWithoutDiacritics(removeVietnameseDiacritics(trimmedText));

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
        return searchFilesByText(freeText, null, null, SearchMode.FUZZY, false);
    }

    public List<FileDescriptor> searchFilesByText(String freeText, SourceStorage scopeStorage) {
        return searchFilesByText(freeText, scopeStorage, null, SearchMode.FUZZY, false);
    }

    public List<FileDescriptor> searchFilesByText(String freeText, SourceStorage scopeStorage, SearchMode searchMode) {
        return searchFilesByText(freeText, scopeStorage, null, searchMode, false);
    }

    public List<FileDescriptor> searchFilesByText(String freeText, SourceStorage scopeStorage, SearchMode searchMode,
            boolean ignoreDiacritics) {
        return searchFilesByText(freeText, scopeStorage, null, searchMode, ignoreDiacritics);
    }

    public List<FileDescriptor> searchFilesByText(String freeText, SourceStorage scopeStorage, Folder folder,
            SearchMode searchMode,
            boolean ignoreDiacritics) {
        if (!StringUtils.hasText(freeText)) {
            return List.of();
        }
        List<OcrFileDescriptorDocument> documents;
        if (searchMode == SearchMode.EXACT) {
            if (ignoreDiacritics) {
                // Tìm kiếm đích danh không dấu: normalize text và tìm trong normalizedText
                String normalizedSearchText = removeVietnameseDiacritics(freeText);
                String escapedText = escapeRegexSpecialChars(normalizedSearchText);
                String regexPattern = ".*" + escapedText + ".*";
                documents = ocrFileDescriptorRepository.searchExactTextWithoutDiacritics(regexPattern);
            } else {
                // Tìm kiếm đích danh có dấu: escape các ký tự đặc biệt trong regex
                String escapedText = escapeRegexSpecialChars(freeText);
                String regexPattern = ".*" + escapedText + ".*";
                documents = ocrFileDescriptorRepository.searchExactText(regexPattern);
            }
        } else {
            // Tìm kiếm tương đối: sử dụng MongoDB text search
            String normalizedSearchText = normalizeWhitespaceForFuzzySearch(freeText);
            List<OcrFileDescriptorDocument> originalDocs = ocrFileDescriptorRepository.searchFullText(freeText);
            List<OcrFileDescriptorDocument> normalizedDocs = originalDocs;
            if (!freeText.equals(normalizedSearchText) && !normalizedSearchText.contains(" ")) {
                String escapedText = escapeRegexSpecialChars(normalizedSearchText);
                String regexPattern = ".*" + escapedText.replaceAll("(.)", "$1\\s*") + ".*";
                // Sử dụng case-insensitive regex để match cả hoa và thường
                List<OcrFileDescriptorDocument> regexDocs = ocrFileDescriptorRepository
                        .searchExactTextCaseInsensitive(regexPattern);
                // Gộp kết quả
                normalizedDocs = regexDocs;
                for (OcrFileDescriptorDocument doc : originalDocs) {
                    if (!normalizedDocs.stream().anyMatch(d -> d.getId().equals(doc.getId()))) {
                        normalizedDocs.add(doc);
                    }
                }
            }
            documents = normalizedDocs;
        }
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
                        "and (:storage is null or f.sourceStorage = :storage) " +
                        "and (:folder is null or f.folder = :folder)")
                .parameter("ids", fileIds)
                .parameter("storage", scopeStorage)
                .parameter("folder", folder)
                .list();
    }

    private String escapeRegexSpecialChars(String text) {
        return text.replace("\\", "\\\\")
                .replace(".", "\\.")
                .replace("^", "\\^")
                .replace("$", "\\$")
                .replace("*", "\\*")
                .replace("+", "\\+")
                .replace("?", "\\?")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("|", "\\|");
    }

    // không dấu
    private String removeVietnameseDiacritics(String text) {
        if (text == null) {
            return null;
        }
        String nfdNormalizedString = Normalizer.normalize(text, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(nfdNormalizedString).replaceAll("");
    }

    private String normalizeWhitespaceForFuzzySearch(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String normalized = text.trim();
        // xử lí có nhiều khoảng trắng
        Pattern singleCharWithSpaces = Pattern.compile("(\\S)\\s+(\\S)");
        String temp = normalized;
        String previous = "";
        // Lặp cho đến khi không còn thay đổi
        while (!temp.equals(previous)) {
            previous = temp;
            // Nếu có nhiều khoảng trắng giữa các ký tự đơn lẻ, loại bỏ chúng
            // Nhưng chỉ khi cả hai ký tự đều là ký tự đơn (không phải từ dài)
            temp = singleCharWithSpaces.matcher(temp).replaceAll("$1$2");
        }
        // Normalize nhiều khoảng trắng thành một khoảng trắng
        normalized = temp.replaceAll("\\s+", " ");

        return normalized.trim();
    }

    private String extractText(FileDescriptor descriptor, File file) {
        String extension = descriptor.getExtension();
        if (extension == null) {
            return null;
        }
        String lowerExtension = extension.toLowerCase(Locale.ROOT);

        try {
            if (SUPPORTED_PDF_EXTENSIONS.contains(lowerExtension)) {
                // Extract text từ PDF - tự động phát hiện PDF có text hay scan
                return ocrFileDescriptorService.extractTextFromPdf(file);
            } else if (SUPPORTED_IMAGE_EXTENSIONS.contains(lowerExtension)) {
                // Extract text từ ảnh sử dụng OCR
                return ocrFileDescriptorService.extractText(file);
            } else {
                log.debug("Unsupported file type for text extraction: {}", extension);
                return null;
            }
        } catch (TesseractException e) {
            log.warn("OCR extraction failed for file {}", file.getName(), e);
            return null;
        } catch (IOException e) {
            log.warn("PDF text extraction failed for file {}", file.getName(), e);
            return null;
        }
    }

    private boolean isEligible(FileDescriptor descriptor, File file) {
        if (descriptor == null || file == null || !file.exists()) {
            return false;
        }
        String extension = descriptor.getExtension();
        if (!StringUtils.hasText(extension)) {
            return false;
        }
        String lowerExtension = extension.toLowerCase(Locale.ROOT);
        return SUPPORTED_IMAGE_EXTENSIONS.contains(lowerExtension)
                || SUPPORTED_PDF_EXTENSIONS.contains(lowerExtension);
    }
}
