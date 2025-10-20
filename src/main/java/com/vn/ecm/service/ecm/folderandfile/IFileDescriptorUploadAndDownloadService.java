package com.vn.ecm.service.ecm.folderandfile;

import com.vn.ecm.entity.FileDescriptor;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.entity.SourceStorage;

import java.util.UUID;

public interface IFileDescriptorUploadAndDownloadService {
    FileDescriptor uploadFile(UUID fileId,
                              String fileName,
                              Long contentLength,
                              Folder folder,
                              SourceStorage sourceStorage,
                              String username);

    byte[] downloadFile(FileDescriptor fileDescriptor);
}
