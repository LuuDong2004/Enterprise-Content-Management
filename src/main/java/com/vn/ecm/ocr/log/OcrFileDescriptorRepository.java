package com.vn.ecm.ocr.log;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OcrFileDescriptorRepository extends MongoRepository<OcrFileDescriptorDocument, String> {

    List<OcrFileDescriptorDocument> findByExtractedTextContainingIgnoreCase(String keyword);

    List<OcrFileDescriptorDocument> findByOcrFileDescriptorId(String ocrFileDescriptorId);

    @Query("{ '$text': { '$search': ?0 } }")
    List<OcrFileDescriptorDocument> searchFullText(String freeText);
}
