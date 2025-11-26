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

    /**
     * Di chuyển thư mục source vào trong thư mục target.
     * Thực hiện đồng bộ FULL_PATH và closure table ở tầng DB.
     */
    Folder moveFolder(Folder source, Folder target);

}
