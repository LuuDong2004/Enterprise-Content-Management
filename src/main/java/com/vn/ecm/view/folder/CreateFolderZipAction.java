package com.vn.ecm.view.folder;

import com.vn.ecm.entity.FileDescriptor;
import com.vn.ecm.entity.Folder;

import com.vn.ecm.service.ecm.zipfile.ZipFileService;
import com.vn.ecm.service.ecm.zipfile.ZipFolderService;
import com.vn.ecm.view.file.FileZipDialogView;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.Notifications;

import io.jmix.flowui.UiComponents;

import io.jmix.flowui.download.Downloader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import java.util.Collection;

@Service
public class CreateFolderZipAction {
    private final ZipFileService zipFileService;
    @Autowired
    private ZipFolderService zipFolderService;
    @Autowired
    private Notifications notifications;
    @Autowired
    private Downloader downloader;

    @Autowired
    private UiComponents uiComponents;
    @Autowired
    private DialogWindows dialogWindows;


    public CreateFolderZipAction(ZipFileService zipFileService) {
        this.zipFileService = zipFileService;
    }

    public void openZipFolderDialog(View<?> view,Folder folder ) {
        if (folder == null) {
            return;
        }
        DialogWindow<FolderZipDialogView> window = dialogWindows.view(view,FolderZipDialogView.class).build();
        FolderZipDialogView dialogView = window.getView();
        dialogView.setFolder(folder);

        window.setResizable(true);
        window.open();

    }

    public void openZipFilesDialog(View<?> view,
                                   Folder folder,
                                   Collection<FileDescriptor> fileDescriptors) {
        if (fileDescriptors == null || fileDescriptors.isEmpty()) {
            return;
        }
        DialogWindow<FileZipDialogView> window = dialogWindows.view(view,FileZipDialogView.class).build();
        FileZipDialogView dialogView = window.getView();
        dialogView.setFolder(folder);
        dialogView.setFileDescriptors(fileDescriptors);
        window.setResizable(true);
        window.open();

    }
}
