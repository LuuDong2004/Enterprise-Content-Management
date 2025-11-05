package com.vn.ecm.service.ecm.folderandfile;



import com.vn.ecm.entity.Folder;

import java.util.UUID;


public interface IFolderService {

    Folder createFolder(Folder folder);

    boolean deleteFolderFromTrash(Folder folder);

    int deleteFolderRecursivelyFromTrash(Folder folder);

    void moveToTrash(Folder folder, String username);

    Folder restoreFromTrash(Folder folder);

    Folder renameFolder(Folder folder, String username);

    String buildFolderPath(Folder folder);

    boolean isNameExists(Folder parent, Object sourceStorage, String name);

    String generateUniqueName(Folder parent, Object sourceStorage, String desiredName);

    Folder findExistingFolder(Folder parent, Object sourceStorage, String name);
}
