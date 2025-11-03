package com.company.minio2.view.minio;


import com.company.minio2.dto.BucketDto;
import com.company.minio2.dto.ObjectDto;
import com.company.minio2.dto.TreeNode;
import com.company.minio2.exception.MinioException;
import com.company.minio2.service.minio.IBucketService;
import com.company.minio2.service.minio.IFileService;
import com.company.minio2.view.assignpermissiondialog.AssignPermissionDialog;
import com.company.minio2.view.main.MainView;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.ItemClickEvent;
import com.vaadin.flow.component.grid.ItemDoubleClickEvent;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.component.contextmenu.ContextMenu;

import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.app.inputdialog.DialogActions;
import io.jmix.flowui.app.inputdialog.DialogOutcome;

import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.component.textfield.TypedTextField;
import com.vaadin.flow.component.upload.receivers.MultiFileMemoryBuffer;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.kit.component.button.JmixButton;


import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.InputStream;
import java.net.URLConnection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static io.jmix.flowui.app.inputdialog.InputParameter.stringParameter;

public class MinioViewOps {

    private final MinioView view;

    public MinioViewOps(MinioView view) {
        this.view = view;
    }

    public void view.loadAllBuckets() {
        try {
            List<BucketDto> list = view.bucketService.listBucketFolderTree();
            list.stream()
                    .filter(b -> TreeNode.BUCKET.equals(b.getType()))
                    .findFirst()
                    .ifPresent(first -> {
                        view.buckets.select(first);
                        view.updateState(first.getBucketName(), "");
                        view.refreshFiles();
                    });
            view.bucketsDc.setItems(list);
            view.buckets.addSelectionListener(e -> view.loadObjectFromBucket());
        } catch (Exception e) {
           view.notifications.show("Load view.buckets failed: " + e.getMessage());
        }
    }

    public void view.loadObjectFromBucket() {
        try {
            BucketDto selected = view.buckets.getSingleSelectedItem();
            if (selected == null) {
                view.updateState(null, "");
                view.objectDc.setItems(List.of());
                return;
            }
            BucketDto root = rootOf(selected);
            String bucket = root != null ? root.getBucketName() : null;
            String prefix = TreeNode.FOLDER.equals(selected.getType()) ? selected.getPath() : "";
            view.updateState(bucket, prefix);
            view.refreshFiles();
            view.applyViewMode();
        } catch (Exception e) {
            view.notifications.show("Load object failed: " + e.getMessage());
        }
    }

    public void view.loadFileMetadata(ObjectDto file) {
        if (view.currentBucket == null || view.currentBucket.isBlank()) {
            view.notifications.show("Chọn bucket trước");
            return;
        }
        if (file == null || file.getKey() == null || file.getKey().isBlank()) {
            view.notifications.show("Không có thông tin file");
            return;
        }
        try {
            ObjectDto fileMetadata = view.fileService.getObjectDetail(view.currentBucket, file.getKey());
            view.metadataName.setText(fileMetadata.getName() != null ? fileMetadata.getName() : "N/A");
            view.metadataSize.setText(fileMetadata.getSize() != null ? view.formatFileSize(fileMetadata.getSize()) : "N/A");
            view.metadataLastModified.setText(fileMetadata.getLastModified() != null ? 
                fileMetadata.getLastModified().toString() : "N/A");
            
            // Parse metadata string to extract ETag and Content-Type
            String metadataString = fileMetadata.getPath();
            String etag = "N/A";
            String contentType = "N/A";
            
            if (metadataString != null && !metadataString.isEmpty()) {
                String[] parts = metadataString.split(" \\| ");
                for (String part : parts) {
                    if (part.startsWith("ETag: ")) {
                        etag = part.substring(6);
                    } else if (part.startsWith("Content-Type: ")) {
                        contentType = part.substring(14);
                    }
                }
            }
            
            view.metadataETag.setText(etag);
            view.metadataContentType.setText(contentType);
            
            // Show the metadata panel if it's hidden
            if (!view.metadataVisible) {
                view.metadataVisible = true;
                view.metadataPanel.setVisible(true);
                view.toggleMetadataBtn.setText("Hide");
            }
        } catch (Exception e) {
            view.notifications.show("Không thể load metadata: " + e.getMessage());
        }
    }

    public String view.formatFileSize(Long size) {
        if (size == null) return "N/A";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024.0));
        return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
    }

    public void view.refreshFiles() {
        try {
            if (view.currentBucket == null || view.currentBucket.isBlank()) {
                view.objectDc.setItems(List.of());
                if (view.backBtn != null) view.backBtn.setEnabled(false);
                return;
            }
            List<ObjectDto> list = view.fileService.getAllFromBucket(view.currentBucket, view.currentPrefix);
            view.objectDc.setItems(list);
            view.applyViewMode();
        } catch (Exception e) {
            view.notifications.show("Load object failed: " + e.getMessage());
        }
    }

    public void view.refreshBuckets() {
        try {
            List<BucketDto> list = view.bucketService.listBucketFolderTree();
            view.bucketsDc.setItems(list);
        } catch (Exception e) {
            view.notifications.show("Load view.buckets failed: " + e.getMessage());
        }

    }

    public void view.viewItemObject() {
        if (view.buckets.getColumnByKey("name") != null) {
            view.buckets.removeColumn(view.buckets.getColumnByKey("name"));
        }
        TreeDataGrid.Column<BucketDto> nameColumn = view.buckets.addComponentHierarchyColumn(this::createBucketItem);
        nameColumn.setHeader("Bucket");
        view.buckets.setColumnPosition(nameColumn, 0);
    }

    public void view.viewItemFile() {
        if (view.objects.getColumnByKey("name") != null) {
            view.objects.removeColumn(view.objects.getColumnByKey("name"));
            DataGrid.Column<ObjectDto> nameColumn = view.objects.addComponentColumn(this::createObjectItem);
            nameColumn.setHeader("File");
            view.objects.setColumnPosition(nameColumn, 0);
            view.nameComponentColumn = nameColumn;
        }
    }

    public HorizontalLayout view.createBucketItem(BucketDto item) {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        layout.setPadding(false);
        layout.setSpacing(true);

        Icon icon = view.buildIcon(item.getType(), 16, null);
        icon.getElement().getStyle().set("flex-shrink", "0");
        Span text = new Span(item.getBucketName() != null ? item.getBucketName() : "");
        layout.add(icon, text);
        return layout;
    }

    public HorizontalLayout view.createObjectItem(ObjectDto item) {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        layout.setPadding(false);
        layout.setSpacing(true);

        int px = switch (view.currentViewMode) {
            case LARGE_ICONS -> 48;
            case MEDIUM_ICONS -> 28;
            default -> 16;
        };
        Icon icon = view.buildIcon(item.getType(), px, null);
        icon.getElement().getStyle().set("flex-shrink", "0");

        Span text = new Span(item.getName() != null ? item.getName() : "");
        layout.add(icon, text);
        return layout;
    }

    public void view.applyViewMode() {
        if (view.objects == null) return;
        // Toggle column visibility
        DataGrid.Column<ObjectDto> sizeCol = view.objects.getColumnByKey("size");
        DataGrid.Column<ObjectDto> typeCol = view.objects.getColumnByKey("type");
        DataGrid.Column<ObjectDto> dateCol = view.objects.getColumnByKey("lastModified");

        boolean details = view.isDetailsMode();
        boolean list = view.isListMode();
        boolean iconMode = view.isIconMode();


        // chỉ hiển thị các cột này ở chế độ details
        if (sizeCol != null) sizeCol.setVisible(details);
        if (typeCol != null) typeCol.setVisible(details);
        if (dateCol != null) dateCol.setVisible(details);

//        if (view.nameComponentColumn == null) {
//            view.nameComponentColumn = view.objects.getColumns().stream()
//                    .filter(c -> c.getKey() == null) // component column usually has no property key
//                    .findFirst().orElse(null);
//        }
        if (view.nameComponentColumn != null) {
            if (details || list) {
                view.nameComponentColumn.setHeader("Name");
            } else {
                view.nameComponentColumn.setHeader("");
            }
        }
        view.switchObjectsContainerVisibility(iconMode);
        view.updateObjectsView();
    }

    public void view.renderIcons() {
        if (view.iconContainer == null) return;
        view.iconContainer.removeAll();
        view.iconContainer.addClassName("icon-container");
        view.iconContainer.setPadding(false);
        view.iconContainer.setSpacing(false);
        view.iconContainer.getStyle().set("flex-wrap", "wrap"); // để các icon tự động xuống dòng khi hết chỗ
        view.iconContainer.getStyle().set("gap", "2px");

        int iconPx = view.currentViewMode == ViewMode.LARGE_ICONS ? 64 : 40;
        int boxW = view.currentViewMode == ViewMode.LARGE_ICONS ? 92 : view.currentViewMode == ViewMode.MEDIUM_ICONS ? 72 : 220;

        List<ObjectDto> items = view.objectDc.getItems();
        if (items == null) return;
        for (ObjectDto it : items) {
            VerticalLayout box = new VerticalLayout();
            box.setPadding(false);
            box.setSpacing(false);
            box.setAlignItems(FlexComponent.Alignment.CENTER);
            box.addClassName("icon-card");
            box.setWidth(boxW + "px");
            box.setHeight(null);
            box.setMargin(false);

            VerticalLayout content = new VerticalLayout();
            content.setPadding(false);
            content.setSpacing(false);
            content.setAlignItems(FlexComponent.Alignment.CENTER);
            content.addClassName("icon-card-inner");

            Icon icon = view.buildIcon(it.getType(), iconPx, "icon");

            Span label = new Span(it.getName() != null ? it.getName() : "");
            label.addClassName("icon-title");
            label.setWidthFull();
            // center and wrap to 2 lines like Windows Explorer
            label.getStyle().set("text-align", "center");
            label.getStyle().set("white-space", "normal");
            label.getStyle().set("word-break", "break-word");
            label.getStyle().set("display", "-webkit-box");
            label.getStyle().set("-webkit-line-clamp", "2");
            label.getStyle().set("-webkit-box-orient", "vertical");
            label.getStyle().set("overflow", "hidden");

            content.add(icon, label);
            box.add(content);

            box.addClickListener(e -> {
                view.selectedIconItemId = view.stableItemId(it);
                if (it.getType() == TreeNode.FOLDER) {
                    view.updateState(view.currentBucket, view.computeNextPrefix(it));
                    view.refreshFiles();
                    view.renderIcons();
                } else if (it.getType() == TreeNode.FILE) {
                    view.loadFileMetadata(it);
                }
            });

            view.attachIconContextMenu(box, it);

            // highlight selection
            if (view.stableItemId(it).equals(view.selectedIconItemId)) {
                content.getStyle().set("outline", "2px solid var(--lumo-primary-color)");
                content.getStyle().set("background", "var(--lumo-primary-color-10pct)");
                content.getStyle().set("border-radius", "6px");
                content.getStyle().set("padding", "4px");
            }
            view.iconContainer.add(box);
        }
    }

    public void view.updateObjectsView() {
        if (view.isIconMode()) {
            view.renderIcons();
        } else {
            if (view.objects != null && view.objects.getDataProvider() != null) {
                view.objects.getDataProvider().refreshAll();
            }
        }
    }

    public void view.switchObjectsContainerVisibility(boolean iconMode) {
        if (view.iconContainer != null) view.iconContainer.setVisible(iconMode);
        if (view.objects != null) view.objects.setVisible(!iconMode);
    }

    public boolean view.isIconMode() {
        return view.currentViewMode == ViewMode.MEDIUM_ICONS || view.currentViewMode == ViewMode.LARGE_ICONS;
    }

    public boolean view.isListMode() {
        return view.currentViewMode == ViewMode.LIST;
    }

    public boolean view.isDetailsMode() {
        return view.currentViewMode == ViewMode.DETAILS;
    }

    public String view.computeNextPrefix(ObjectDto item) {
        String key = item.getKey();
        if (key != null && !key.isBlank()) return key;
        String path = item.getPath();
        if (path != null && !path.isBlank()) return path;
        return item.getName();
    }

    public String view.stableItemId(ObjectDto item) {
        String key = item.getKey();
        if (key != null && !key.isBlank()) return key;
        String path = item.getPath();
        if (path != null && !path.isBlank()) return path;
        return item.getName();
    }

    public Icon view.buildIcon(TreeNode type, int sizePx, String extraClass) {
        Icon icon;
        if (type == TreeNode.FOLDER) {
            icon = VaadinIcon.FOLDER.create();
            icon.addClassName("folder-item");
        } else if (type == TreeNode.FILE) {
            icon = VaadinIcon.FILE.create();
            icon.addClassName("file-item");
        } else if (type == TreeNode.BUCKET) {
            icon = VaadinIcon.ARCHIVE.create();
            icon.addClassName("bucket-item");
        } else {
            icon = VaadinIcon.FILE_O.create();
            icon.addClassName("file-item");
        }
        if (extraClass != null) icon.addClassName(extraClass);
        icon.setSize(sizePx + "px");
        return icon;
    }

    public void view.attachIconContextMenu(VerticalLayout target, ObjectDto item) {
        ContextMenu menu = new ContextMenu(target);
        menu.setOpenOnClick(false); // right-click

        menu.addItem("New Folder", e -> view.openCreateFolderDialog());
        menu.addItem("Delete", e -> view.deleteObject(item));
        menu.addItem("Download", e -> view.downloadObject(item));
        menu.addItem("Upload file", e -> Notification.show("Use toolbar Upload"));

        menu.addOpenedChangeListener(ev -> {
            if (ev.isOpened()) {
                view.selectedIconItemId = view.stableItemId(item);
                // re-apply highlight when menu opens via right click
                view.renderIcons();
            }
        });
    }

    public void view.openCreateFolderDialog() {
        if (view.currentBucket == null || view.currentBucket.isBlank()) {
            view.notifications.show("Vui lòng chọn bucket trước!");
            return;
        }
        view.dialogs.createInputDialog(this)
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
                            String objectKey = closeEvent.getValue("name");
                            view.fileService.createNewObject(view.currentBucket, view.currentPrefix, objectKey);
                            view.refreshBuckets();
                            view.refreshFiles();
                            view.notifications.show("Tạo mới folder thành công");
                        } catch (MinioException e) {
                            view.notifications.show("Không thể tạo mới folder" + e);
                        }
                    }
                })
                .open();
    }

    public void view.deleteObject(ObjectDto object) {
        if (view.currentBucket == null || view.currentBucket.isBlank()) {
            Notification.show("Chưa chọn bucket");
            return;
        }
        if (object == null || object.getKey() == null || object.getKey().isBlank()) {
            Notification.show("Chọn folder or file để xóa!");
            return;
        }
        ConfirmDialog dlg = new ConfirmDialog();
        dlg.setHeader("Xác nhận");
        dlg.setText("Xóa file '" + object.getName() + " ?");
        dlg.setCancelable(true);
        dlg.setConfirmText("Xóa");
        dlg.addConfirmListener(e2 -> {
            try {
                view.fileService.delete(view.currentBucket, object.getKey());
                Notification.show("Đã xóa file: " + object.getName());
                view.refreshFiles();
                view.refreshBuckets();
            } catch (Exception ex) {
                view.notifications.show("Không thể xóa bucket (có thể bucket chưa rỗng)." + ex);
            }
        });
        dlg.open();
    }

    public void view.downloadObject(ObjectDto selected) {
        if (selected == null || selected.getKey() == null || selected.getKey().isBlank()) {
            Notification.show("Chưa chọn file");
            return;
        }
        if (view.currentBucket == null || view.currentBucket.isBlank()) {
            Notification.show("Chưa chọn bucket");
            return;
        }
        try {
            String url = view.fileService.download(view.currentBucket, selected.getKey(), 300);
            getUI().ifPresent(ui -> ui.getPage().open(url));
            Notification.show("Downloading '" + selected.getKey() + "'");
        } catch (Exception e) {
            Notification.show("Tải xuống thất bại: " + e.getMessage());
        }
    }

    public void view.updateState(String bucket, String prefix) {
        view.currentBucket = bucket;
        view.currentPrefix = norm(prefix);
        if (view.prefixField != null) view.prefixField.setValue(view.currentPrefix);
        if (view.backBtn != null) view.backBtn.setEnabled(!view.currentPrefix.isBlank());
    }

    public void view.clearMetadata() {
        view.metadataName.setText("N/A");
        view.metadataSize.setText("N/A");
        view.metadataLastModified.setText("N/A");
        view.metadataETag.setText("N/A");
        view.metadataContentType.setText("N/A");
    }
}
