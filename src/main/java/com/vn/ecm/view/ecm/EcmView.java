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
import com.vn.ecm.service.ecm.folderandfile.IFolderService;
import com.vn.ecm.service.ecm.folderandfile.Impl.FileDescriptorService;
import com.vn.ecm.view.confirmreplacefolder.ConfirmReplaceFolderView;
import com.vn.ecm.view.main.MainView;
import com.vn.ecm.view.sourcestorage.SourceStorageListView;
import com.vn.ecm.view.viewmode.ViewModeFragment;
import io.jmix.core.DataManager;
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
import io.jmix.flowui.Actions;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.kit.component.upload.event.FileUploadSucceededEvent;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static io.jmix.flowui.app.inputdialog.InputParameter.stringParameter;

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
    private UploadAndUploadFileAction uploadAction;
    @ViewComponent("downloadAction")
    private UploadAndUploadFileAction downloadAction;
    @ViewComponent
    private JmixButton btnDownload;
    @Autowired
    private IFolderService folderService;
    @Autowired
    private FileDescriptorService fileDescriptorService;
    @ViewComponent
    private FileStorageUploadField fileRefField;
    @ViewComponent
    private ViewModeFragment viewModeFragment;
    @ViewComponent
    private HorizontalLayout iconTiles;
    @ViewComponent
    private VerticalLayout metadataPanel;
    @ViewComponent
    private InstanceContainer<FileDescriptor> metadataFileDc;
    @ViewComponent
    private JmixButton previewBtn;

    @Subscribe("fileDataGird")
    public void onFileDataGirdItemClick(final ItemClickEvent<FileDescriptor> event) {
        metadataFileDc.setItem(event.getItem());
    }

    @Subscribe(id = "previewBtn", subject = "clickListener")
    public void onPreviewBtnClick(final ClickEvent<JmixButton> event) {
        boolean selection = fileDataGird.getSingleSelectedItem() != null;
        boolean currentlyVisible = metadataPanel.isVisible();

        if (!selection) {
            if (currentlyVisible) {
                metadataPanel.setVisible(false);
                metadataPanel.setEnabled(false);
                previewBtn.setText("Xem chi tiết");
            } else {
                notifications.create("Vui lòng chọn tệp để xem chi tiết")
                        .withDuration(2000)
                        .withCloseable(false)
                        .withType(Notifications.Type.WARNING)
                        .show();
            }
            return;
        }
        metadataPanel.setEnabled(true);
        metadataPanel.setVisible(!currentlyVisible);
        boolean nowVisible = metadataPanel.isVisible();
        previewBtn.setText(nowVisible ? "Ẩn chi tiết" : "Xem chi tiết");
    }


    @Subscribe
    public void onInit(InitEvent event) {
        previewBtn.getElement().getStyle().set("position", "fixed");
        previewBtn.getElement().getStyle().set("right", "16px");

        metadataPanel.setVisible(false);
        viewModeFragment.bind(fileDataGird, filesDc, iconTiles);

        initFolderGridColumn();
        // Mặc định ẩn upload nếu chưa chọn thư mục
        fileRefField.setEnabled(false);
        uploadAction.setMode(UploadAndUploadFileAction.Mode.UPLOAD);
        uploadAction.setFolderSupplier(() -> foldersTree.getSingleSelectedItem());
        uploadAction.setStorageSupplier(() -> currentStorage);
        //download
        downloadAction.setMode(UploadAndUploadFileAction.Mode.DOWNLOAD);
        downloadAction.setTarget(fileDataGird);
        if (btnDownload.getAction() == null) {
            btnDownload.setAction(downloadAction);
        }
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
    public void onFoldersTreeCreateFolder(final ActionPerformedEvent event) {
        User user = (User) currentAuthentication.getUser();
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
                        Folder folder = new Folder();
                        folder.setName(closeEvent.getValue("name"));
                        folder.setParent(foldersTree.getSingleSelectedItem());
                        folder.setSourceStorage(currentStorage);
                        folderService.createFolder(folder);
                        loadAccessibleFolders(user);
                        notifications.create(messageBundle.getMessage("ecmCreateFolderAlert"))
                                .withDuration(2000)
                                .withCloseable(false)
                                .withType(Notifications.Type.SUCCESS)
                                .show();
                    }
                })
                .open();
    }
    //rename folder
    @Subscribe("foldersTree.renameFolder")
    public void onFoldersTreeRenameFolder(final ActionPerformedEvent event) {
        Folder selected = foldersTree.getSingleSelectedItem();
        User userCurr = (User) currentAuthentication.getUser();
        boolean per = permissionService.hasPermission(userCurr, PermissionType.MODIFY, selected);
        Folder parent = selected.getParent();
        if (!per) {
            notifications.create("Bạn không có quyền đổi tên thư mục này.")
                    .withType(Notifications.Type.ERROR)
                    .withDuration(2000)
                    .withCloseable(false)
                    .show();
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
                    String inputName = closeEvent.getValue("name");
                    String uniqueName = folderService.generateUniqueName(parent, currentStorage, inputName);
                    Folder existingFolder = folderService.findExistingFolder(parent, currentStorage, inputName);
                    if (existingFolder != null) {
                        DialogWindow<ConfirmReplaceFolderView> window =
                                dialogWindows.view(this, ConfirmReplaceFolderView.class)
                                        .withAfterCloseListener(w -> {
                                            if (w.closedWith(StandardOutcome.SAVE)) {
                                                notifications.show("Chức năng gộp đang phát triển");
                                            } else if (w.closedWith(StandardOutcome.CLOSE)) {
                                                folderService.renameFolder(selected, closeEvent.getValue("name"));
                                                notifications.show(messageBundle.getMessage("ecmRenameFolderAlert"));
                                            }
                                        })
                                        .build();

                        Folder newFolderForDialog = new Folder();
                        newFolderForDialog.setName(uniqueName);
                        newFolderForDialog.setCreatedDate(LocalDateTime.now());
                        window.getView().setFolderData(existingFolder, newFolderForDialog);
                        window.open();
                        return;
                    }
                    folderService.renameFolder(selected, closeEvent.getValue("name"));
                    notifications.show(messageBundle.getMessage("ecmRenameFolderAlert"));
                })
                .open();

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
        FileDescriptor selected = fileDataGird.getSingleSelectedItem();
        User userCurr = (User) currentAuthentication.getUser();
        boolean per = permissionService.hasPermission(userCurr, PermissionType.MODIFY, selected);
        if (!per) {
            notifications.create("Bạn không có quyền đổi tên tệp này.")
                    .withType(Notifications.Type.ERROR)
                    .withCloseable(false)
                    .withDuration(2000)
                    .show();
            return;
        }
        notifications.show("Chức năng đang phát triển");
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
}


