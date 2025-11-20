package com.vn.ecm;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * Enables Spring Data MongoDB repositories across the ECM application.
 * <p>
 * Without this configuration class Spring does not scan the {@code com.vn.ecm}
 * package for interfaces extending
 * {@link org.springframework.data.mongodb.repository.MongoRepository}.
 * As a result Mongo repositories such as {@code OcrFileDescriptorRepository}
 * were not instantiated and CRUD operations against MongoDB failed.
 * </p>
 */
@Configuration
@EnableMongoRepositories(basePackages = "com.vn.ecm")
public class MongoDbConfiguration {
}
