package com.vn.ecm.view.ecm;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.ItemClickEvent;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.*;
import com.vn.ecm.ecm.storage.DynamicStorageManager;
import com.vn.ecm.entity.*;
import com.vn.ecm.service.ecm.PermissionService;
import com.vn.ecm.view.assignpermission.AssignPermissionView;

import com.vn.ecm.view.main.MainView;
import io.jmix.core.DataManager;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.DialogWindows;

import io.jmix.flowui.Dialogs;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.UiComponents;

import io.jmix.flowui.app.inputdialog.DialogActions;
import io.jmix.flowui.app.inputdialog.DialogOutcome;
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
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;

import java.io.File;
import java.io.InputStream;
import java.time.LocalDateTime;

import java.util.List;
import java.util.UUID;

import static io.jmix.flowui.app.inputdialog.InputParameter.stringParameter;

@Route(value = "source-storages/:id", layout = MainView.class)
@ViewController("EcmView")
@ViewDescriptor("ECM-view.xml")
public class EcmView extends StandardView implements BeforeEnterObserver,  AfterNavigationObserver {
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
    @ViewComponent
    private CollectionContainer<SourceStorage> StorageDc;
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
    @Autowired
    private CurrentAuthentication currentAuthentication;
    @Autowired
    private PermissionService permissionService;
    @Autowired
    private Dialogs dialogs;
    private SourceStorage currentStorage;

    private UUID id;

    @Subscribe
    public void onInit(InitEvent event) {
        initFolderGridColumn();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String idParam = event.getRouteParameters().get("id").orElseThrow();
        this.id = UUID.fromString(idParam);

        this.currentStorage = dataManager.load(SourceStorage.class)
                .id(id)
                .optional()
                .orElse(null);
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        if (currentStorage == null) {
            notifications.create("❌ Không tìm thấy kho lưu trữ!")
                    .withType(Notifications.Type.ERROR).show();
            return;
        }
        User currentUser = (User) currentAuthentication.getUser();
        loadAccessibleFolders(currentUser);
        loadAccessibleFiles(currentUser, null);
    }

    @Subscribe("foldersTree")
    public void onFoldersTreeItemClick(ItemClickEvent<Folder> e) {
        Folder selected = e.getItem();
        User currentUser = (User) currentAuthentication.getUser();
        // Check quyền READ trước khi hiển thị files
        if (!permissionService.hasPermission(currentUser, PermissionType.READ, selected)) {
            notifications.create("Bạn không có quyền truy cập thư mục này.")
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }
        loadAccessibleFiles(currentUser, selected);
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
        SourceStorage selectedStorage = currentStorage;
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
        User userCurr = (User) currentAuthentication.getUser();
        boolean per = permissionService.hasPermission(userCurr, PermissionType.MODIFY, selectedFile);
        if (!per) {
            notifications.create("Bạn không có quyền tải xuống File này.")
                    .withType(Notifications.Type.ERROR)
                    .show();
            return;
        }
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

    //css
    private void initFolderGridColumn() {
        //remove column by key
        if (foldersTree.getColumnByKey("name") != null) {
            foldersTree.removeColumn(foldersTree.getColumnByKey("name"));
        }
        //Add hierarchy column
        TreeDataGrid.Column<Folder> nameColumn = foldersTree.addComponentHierarchyColumn(item -> renderFolderItem(item));
        nameColumn.setHeader("Name");
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

    @Subscribe("foldersTree.createFolder")
    public void onFoldersTreeCreateFolder(final ActionPerformedEvent event) {
        dialogs.createInputDialog(this)
                .withHeader("Tạo mới folder")
                .withParameters(
                        stringParameter("name")
                                .withLabel("Tên Folder ")
                                .withRequired(true)
                                .withDefaultValue("New Folder")
                )
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        try {
                            Folder folder = dataManager.create(Folder.class);
                            folder.setId(UUID.randomUUID());
                            folder.setName(closeEvent.getValue("name"));
                            folder.setParent(foldersTree.getSingleSelectedItem());
                            folder.setSourceStorage(currentStorage);
                            folder.setCreatedDate(LocalDateTime.now());
                            folder.setFullPath(buildFolderPath(folder));
                            dataManager.save(folder);
                            foldersDl.load();
                            notifications.show("Tạo mới folder thành công");
                        } catch (Exception e) {
                            notifications.show("Không thể tạo mới folder" + e);
                        }
                    }
                })
                .open();
    }
    @Subscribe("foldersTree.delete")
    public void onFoldersTreeDelete(final ActionPerformedEvent event) {
        Folder selected = foldersTree.getSingleSelectedItem();
        if(selected == null){
            notifications.show("Vui lòng chọn folder để xóa");
            return;
        }
        ConfirmDialog dlg = new ConfirmDialog();
        dlg.setHeader("Xác nhận");
        dlg.setText("Xóa folder '" + selected.getName() + " ?");
        dlg.setCancelText("Hủy");
        dlg.setCancelable(true);
        dlg.setConfirmText("Xóa");
        dlg.addConfirmListener(e2 -> {
            try {
                List<FileDescriptor> files = dataManager.load(FileDescriptor.class)
                        .query("select f from FileDescriptor f where f.folder = :folder")
                        .parameter("folder", selected)
                        .list();

                List<Folder> subFolders = dataManager.load(Folder.class)
                        .query("select f from Folder f where f.parent = :parent")
                        .parameter("parent", selected)
                        .list();

                if (!files.isEmpty() || !subFolders.isEmpty()) {
                    notifications.show("Không thể xóa folder này! Vui Lòng xóa hết tệp trước");
                    return;
                }
                dataManager.remove(selected);
                notifications.show("Đã xóa folder: " + selected.getName());
                foldersDl.load();
            } catch (Exception ex) {
                notifications.show("Lỗi " + ex.getMessage());
            }
        });
        dlg.open();
    }

    @Subscribe("foldersTree.rename")
    public void onFoldersTreeRenameFolder(final ActionPerformedEvent event) {
        Folder selected = foldersTree.getSingleSelectedItem();
        if (selected == null) {
            notifications.show("Vui lòng chọn thư mục để đổi tên");
            return;
        }
        dialogs.createInputDialog(this)
                .withHeader("Đổi tên thư mục")
                .withParameters(
                        stringParameter("name")
                                .withLabel("Tên thư mục")
                                .withDefaultValue(selected.getName())
                                .withRequired(true)
                )
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        try {
                            // xử lý logic
                        } catch (Exception e) {
                            notifications.show("Không thể đổi tên" + e);
                        }
                    }
                })
                .open();
    }

    @Subscribe("fileDataGird.deleteFile")
    public void onFileDataGirdDeleteFile(final ActionPerformedEvent event) {
        FileDescriptor selected = fileDataGird.getSingleSelectedItem();
        if(selected == null){
            notifications.create("Vui lòng chọn tệp để xóa").show();
            return;
        }
        ConfirmDialog dlg = new ConfirmDialog();
        dlg.setHeader("Xác nhận");
        dlg.setText("Xóa file '" + selected.getName() + " ?");
        dlg.setCancelText("Hủy");
        dlg.setCancelable(true);
        dlg.setConfirmText("Xóa");
        dlg.addConfirmListener(e2 -> {
            try{
                FileRef fileRef = selected.getFileRef();
                if(fileRef != null){
                    SourceStorage sourceStorage = selected.getSourceStorage();
                    if(sourceStorage != null){
                        FileStorage fileStorage = dynamicStorageManager.getOrCreateFileStorage(sourceStorage);
                        fileStorage.removeFile(fileRef);
                        dataManager.remove(selected);
                        notifications.show("Đã xóa "+selected.getName());
                        filesDl.load();
                    }
                }
            }catch(Exception e){
                notifications.show("Lỗi" + e.getMessage());
            }
        });
        dlg.open();
    }
    // Đệ quy lấy path
    private String buildFolderPath(Folder folder) {
        if (folder == null) return "";
        StringBuilder path = new StringBuilder(folder.getName());
        Folder parent = folder.getParent();
        while (parent != null) {
            path.insert(0, parent.getName() + "/");
            parent = parent.getParent();
        }
        return path.toString();
    }

    private void loadAccessibleFolders(User user) {
        List<Folder> accessibleFolders = permissionService.getAccessibleFolders(user, currentStorage);
        foldersDc.setItems(accessibleFolders);
        foldersTree.expandRecursively(accessibleFolders, 1);
    }

    private void loadAccessibleFiles(User user, Folder folder) {
        List<FileDescriptor> accessibleFiles = permissionService.getAccessibleFiles(user, currentStorage, folder);
        filesDc.setItems(accessibleFiles);
    }
}
