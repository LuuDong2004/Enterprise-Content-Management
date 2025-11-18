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
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.selection.SelectionEvent;
import com.vaadin.flow.router.*;
import com.vn.ecm.entity.*;
import com.vn.ecm.service.ecm.PermissionService;
import com.vn.ecm.service.ecm.folderandfile.IFileDescriptorService;
import com.vn.ecm.service.ecm.folderandfile.IFolderService;
import com.vn.ecm.view.component.filepreview.*;
import com.vn.ecm.view.file.EditFileNameDialogView;
import com.vn.ecm.view.folder.CreateFolderDialogView;
import com.vn.ecm.view.folder.EditNameFolderDialogView;
import com.vn.ecm.view.main.MainView;
import com.vn.ecm.view.sourcestorage.SourceStorageListView;
import com.vn.ecm.view.viewmode.ViewModeFragment;
import io.jmix.core.DataManager;
import io.jmix.core.FileRef;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.component.upload.FileStorageUploadField;
import io.jmix.flowui.Actions;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.kit.component.upload.event.FileUploadSucceededEvent;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;

import java.util.*;

@Route(value = "source-storages/:id", layout = MainView.class)
@ViewController("EcmView")
@ViewDescriptor("ECM-view.xml")
public class EcmView extends StandardView implements BeforeEnterObserver, AfterNavigationObserver {
    @ViewComponent
    private CollectionContainer<Folder> foldersDc;
    @ViewComponent
    private TreeDataGrid<Folder> foldersTree;
    @ViewComponent
    private DataGrid<FileDescriptor> fileDataGird;
    @ViewComponent
    private CollectionContainer<FileDescriptor> filesDc;
    @Autowired
    private Notifications notifications;
    @Autowired
    private DataManager dataManager;
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
    @ViewComponent
    private MessageBundle messageBundle;
    @Autowired
    private Actions actions;
    @ViewComponent("uploadAction")
    private UploadAndDownloadFileAction uploadAction;
    @ViewComponent("downloadAction")
    private UploadAndDownloadFileAction downloadAction;
    @ViewComponent
    private JmixButton btnDownload;
    @Autowired
    private IFolderService folderService;
    @ViewComponent
    private FileStorageUploadField fileRefField;
    @ViewComponent
    private ViewModeFragment viewModeFragment;
    @ViewComponent
    private HorizontalLayout iconTiles;
    @Autowired
    private IFileDescriptorService fileDescriptorService;
    @ViewComponent
    private VerticalLayout metadataPanel;
    @ViewComponent
    private InstanceContainer<FileDescriptor> metadataFileDc;
    @ViewComponent
    private JmixButton previewBtn;
    @ViewComponent
    private Span emptyStateText;
    @ViewComponent
    private VerticalLayout metadataContent;
    @ViewComponent
    private VerticalLayout metadataEmptyState;


    @Subscribe
    public void onInit(InitEvent event) {
        previewBtn.getElement().getStyle().set("position", "fixed");
        previewBtn.getElement().getStyle().set("right", "16px");
        // Mặc định ẩn metadata panel
        metadataPanel.setVisible(false);
        metadataContent.setVisible(false);
        metadataEmptyState.setVisible(true);
        viewModeFragment.bind(fileDataGird, filesDc, iconTiles);

        initFolderGridColumn();
        fileRefField.setEnabled(false);
        uploadAction.setMode(UploadAndDownloadFileAction.Mode.UPLOAD);
        uploadAction.setFolderSupplier(() -> foldersTree.getSingleSelectedItem());
        uploadAction.setStorageSupplier(() -> currentStorage);
        //download
        downloadAction.setMode(UploadAndDownloadFileAction.Mode.DOWNLOAD);
        downloadAction.setTarget(fileDataGird);
        if (btnDownload.getAction() == null) {
            btnDownload.setAction(downloadAction);
        }

        if (foldersTree != null) {
            foldersTree.addSelectionListener(e -> updateEmptyStateText());
        }
        if (filesDc != null) {
            filesDc.addCollectionChangeListener(e -> updateEmptyStateText());
        }
    }

    @Subscribe(id = "previewBtn", subject = "clickListener")
    public void onPreviewBtnClick(final ClickEvent<JmixButton> event) {
        boolean currentlyVisible = metadataPanel.isVisible();
        metadataPanel.setVisible(!currentlyVisible);
        boolean nowVisible = metadataPanel.isVisible();
        previewBtn.setText(nowVisible ? "Ẩn chi tiết" : "Xem chi tiết");
    }


    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String idParam = event.getRouteParameters().get("id").orElseThrow();
        this.id = UUID.fromString(idParam);
        this.currentStorage = dataManager.load(SourceStorage.class)
                .id(id)
                .optional()
                .orElse(null);
        if (currentStorage == null || Boolean.FALSE.equals(currentStorage.getActive())) {
            notifications.show(messageBundle.getMessage("ecmSourceStorageInactiveAlert"));
            event.rerouteTo(SourceStorageListView.class);
        }
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        if (currentStorage == null) {
            notifications.create("Không tìm thấy kho lưu trữ!")
                    .withType(Notifications.Type.ERROR).show();
            return;
        }
        User currentUser = (User) currentAuthentication.getUser();
        loadAccessibleFolders(currentUser);
        loadAccessibleFiles(currentUser, null);
    }

    @Subscribe(id = "foldersTree", subject = "selectionListener")
    public void onFoldersTreeSelectionChange(SelectionEvent<TreeDataGrid<Folder>, Folder> event) {
        Folder selected = event.getFirstSelectedItem().orElse(null);
        boolean selection = selected != null;
        fileRefField.setEnabled(selection);
        User user = (User) currentAuthentication.getUser();
        if (selection) {
            boolean hasUploadPermission = permissionService.hasPermission(user, PermissionType.CREATE, selected);
            fileRefField.setEnabled(hasUploadPermission);
            loadAccessibleFiles(user, selected);
        } else {
            filesDc.getMutableItems().clear();
        }
    }

    @Subscribe(id = "fileDataGird", subject = "selectionListener")
    public void onFileDataGirdSelectionChange(SelectionEvent<DataGrid<FileDescriptor>, FileDescriptor> event) {
        FileDescriptor selected = event.getFirstSelectedItem().orElse(null);
        boolean selection = selected != null;
        User user = (User) currentAuthentication.getUser();
        if (selection) {
            metadataFileDc.setItem(selected);
            metadataContent.setVisible(true);
            metadataEmptyState.setVisible(false);
        } else {
            metadataFileDc.setItem(null);
            metadataContent.setVisible(false);
            metadataEmptyState.setVisible(true);
        }
    }

    //upload file
    @Subscribe("fileRefField")
    public void onFileRefFieldFileUploadSucceeded(final FileUploadSucceededEvent<FileStorageUploadField> event) {
        User user = (User) currentAuthentication.getUser();
        boolean per = permissionService.hasPermission(user, PermissionType.CREATE, foldersTree.getSingleSelectedItem());
        if (!per) {
            notifications.create("Bạn không có quyền tải file này lên hệ thống.")
                    .withType(Notifications.Type.ERROR)
                    .withDuration(2000)
                    .withCloseable(false)
                    .show();
            return;
        }
        try {
            uploadAction.setUploadEvent(event);
            uploadAction.execute();
            loadAccessibleFiles(user, foldersTree.getSingleSelectedItem());
            notifications.create(messageBundle.getMessage("ecmUploadFileAlert"))
                    .withType(Notifications.Type.SUCCESS)
                    .withDuration(2000)
                    .withCloseable(false)
                    .show();
        } catch (Exception e) {
            notifications.create("Lỗi tải lên : " + event.getFileName())
                    .withType(Notifications.Type.ERROR)
                    .withDuration(4000)
                    .withCloseable(false)
                    .show();
        }
    }

    //new folder
    @Subscribe("foldersTree.createFolder")
    public void onFoldersTreeCreateFolder(ActionPerformedEvent event) {
        User user = (User) currentAuthentication.getUser();
        Folder parent = foldersTree.getSingleSelectedItem();
        if (parent != null) {
            boolean per = permissionService.hasPermission(user, PermissionType.CREATE, parent);
            if (!per) {
                notifications.create("Bạn không có quyền tạo mới thư mục")
                        .withType(Notifications.Type.ERROR)
                        .withDuration(2000)
                        .withCloseable(false)
                        .show();
                return;
            }
        }

        var dw = dialogWindows.view(this, CreateFolderDialogView.class).build();
        dw.getView().setContext(parent, currentStorage);
        dw.addAfterCloseListener(e -> {
            loadAccessibleFolders(user);
        });
        dw.open();
    }

    //rename folder
    @Subscribe("foldersTree.renameFolder")
    public void onFoldersTreeRenameFolder(final ActionPerformedEvent event) {
        Folder selected = foldersTree.getSingleSelectedItem();
        if (selected == null) {
            notifications.show("Vui lòng chọn thư mục để đổi tên");
            return;
        }
        User userCurr = (User) currentAuthentication.getUser();
        boolean per = permissionService.hasPermission(userCurr, PermissionType.MODIFY, selected);
        if (!per) {
            notifications.create("Bạn không có quyền đổi tên thư mục này.")
                    .withType(Notifications.Type.ERROR)
                    .withDuration(2000)
                    .withCloseable(false)
                    .show();
            return;
        }
        var dw = dialogWindows.view(this, EditNameFolderDialogView.class).build();
        dw.getView().setContext(selected, currentStorage);
        dw.addAfterCloseListener(e -> {
            loadAccessibleFolders(userCurr);
        });
        dw.open();
    }

    //xóa vào thùng rác
    @Subscribe("foldersTree.delete")
    public void onFoldersTreeDelete(final ActionPerformedEvent event) {
        Folder selected = foldersTree.getSingleSelectedItem();
        User userCurr = (User) currentAuthentication.getUser();
        boolean per = permissionService.hasPermission(userCurr, PermissionType.FULL, selected);
        if (!per) {
            notifications.create("Bạn không có quyền xóa thư mục này.")
                    .withType(Notifications.Type.ERROR)
                    .withDuration(2000)
                    .withCloseable(false)
                    .show();
            return;
        }
        ConfirmDialog dlg = new ConfirmDialog();
        dlg.setHeader("Xác nhận");
        dlg.setText("Xóa thư mục '" + selected.getName() + "' ?" + " (Đưa vào thùng rác)");
        dlg.setCancelable(true);
        dlg.setConfirmText("Xóa");
        dlg.addConfirmListener(e2 -> {
            try {
                String useName = userCurr.getUsername();
                folderService.moveToTrash(selected, useName);
                loadAccessibleFolders(userCurr);
                notifications.show(messageBundle.getMessage("ecmDeleteFolderAlert"));
            } catch (Exception ex) {
                notifications.show("Lỗi " + ex.getMessage());
            }
        });
        dlg.open();
    }

    //remove file
    @Subscribe("fileDataGird.deleteFile")
    public void onFileDataGirdDeleteFile(final ActionPerformedEvent event) {
        FileDescriptor selected = fileDataGird.getSingleSelectedItem();
        User userCurr = (User) currentAuthentication.getUser();
        boolean per = permissionService.hasPermission(userCurr, PermissionType.FULL, selected);
        if (!per) {
            notifications.create("Bạn không có quyền xóa File này.")
                    .withType(Notifications.Type.ERROR)
                    .withDuration(2000)
                    .withCloseable(false)
                    .show();
            return;
        }
        ConfirmDialog dlg = new ConfirmDialog();
        dlg.setHeader("Xác nhận");
        dlg.setText("Xóa file '" + selected.getName() + " ?" + "(Đưa vào thùng rác)");
        dlg.setCancelable(true);
        dlg.setConfirmText("Xóa");
        dlg.addConfirmListener(e2 -> {
            try {
                fileDescriptorService.removeFileToTrash(selected, currentAuthentication.getUser().getUsername());
                loadAccessibleFiles(userCurr, foldersTree.getSingleSelectedItem());
                notifications.show(messageBundle.getMessage("ecmDeleteFileAlert"));
            } catch (Exception e) {
                notifications.show("Lỗi" + e.getMessage());
            }
        });
        dlg.open();
    }

    //action download file
    @Subscribe("fileDataGird.downloadFile")
    public void onFileDataGirdDownloadFile(final ActionPerformedEvent event) {
        btnDownload.click();
    }

    //action rename file
    @Subscribe("fileDataGird.renameFile")
    public void onFileDataGirdRenameFile(final ActionPerformedEvent event) {
        Folder selectedFolder = foldersTree.getSingleSelectedItem();
        FileDescriptor selectedFile = fileDataGird.getSingleSelectedItem();
        User userCurr = (User) currentAuthentication.getUser();
        boolean per = permissionService.hasPermission(userCurr, PermissionType.MODIFY, selectedFile);
        if (!per) {
            notifications.create("Bạn không có quyền đổi tên tệp này.")
                    .withType(Notifications.Type.ERROR)
                    .withCloseable(false)
                    .withDuration(2000)
                    .show();
            return;
        }
        var dw = dialogWindows.view(this, EditFileNameDialogView.class).build();
        dw.getView().setContext(selectedFolder, selectedFile, currentStorage);
        dw.addAfterCloseListener(e -> {
            loadAccessibleFiles(userCurr, selectedFolder);
        });
        dw.open();


    }

    private void loadAccessibleFolders(User user) {
        List<Folder> accessibleFolders = permissionService.getAccessibleFolders(user, currentStorage);
        foldersDc.setItems(accessibleFolders);
    }

    private void loadAccessibleFiles(User user, Folder folder) {
        List<FileDescriptor> accessibleFiles = permissionService.getAccessibleFiles(user, currentStorage, folder);
        filesDc.setItems(accessibleFiles);
    }

    //css
    private void initFolderGridColumn() {
        //remove column by key
        if (foldersTree.getColumnByKey("name") != null) {
            foldersTree.removeColumn(foldersTree.getColumnByKey("name"));
        }
        //Add hierarchy column
        TreeDataGrid.Column<Folder> nameColumn = foldersTree.addComponentHierarchyColumn(item -> renderFolderItem(item));
        nameColumn.setHeader("Thư mục");
        nameColumn.setFlexGrow(1);
        nameColumn.setResizable(true);

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

    private void updateEmptyStateText() {
        if (emptyStateText == null) return;

        Folder selectedFolder = foldersTree.getSingleSelectedItem();
        boolean hasFolderSelected = selectedFolder != null;
        boolean hasFiles = filesDc != null && !filesDc.getItems().isEmpty();

        if (!hasFolderSelected) {
            emptyStateText.setText("Chưa chọn thư mục");
        } else if (!hasFiles) {
            emptyStateText.setText("Thư mục trống");
        } else {
            emptyStateText.setText("Không có dữ liệu");
        }
    }

    @Subscribe("fileDataGird.preViewFile")
    public void onFileDataGirdPreViewFile(final ActionPerformedEvent event) {
        FileDescriptor file = fileDataGird.getSingleSelectedItem();
        if (file == null) {
            return;
        }
        FileRef fileRef = file.getFileRef();
        String extension = file.getExtension();
        if (extension.startsWith("pdf")) {
            previewPdfFile(fileRef);
        }
        if (extension.startsWith("txt") || extension.startsWith("docx")) {
            previewTextFile(fileRef);
        }
        if (extension.startsWith("jpg")
                || extension.startsWith("png")
                || extension.startsWith("jpeg")
                || extension.startsWith("webp")
                || extension.startsWith("svg")
                || extension.startsWith("gif")) {
            previewImageFile(fileRef);
        }
        if (extension.startsWith("mp4")
                || extension.startsWith("mov")
                || extension.startsWith("webm")) {
            preViewVideoFile(fileRef);
        }
        if(extension.startsWith("html") || extension.startsWith("htm")){
            preViewHtmlFile(fileRef);
        }

    }

    private void previewPdfFile(FileRef fileRelf) {
        DialogWindow<PdfPreview> window = dialogWindows.view(this, PdfPreview.class).build();
        window.getView().setInputFile(fileRelf);
        window.setResizable(true);
        window.open();
    }

    private void previewTextFile(FileRef fileRelf) {
        DialogWindow<TextPreview> window = dialogWindows.view(this, TextPreview.class).build();
        window.getView().setInputFile(fileRelf);
        window.setResizable(true);
        window.open();
    }

    private void previewImageFile(FileRef fileRelf) {
        DialogWindow<ImagePreview> window = dialogWindows.view(this, ImagePreview.class).build();
        window.getView().setInputFile(fileRelf);
        window.setResizable(true);
        window.open();
    }

    private void preViewVideoFile(FileRef fileRelf) {
        DialogWindow<VideoPreview> window = dialogWindows.view(this, VideoPreview.class).build();
        window.getView().setInputFile(fileRelf);
        window.setResizable(true);
        window.open();
    }
    private void preViewHtmlFile(FileRef fileRelf){
        DialogWindow<HtmlPreview> window = dialogWindows.view(this, HtmlPreview.class).build();
        window.getView().setInputFile(fileRelf);
        window.setResizable(true);
        window.open();
    }
}


