package com.vn.ecm.service.minio;

import java.io.InputStream;

public interface IMinioService {
    String upload(String fileRef, String fileName, InputStream inputStream, String contentType);
}
