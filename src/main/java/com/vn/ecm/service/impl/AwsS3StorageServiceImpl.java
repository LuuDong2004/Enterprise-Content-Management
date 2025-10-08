package com.vn.ecm.service.impl;

import com.vn.ecm.service.StorageService;

public class AwsS3StorageServiceImpl implements StorageService {

    @Override
    public String getProviderName() {
        return "AWS S3 Storage";
    }
}
