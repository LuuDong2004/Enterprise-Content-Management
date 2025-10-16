package com.vn.ecm.service.ecm;

import com.vn.ecm.entity.FileDescriptor;

public interface IFileDescriptorService {

    public void removeFileToTrash(FileDescriptor fileDescriptor,String user);
}
