package com.vn.ecm.ecmmodule.service.ecm.Impl;

import com.vn.ecm.ecmmodule.entity.FileDescriptor;
import com.vn.ecm.ecmmodule.service.ecm.IFileDescriptorService;
import io.jmix.core.DataManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FileDescriptorService implements IFileDescriptorService {

    @Autowired
    private DataManager dataManager;

    @Override
    public void removeFileToTrash(FileDescriptor fileDescriptor,String user) {
        if(fileDescriptor == null ){
            return;
        }
        fileDescriptor.setInTrash(true);
        fileDescriptor.setDeleteDate(java.time.LocalDateTime.now());
        fileDescriptor.setDeletedBy(user);
        dataManager.save(fileDescriptor);
    }
}
