package com.vn.ecm.service.impl;

import com.vn.ecm.service.StorageService;

public class MinIoStorageServiceImpl implements StorageService {

    @Override
    public String getProviderName() {
        return "MinIO";
    }
}
