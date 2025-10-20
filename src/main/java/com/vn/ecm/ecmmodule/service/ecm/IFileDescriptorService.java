package com.vn.ecm.ecmmodule.service.ecm;

import com.vn.ecm.ecmmodule.entity.FileDescriptor;

public interface IFileDescriptorService {

    public void removeFileToTrash(FileDescriptor fileDescriptor,String user);
}
