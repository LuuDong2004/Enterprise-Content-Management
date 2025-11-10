package com.vn.ecm.view.viewmode;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vn.ecm.entity.FileDescriptor;
import com.vn.ecm.entity.ViewModeType;
import com.vn.ecm.service.ecm.viewmode.IViewModeAdapter;
import com.vn.ecm.service.ecm.viewmode.Impl.ViewModeApdapterImpl;
import io.jmix.flowui.action.list.ItemTrackingAction;
import io.jmix.flowui.component.combobox.JmixComboBox;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.fragment.Fragment;
import io.jmix.flowui.fragment.FragmentDescriptor;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.view.Subscribe;

import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.kit.action.Action;

import static com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER;

@FragmentDescriptor("view-mode-fragment.xml")
public class ViewModeFragment extends Fragment<HorizontalLayout> {

    @ViewComponent
    private JmixComboBox<ViewModeType> viewMode;

    private DataGrid<FileDescriptor> fileDescriptorDataGrid;
    private CollectionContainer<FileDescriptor> filesDc;
    private HorizontalLayout iconTiles;
    private Div selectedTile;
    private ContextMenu globalMenu;
    private MenuItem globalDeleteItem;
    private MenuItem globalRenameItem;
    private MenuItem globalAssignItem;
    private boolean handlingTileClick = false;

    private final IViewModeAdapter<FileDescriptor> fileViewModeAdapter =
            new ViewModeApdapterImpl<>(ViewModeApdapterImpl.Kind.FILE);

    /**
     * Gọi một lần từ EcmView để đăng ký grid và container cho fragment.
     */
    public void bind(DataGrid<FileDescriptor> grid,
                     CollectionContainer<FileDescriptor> dc,
                     HorizontalLayout tilesContainer) {
        this.fileDescriptorDataGrid = grid;
        this.filesDc = dc;
        this.iconTiles = tilesContainer;
        if (this.fileDescriptorDataGrid != null) {
            fileViewModeAdapter.applyDefault(this.fileDescriptorDataGrid);
        }
        if (this.filesDc != null) {
            this.filesDc.addCollectionChangeListener(event -> {
                if (iconTiles != null && iconTiles.isVisible()) {
                    showTiles();
                }
            });
            this.filesDc.addItemChangeListener(event -> updateMenuItemsState());
        }
        if (this.iconTiles != null) {
            this.iconTiles.addClickListener(e -> {
                if (handlingTileClick) return; // ignore if a tile handled the click
                clearSelection();
            });
        }
    }

    @Subscribe
    public void onReady(Fragment.ReadyEvent event) {
        viewMode.setItemLabelGenerator(ViewModeType::toString);
        viewMode.setValue(ViewModeType.DEFAULT);
        if (iconTiles != null) iconTiles.setVisible(false);

        // KHÔNG tạo global menu ở đây vì sẽ xung đột với tile menu
        // Mỗi tile đã có menu riêng trong createTile()
        // Global menu chỉ cần khi click vào vùng trống (không phải tile)
        // Nhưng trong trường hợp này, không cần global menu vì:
        // 1. Mỗi tile đã có menu riêng
        // 2. Global menu sẽ xung đột và gây ra hiện tượng menu mở rồi đóng ngay

        viewMode.addValueChangeListener(e -> {
            ViewModeType mode = e.getValue() == null ? ViewModeType.DEFAULT : e.getValue();
            switch (mode) {
                case LIST -> {
                    showGrid();
                    fileViewModeAdapter.applyList(fileDescriptorDataGrid);
                }
                case MEDIUM_ICONS -> showTiles();
                default -> {
                    showGrid();
                    fileViewModeAdapter.applyDefault(fileDescriptorDataGrid);
                }
            }
        });
    }
    private void showGrid() {
        if (fileDescriptorDataGrid != null) fileDescriptorDataGrid.setVisible(true);
        if (iconTiles != null) iconTiles.setVisible(false);
    }

    private void showTiles() {
        // Ẩn datagrid nhưng vẫn giữ enabled để selection hoạt động
        if (fileDescriptorDataGrid != null) {
            fileDescriptorDataGrid.setVisible(false);
            fileDescriptorDataGrid.setEnabled(true);
        }

        if (iconTiles == null || filesDc == null) return;

        // Lưu selection hiện tại để restore sau
        FileDescriptor currentSelection = filesDc.getItemOrNull();

        iconTiles.removeAll();

        // Reset selection giữa các lần thay đổi folder/data
        clearSelection();

        // Tạo tiles cho tất cả files
        for (FileDescriptor fd : filesDc.getItems()) {
            iconTiles.add(createTile(fd));
        }

        iconTiles.setVisible(true);

        // Restore selection nếu có
        if (currentSelection != null && filesDc.getItems().contains(currentSelection)) {
            java.util.List<com.vaadin.flow.component.Component> children =
                    iconTiles.getChildren().collect(java.util.stream.Collectors.toList());

            for (int i = 0; i < children.size() && i < filesDc.getItems().size(); i++) {
                com.vaadin.flow.component.Component component = children.get(i);
                if (component instanceof Div) {
                    Div tile = (Div) component;
                    FileDescriptor fd = filesDc.getItems().get(i);
                    if (fd.equals(currentSelection)) {
                        selectTile(tile, fd);
                        break;
                    }
                }
            }
        }
        updateMenuItemsState();
    }

    private Div createTile(FileDescriptor fd) {
        Div tile = new Div();
        tile.addClassName("file-tile");

        // Lưu reference đến FileDescriptor
        tile.getElement().setProperty("fileDescriptorId",
                fd.getId() != null ? fd.getId().toString() : "");

        // Tạo icon
        Icon icon = pickIcon(fd).create();
        icon.setSize("48px");
        icon.addClassName("file-icon");
        icon.addClassName(fileTypeClass(fd));

        // Tạo tên file
        Span name = new Span(fd.getName());
        name.addClassName("file-name");

        // Layout chứa icon và tên
        VerticalLayout box = new VerticalLayout(icon, name);
        box.setPadding(false);
        box.setSpacing(false);
        box.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER);
        tile.add(box);

        tile.getStyle().set("cursor", "pointer");

        // Context menu cho mỗi tile - CHỈ MỞ BẰNG CHUỘT PHẢI
        ContextMenu menu = new ContextMenu(tile);
        menu.setOpenOnClick(false);

        var deleteItem = menu.addItem("Delete file", e -> {
            executeGridAction("deleteFile");
        });

        var renameItem = menu.addItem("Rename file", e -> {
            executeGridAction("renameFile");
        });

        var assignItem = menu.addItem("Assign Permission", e -> {
            executeGridAction("onObjectAssignPermission");
        });

        // QUAN TRỌNG: Đóng menu ngay lập tức nếu chưa có selection
        // Sử dụng UI.access để đóng đồng bộ
        menu.addOpenedChangeListener(ev -> {
            if (ev.isOpened()) {
                // Kiểm tra ngay lập tức
                boolean isSelectedHere = (selectedTile == tile) &&
                        filesDc != null &&
                        filesDc.getItemOrNull() != null &&
                        filesDc.getItemOrNull().equals(fd);

                if (!isSelectedHere) {
                    // Đóng menu đồng bộ ngay lập tức
                    com.vaadin.flow.component.UI.getCurrent().accessSynchronously(() -> {
                        menu.close();
                    });
                    return;
                }

                // Enable actions nếu đã được chọn
                deleteItem.setEnabled(true);
                renameItem.setEnabled(true);
                assignItem.setEnabled(true);
            }
        });

        // Click handler cho tile - CHỈ XỬ LÝ CHUỘT TRÁI
        tile.addClickListener(e -> {
            handlingTileClick = true;

            try {
                if (selectedTile == tile) {
                    clearSelection();
                } else {
                    selectTile(tile, fd);
                }
            } finally {
                com.vaadin.flow.component.UI.getCurrent().access(() -> {
                    handlingTileClick = false;
                });
            }
        });

        return tile;
    }

    private void executeGridAction(String actionId) {
        if (fileDescriptorDataGrid == null) return;
        Action action = fileDescriptorDataGrid.getAction(actionId);
        if (action != null) {
            action.actionPerform(fileDescriptorDataGrid);
        }
    }
    private void addMenuItems(ContextMenu menu) {
        menu.removeAll();
        globalDeleteItem = menu.addItem("Delete file", e -> executeGridAction("deleteFile"));
        globalRenameItem = menu.addItem("Rename file", e -> executeGridAction("renameFile"));
        globalAssignItem = menu.addItem("Assign Permission", e -> executeGridAction("onObjectAssignPermission"));
        updateMenuItemsState();
    }
    private void updateMenuItemsState() {
        boolean hasSelection = false;
        if (filesDc != null) {
            try {
                hasSelection = filesDc.getItemOrNull() != null;
            } catch (Exception ignored) {
                hasSelection = false;
            }
        }
        if (globalDeleteItem != null) globalDeleteItem.setEnabled(hasSelection);
        if (globalRenameItem != null) globalRenameItem.setEnabled(hasSelection);
        if (globalAssignItem != null) globalAssignItem.setEnabled(true);
    }
    private void clearSelection() {
        if (selectedTile != null) {
            selectedTile.removeClassName("file-tile-selected");
            selectedTile = null;
        }
        if (filesDc != null) {
            try { filesDc.setItem(null); } catch (Exception ignored) {}
        }
        if (fileDescriptorDataGrid != null) {
            fileDescriptorDataGrid.deselectAll();
        }
        updateMenuItemsState();
    }
    private void selectTile(Div tile, FileDescriptor fd) {
        if (selectedTile != null) selectedTile.removeClassName("file-tile-selected");
        selectedTile = tile;
        tile.addClassName("file-tile-selected");
        if (filesDc != null) filesDc.setItem(fd);
        if (fileDescriptorDataGrid != null) {
            // Đảm bảo datagrid selection được cập nhật ngay cả khi ẩn
            fileDescriptorDataGrid.select(fd);
            // Refresh actions để chúng được enable/disable đúng cách
            fileDescriptorDataGrid.getActions().forEach(action -> {
                if (action instanceof ItemTrackingAction) {
                    ((ItemTrackingAction<?>) action).refreshState();
                }
            });
        }
        updateMenuItemsState();
    }

    private VaadinIcon pickIcon(FileDescriptor fd) {
        String ext = fd.getExtension() == null ? "" : fd.getExtension().toLowerCase(java.util.Locale.ROOT);
        return switch (ext) {
            case "png", "jpg", "jpeg", "gif", "bmp", "svg" -> VaadinIcon.PICTURE;
            case "pdf" -> VaadinIcon.FILE_TEXT;
            case "xls", "xlsx" -> VaadinIcon.FILE_TABLE;
            case "doc", "docx" -> VaadinIcon.FILE_TEXT_O;
            case "zip", "rar", "7z" -> VaadinIcon.ARCHIVE;
            case "mp3", "wav", "flac" -> VaadinIcon.MUSIC;
            case "mp4", "avi", "mkv", "mov" -> VaadinIcon.FILM;
            default -> VaadinIcon.FILE_O;
        };
    }

    private String fileTypeClass(FileDescriptor fd) {
        String ext = fd.getExtension() == null ? "" : fd.getExtension().toLowerCase(java.util.Locale.ROOT);
        return switch (ext) {
            case "png", "jpg", "jpeg", "gif", "bmp", "svg" -> "ext-image";
            case "pdf" -> "ext-pdf";
            case "xls", "xlsx" -> "ext-excel";
            case "doc", "docx" -> "ext-word";
            case "ppt", "pptx" -> "ext-powerpoint";
            case "zip", "rar", "7z" -> "ext-archive";
            case "mp3", "wav", "flac" -> "ext-audio";
            case "mp4", "avi", "mkv", "mov" -> "ext-video";
            case "txt" -> "ext-text";
            default -> "ext-file";
        };
    }
}
