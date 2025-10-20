package com.vn.ecm.ecmmodule.service.ecm;



import com.vn.ecm.ecmmodule.entity.Folder;


public interface IFolderService {

    Folder createFolder(Folder folder);

    boolean deleteFolderFromTrash(Folder folder);

    int deleteFolderRecursivelyFromTrash(Folder folder);

    void moveToTrash(Folder folder, String username);

    Folder restoreFromTrash(Folder folder);

    Folder renameFolder(Folder folder, String username);

    String buildFolderPath(Folder folder);
}
