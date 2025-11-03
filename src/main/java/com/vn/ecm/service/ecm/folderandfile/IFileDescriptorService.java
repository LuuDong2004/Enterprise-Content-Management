package com.vn.ecm.service.ecm.folderandfile;

import com.vn.ecm.entity.FileDescriptor;

public interface IFileDescriptorService {

    void removeFileToTrash(FileDescriptor fileDescriptor,String user);
    FileDescriptor renameFile(FileDescriptor file, String newName, String user);
}
