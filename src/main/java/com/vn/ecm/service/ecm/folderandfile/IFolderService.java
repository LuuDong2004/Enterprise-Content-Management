package com.vn.ecm.service.ecm.folderandfile;

import com.vn.ecm.entity.Folder;

public interface IFolderService {

    Folder createFolder(Folder folder);

    boolean deleteFolderFromTrash(Folder folder);

    int deleteFolderRecursivelyFromTrash(Folder folder);

    void moveToTrash(Folder folder, String username);

    Folder restoreFromTrash(Folder folder);

    Folder renameFolder(Folder folder, String username);

    String buildFolderPath(Folder folder);

    Folder findExistingFolder(Folder parent, Object sourceStorage, String name);

    Folder moveFolder(Folder source, Folder target);

    Folder moveFolderPathOnly(Folder source, Folder target);

    Folder getOrCreateRootFolder(com.vn.ecm.entity.User user);

}
