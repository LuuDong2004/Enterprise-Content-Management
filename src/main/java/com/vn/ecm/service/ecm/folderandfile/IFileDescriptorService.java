package com.vn.ecm.service.ecm.folderandfile;

import com.vn.ecm.entity.FileDescriptor;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.entity.SourceStorage;

import java.util.UUID;

public interface IFileDescriptorService {
    FileDescriptor findByName(Folder folder, SourceStorage src, String name);
    void removeFileToTrash(FileDescriptor fileDescriptor,String user);
    FileDescriptor renameFile(FileDescriptor file, String newName, String user);
}
