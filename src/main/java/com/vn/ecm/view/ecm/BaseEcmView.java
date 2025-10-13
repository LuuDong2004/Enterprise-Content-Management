package com.vn.ecm.view.ecm;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.ItemClickEvent;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vn.ecm.ecm.storage.DynamicStorageManager;
import com.vn.ecm.entity.FileDescriptor;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.entity.SourceStorage;
import com.vn.ecm.view.assignpermission.AssignPermissionView;
import io.jmix.core.DataManager;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import io.jmix.flowui.DialogWindows;

import io.jmix.flowui.Notifications;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.component.upload.FileStorageUploadField;
import io.jmix.flowui.component.upload.receiver.FileTemporaryStorageBuffer;
import io.jmix.flowui.download.Downloader;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.kit.component.upload.event.FileUploadSucceededEvent;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.upload.TemporaryStorage;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;


import java.io.File;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.UUID;

@ViewController("ecm_BaseEcmView")
@ViewDescriptor("ECM-view.xml")
public abstract class BaseEcmView extends StandardView {


    @ViewComponent
    private CollectionContainer<Folder> foldersDc;
    @ViewComponent
    private CollectionContainer<FileDescriptor> filesDc;
    @ViewComponent
    private TreeDataGrid<Folder> foldersTree;
    @ViewComponent
    private CollectionLoader<Folder> foldersDl;
    @ViewComponent
    private DataGrid<FileDescriptor> fileDataGird;
    @ViewComponent
    private CollectionLoader<FileDescriptor> filesDl;
    @ViewComponent
    private FileStorageUploadField fileRefField;

    // -----------------------------
    // Dependencies
    // -----------------------------
    @Autowired
    private TemporaryStorage temporaryStorage;
    @Autowired
    private Notifications notifications;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private Downloader downloader;
    @Autowired
    private DynamicStorageManager dynamicStorageManager;
    @Autowired
    private UiComponents uiComponents;
    @Autowired
    private DialogWindows dialogWindows;
    protected abstract SourceStorage getCurrentStorage();


    @Subscribe
    public void onInit(InitEvent event){
        initFolderGridColumn();
    }

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        // folders trước
        foldersDl.setParameter("storage", getCurrentStorage());
        foldersDl.load();

        foldersTree.expandRecursively(foldersDc.getItems(), 1);

        filesDl.setParameter("storage", getCurrentStorage());
        filesDl.setParameter("folder", null);
    }
    @Subscribe("foldersTree")
    public void onFoldersTreeItemClick(ItemClickEvent<Folder> e) {
        Folder selected = e.getItem();
        filesDl.setParameter("storage", getCurrentStorage());
        filesDl.setParameter("folder", selected);

        filesDl.load();
    }
    @Subscribe("fileRefField")
    public void onFileRefFieldFileUploadSucceeded(final FileUploadSucceededEvent<FileStorageUploadField> event) {
        Folder selected = foldersTree.getSingleSelectedItem();
        if (selected == null) {
            notifications.create("Hãy chọn thư mục trước khi upload.")
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }
        SourceStorage selectedStorage = getCurrentStorage();
        if (selectedStorage == null) {
            notifications.create("Không xác định được kho lưu trữ.")
                    .withType(Notifications.Type.ERROR)
                    .show();
            return;
        }
        if (event.getReceiver() instanceof FileTemporaryStorageBuffer buffer) {
            UUID fileId = buffer.getFileData().getFileInfo().getId();
            File fileIo = temporaryStorage.getFile(fileId);
            if (fileIo != null) {
                try {
                    FileStorage dynamicFs = dynamicStorageManager.getOrCreateFileStorage(selectedStorage);
                    String fileName = event.getFileName();
                    FileRef fileRef = temporaryStorage.putFileIntoStorage(fileId, fileName, dynamicFs);
                    fileRefField.setValue(fileRef);

                    // Lưu metadata file vào DB
                    Long length = event.getContentLength() > 0 ? event.getContentLength() : null;
                    FileDescriptor fileDescriptor = dataManager.create(FileDescriptor.class);
                    fileDescriptor.setId(UUID.randomUUID());
                    fileDescriptor.setName(event.getFileName());
                    fileDescriptor.setSize(length);
                    if (fileName.contains(".")) {
                        fileDescriptor.setExtension(fileName.substring(fileName.lastIndexOf('.') + 1));
                    }
                    fileDescriptor.setLastModified(LocalDateTime.now());
                    fileDescriptor.setFolder(selected);
                    fileDescriptor.setFileRef(fileRef);
                    fileDescriptor.setSourceStorage(selectedStorage);
                    dataManager.save(fileDescriptor);

                    notifications.create("Tải lên thành công: " + fileName)
                            .withType(Notifications.Type.SUCCESS)
                            .show();

                    filesDl.load();
                } catch (Exception e) {
                    notifications.create("Lỗi khi lưu file: " + e.getMessage())
                            .withType(Notifications.Type.ERROR)
                            .show();
                }
            }
        }
    }
    @Subscribe(id = "btnDownload", subject = "clickListener")
    public void onBtnDownloadClick(final ClickEvent<JmixButton> event) {
        FileDescriptor selectedFile = fileDataGird.getSingleSelectedItem();
        if (selectedFile == null) {
            notifications.create("Chưa chọn file để tải xuống.")
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }
        FileRef fileRef = selectedFile.getFileRef();
        if (fileRef == null) {
            notifications.create("File này không có đường dẫn tải xuống hợp lệ.")
                    .withType(Notifications.Type.ERROR)
                    .show();
            return;
        }

        try {
            String storageName = fileRef.getStorageName();
            dynamicStorageManager.ensureStorageRegistered(storageName);
            SourceStorage sourceStorage = selectedFile.getSourceStorage();
            if (sourceStorage != null) {
                FileStorage fileStorage = dynamicStorageManager.getOrCreateFileStorage(sourceStorage);
                InputStream inputStream = fileStorage.openStream(fileRef);
                byte[] fileBytes = inputStream.readAllBytes();
                inputStream.close();
                String downloadFileName = selectedFile.getName();
                downloader.download(fileBytes, downloadFileName);
            } else {
                throw new RuntimeException("SourceStorage is null");
            }

        } catch (Exception e) {
            notifications.create("Lỗi khi tải xuống: " + e.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
        }
    }

    @Subscribe("foldersTree.assignPermission")
    public void onFoldersTreeAssignPermission(final ActionPerformedEvent event) {
        Folder folder = foldersTree.getSingleSelectedItem();
        if (folder == null) {
            notifications.show("Vui lòng chọn một folder để gán quyền.");
            return;
        }
        try {
            String path = buildFolderPath(folder);
            DialogWindow<AssignPermissionView> window = dialogWindows.view(this, AssignPermissionView.class).build();
            window.getView().setTargetFolder(folder);
            window.getView().setPath(path);
            window.open();
        } catch (Exception e) {
            e.printStackTrace();
            notifications.show("Lỗi khi mở popup: " + e.getMessage());
        }
    }

    // đệ quy lấy path
    private String buildFolderPath(Folder folder) {
        StringBuilder path = new StringBuilder(folder.getName());
        Folder parent = folder.getParent();
        while (parent != null) {
            path.insert(0, parent.getName() + "/");
            parent = parent.getParent();
        }
        return path.toString();
    }

    @Subscribe("fileDataGird.onObjectAssignPermission")
    public void onOnObjectAssignPermission(final ActionPerformedEvent event) {
        FileDescriptor file = fileDataGird.getSingleSelectedItem();
        if (file == null) {
            notifications.show("Vui lòng chọn một file để gán quyền.");
            return;
        }
        try {
            Folder folderParent = file.getFolder();
            String path = "";
            if (folderParent != null) {
                path = buildFolderPath(folderParent) + "/" + file.getName();
            } else {
                path = file.getName();
            }
            DialogWindow<AssignPermissionView> window = dialogWindows.view(this, AssignPermissionView.class).build();
            window.getView().setTargetFile(file);
            window.getView().setPath(path);
            window.open();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }




    //css
    private void initFolderGridColumn() {
        //remove column by key
        if (foldersTree.getColumnByKey("name") != null) {
            foldersTree.removeColumn(foldersTree.getColumnByKey("name"));
        }

        //Add hierarchy column
        TreeDataGrid.Column<Folder> nameColumn = foldersTree.addComponentHierarchyColumn(item -> renderFolderItem(item));
        nameColumn.setHeader("Tên Đơn vị");
        nameColumn.setWidth("500px");
        //set position for hierarchy column
        foldersTree.setColumnPosition(nameColumn, 0);
    }
    private Component renderFolderItem(Folder item) {
        HorizontalLayout hboxMain = uiComponents.create(HorizontalLayout.class);
        hboxMain.setAlignItems(FlexComponent.Alignment.CENTER);
        hboxMain.setWidthFull();
        hboxMain.setPadding(false);
        hboxMain.setSpacing(true);

        Icon icon = uiComponents.create(Icon.class);
        icon.setIcon(VaadinIcon.FOLDER);

        // Không cho icon bị co lại
        icon.getElement().getStyle().set("flex-shrink", "0");
        icon.addClassName("folder-item");

        Span span = uiComponents.create(Span.class);
        span.setText(item.getName());
        span.addClassName("folder-text");

        hboxMain.add(icon, span);

        hboxMain.addClickListener(event -> {
            foldersTree.select(item);
            foldersDc.setItem(item);
        });
        return hboxMain;
    }


}
