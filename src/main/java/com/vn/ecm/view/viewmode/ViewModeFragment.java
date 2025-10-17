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
import io.jmix.flowui.component.combobox.JmixComboBox;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.fragment.Fragment;
import io.jmix.flowui.fragment.FragmentDescriptor;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.Target;
import io.jmix.flowui.view.View;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.kit.action.Action;

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
        viewMode.setValue(ViewModeType.DEFAULT);
        if (iconTiles != null) iconTiles.setVisible(false);
        // global context menu attached to tiles container so user can right/left click anywhere
        if (iconTiles != null && globalMenu == null) {
            globalMenu = new ContextMenu(iconTiles);
            globalMenu.setOpenOnClick(false); // open by right-click only
            addMenuItems(globalMenu);
            globalMenu.addOpenedChangeListener(ev -> {
                if (!ev.isOpened()) return;
                boolean hasSelection = filesDc != null && filesDc.getItemOrNull() != null;
                if (!hasSelection) {
                    globalMenu.close(); // only open when there is a selection
                    return;
                }
                updateMenuItemsState();
            });
        }
        viewMode.addValueChangeListener(e -> {
            ViewModeType mode = e.getValue() == null ? ViewModeType.DEFAULT : e.getValue();
            switch (mode) {
                case LIST -> { showGrid(); fileViewModeAdapter.applyList(fileDescriptorDataGrid); }
                case MEDIUM_ICONS -> showTiles();
                default -> { showGrid(); fileViewModeAdapter.applyDefault(fileDescriptorDataGrid); }
            }
        });
    }
    private void showGrid() {
        if (fileDescriptorDataGrid != null) fileDescriptorDataGrid.setVisible(true);
        if (iconTiles != null) iconTiles.setVisible(false);
    }

    private void showTiles() {
        if (fileDescriptorDataGrid != null) fileDescriptorDataGrid.setVisible(false);
        if (iconTiles == null || filesDc == null) return;
        iconTiles.removeAll();
        // reset selection between folder/data changes
        clearSelection();
        for (FileDescriptor fd : filesDc.getItems()) {
            iconTiles.add(createTile(fd));
        }
        iconTiles.setVisible(true);
        // ensure global menu exists
        if (globalMenu == null) {
            globalMenu = new ContextMenu(iconTiles);
            globalMenu.setOpenOnClick(false);
            addMenuItems(globalMenu);
            globalMenu.addOpenedChangeListener(ev -> {
                if (ev.isOpened()) updateMenuItemsState();
            });
        }
        updateMenuItemsState();
    }

    private Div createTile(FileDescriptor fd) {
        Div tile = new Div();
        tile.addClassName("file-tile");
        Icon icon = pickIcon(fd).create();
        icon.setSize("48px");
        icon.addClassName("file-icon");
        icon.addClassName(fileTypeClass(fd));
        Span name = new Span(fd.getName());
        name.addClassName("file-name");
        VerticalLayout box = new VerticalLayout(icon, name);
        box.setPadding(false);
        box.setSpacing(false);
        box.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER);
        tile.add(box);

        tile.getStyle().set("cursor", "pointer");

        // Context menu per tile (manual open when already selected and left-clicked)
        ContextMenu menu = new ContextMenu(tile);
        menu.setOpenOnClick(false);
        var deleteItem = menu.addItem("Delete file", e -> executeGridAction("deleteFile"));
        var renameItem = menu.addItem("Rename file", e -> executeGridAction("renameFile"));
        var assignItem = menu.addItem("Assign Permission", e -> executeGridAction("onObjectAssignPermission"));
        menu.addOpenedChangeListener(ev -> {
            // right-click should also select the tile
            if (ev.isOpened() && selectedTile != tile) {
                selectTile(tile, fd);
            }
            boolean isSelectedHere = (selectedTile == tile) && filesDc != null && filesDc.getItemOrNull() != null;
            deleteItem.setEnabled(isSelectedHere);
            renameItem.setEnabled(isSelectedHere);
            assignItem.setEnabled(true);
        });
        tile.addClickListener(e -> {
            handlingTileClick = true;
            if (selectedTile == tile) {
                clearSelection(); // second click on same tile: deselect
            } else {
                selectTile(tile, fd); // first click: select
            }
            handlingTileClick = false;
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
        if (fileDescriptorDataGrid != null) fileDescriptorDataGrid.select(fd);
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
