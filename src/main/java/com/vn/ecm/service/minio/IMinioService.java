package com.vn.ecm.service.minio;

import com.vn.ecm.entity.File;

import java.io.InputStream;

public interface IService {
    String upload(String fileRef, String fileName, InputStream inputStream, String contentType);
}
