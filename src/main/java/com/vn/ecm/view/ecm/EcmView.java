package com.vn.ecm.view.ecm;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.ItemClickEvent;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.*;
import com.vn.ecm.ecm.storage.DynamicStorageManager;
import com.vn.ecm.entity.*;
import com.vn.ecm.view.assignpermission.AssignPermissionView;
import com.vn.ecm.view.main.MainView;
import io.jmix.core.DataManager;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import io.jmix.flowui.*;
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
    private CollectionLoader<Folder> foldersDl;
    @ViewComponent
    private DataGrid<FileDescriptor> fileDataGird;
    @ViewComponent
    private CollectionLoader<FileDescriptor> filesDl;
    @Autowired
    private Notifications notifications;
    @Autowired
    private DataManager dataManager;

    @Autowired
    private DynamicStorageManager dynamicStorageManager;
    @Autowired
    private UiComponents uiComponents;
    @Autowired
    private DialogWindows dialogWindows;

    private SourceStorage currentStorage;

    private UUID id;

    @Autowired
    private Dialogs dialogs;
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



    @Subscribe
    public void onInit(InitEvent event) {
        initFolderGridColumn();
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
        // FOLDERS
        foldersDl.setParameter("storage", currentStorage);
        foldersDl.load();
        foldersTree.expandRecursively(foldersDc.getItems(), 1);

        filesDl.setParameter("storage", currentStorage);
        filesDl.setParameter("folder", null);
    }
    @Subscribe("foldersTree")
    public void onFoldersTreeItemClick(ItemClickEvent<Folder> e) {
        Folder selected = e.getItem();
        SourceStorage storage = currentStorage;
        if (storage == null) return;
        filesDl.setParameter("storage", storage);
        filesDl.setParameter("folder", selected);
        filesDl.load();
    }


    //upload file
    @Subscribe("fileRefField")
    public void onFileRefFieldFileUploadSucceeded(final FileUploadSucceededEvent<FileStorageUploadField> event) {
        uploadAction.setUploadEvent(event);
        uploadAction.execute();
        filesDl.load();
        notifications.show(messageBundle.getMessage("ecmUploadFileAlert"));
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
                    notifications.show(messageBundle.getMessage("ecmDeleteFolderAlert"));
                    return;
                }
                dataManager.remove(selected);
                Notification.show("Đã xóa folder: " + selected.getName());
                foldersDl.load();
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
}
