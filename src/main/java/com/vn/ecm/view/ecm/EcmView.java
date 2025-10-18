package com.vn.ecm.view.ecm;


import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.ItemClickEvent;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.data.selection.SelectionEvent;
import com.vaadin.flow.router.*;
import com.vn.ecm.entity.*;
import com.vn.ecm.service.ecm.PermissionService;
import com.vn.ecm.service.ecm.IFileDescriptorService;
import com.vn.ecm.service.ecm.IFolderService;
import com.vn.ecm.view.main.MainView;
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
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;

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
    private CollectionLoader<Folder> foldersDl;
    @ViewComponent
    private DataGrid<FileDescriptor> fileDataGird;
    @ViewComponent
    private CollectionLoader<FileDescriptor> filesDl;
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
    private IFileDescriptorService fileDescriptorService;
    @ViewComponent
    private FileStorageUploadField fileRefField;

    @ViewComponent
    private ViewModeFragment viewModeFragment;
    @ViewComponent
    private com.vaadin.flow.component.orderedlayout.HorizontalLayout iconTiles;


    @Subscribe
    public void onInit(InitEvent event) {
        // mode view
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
        // FOLDERS
        foldersDl.setParameter("storage", currentStorage);
//        foldersDl.load();
        foldersTree.expandRecursively(foldersDc.getItems(), 1);

        filesDl.setParameter("storage", currentStorage);
        filesDl.setParameter("folder", null);

    }

    @Subscribe(id = "foldersTree", subject = "selectionListener")
    public void onFoldersTreeSelectionChange(SelectionEvent<TreeDataGrid<Folder>, Folder> event) {
        Folder selected = event.getFirstSelectedItem().orElse(null);
        boolean selection = selected != null;
        fileRefField.setEnabled(selection);
        if (selection) {
            filesDl.setParameter("storage", currentStorage);
            filesDl.setParameter("folder", selected);
            filesDl.load();
        } else {
            filesDl.setParameter("folder", null);
            filesDc.getMutableItems().clear();
        }
    }


    @Subscribe("foldersTree")
    public void onFoldersTreeItemClick(ItemClickEvent<Folder> e) {
        Folder selected = e.getItem();
        User currentUser = (User) currentAuthentication.getUser();
        loadAccessibleFiles(currentUser, selected);
    }

    //upload file
    @Subscribe("fileRefField")
    public void onFileRefFieldFileUploadSucceeded(final FileUploadSucceededEvent<FileStorageUploadField> event) {
        uploadAction.setUploadEvent(event);
        uploadAction.execute();
        filesDl.load();
        notifications.show(messageBundle.getMessage("ecmUploadFileAlert"));
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
                        Folder folder = new Folder();
                        folder.setName(closeEvent.getValue("name"));
                        folder.setParent(foldersTree.getSingleSelectedItem());
                        folder.setSourceStorage(currentStorage);
                        folderService.createFolder(folder);
                        foldersDl.load();
                        notifications.show(messageBundle.getMessage("ecmCreateFolderAlert"));
                    }
                })
                .open();
    }

    //xóa vào thùng rác
    @Subscribe("foldersTree.delete")
    public void onFoldersTreeDelete(final ActionPerformedEvent event) {
        Folder selected = foldersTree.getSingleSelectedItem();
        if (selected == null) {
            notifications.show("Vui lòng chọn folder để xóa");
            return;
        }
        User userCurr = (User) currentAuthentication.getUser();
        boolean per = permissionService.hasPermission(userCurr, PermissionType.FULL, selected);
        if (!per) {
            notifications.create("Bạn không có quyền xóa thư mục này.")
                    .withType(Notifications.Type.ERROR)
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
                folderService.moveToTrash(selected, currentAuthentication.getUser().getUsername());
                foldersDl.load();
                notifications.show(messageBundle.getMessage("ecmDeleteFolderAlert"));
            } catch (Exception ex) {
                notifications.show("Lỗi " + ex.getMessage());
            }
        });
        dlg.open();
    }

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
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        try {
                            folderService.renameFolder(selected, closeEvent.getValue("name"));
                            foldersDl.load();
                            notifications.show(messageBundle.getMessage("ecmRenameFolderAlert"));
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
        if (selected == null) {
            notifications.create("Vui lòng chọn tệp để xóa").show();
            return;
        }
        User userCurr = (User) currentAuthentication.getUser();
        boolean per = permissionService.hasPermission(userCurr, PermissionType.FULL, selected);
        if (!per) {
            notifications.create("Bạn không có quyền xóa File này.")
                    .withType(Notifications.Type.ERROR)
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
                filesDl.load();
                notifications.show(messageBundle.getMessage("ecmDeleteFileAlert"));
            } catch (Exception e) {
                notifications.show("Lỗi" + e.getMessage());
            }
        });
        dlg.open();
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


