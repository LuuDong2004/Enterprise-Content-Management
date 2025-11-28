package com.vn.ecm.view.ecm;

import com.vaadin.flow.component.button.Button;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.entity.SourceStorage;
import com.vn.ecm.view.folder.FolderLazyTreeItems;
import io.jmix.core.DataManager;
import io.jmix.core.Metadata;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import org.springframework.beans.factory.annotation.Autowired;

@ViewController("EcmMoveFolderDialog")
@ViewDescriptor("ecm-move-folder-dialog.xml")
public class EcmMoveFolderDialog extends StandardView {

    @ViewComponent
    private TreeDataGrid<Folder> targetFoldersTree;

    @ViewComponent
    private Button confirmMoveBtn;

    @Autowired
    private DataManager dataManager;

    @Autowired
    private Metadata metadata;

    @Autowired
    private Notifications notifications;

    private Folder sourceFolder;
    private Folder selectedTarget;

    public void setContext(Folder sourceFolder, SourceStorage storage) {
        this.sourceFolder = sourceFolder;
        String conditions = " and e.inTrash = false and e.sourceStorage = :storage";
        FolderLazyTreeItems dataProvider = new FolderLazyTreeItems(dataManager, metadata, conditions, storage);
        targetFoldersTree.setDataProvider(dataProvider);
    }

    public Folder getSelectedTarget() {
        return selectedTarget;
    }

    @Subscribe("confirmMoveBtn")
    public void onConfirmMoveBtnClick(final com.vaadin.flow.component.ClickEvent<Button> event) {
        Folder target = targetFoldersTree.getSelectedItems().stream().findFirst().orElse(null);
        if (target == null) {
            notifications.show("Vui lòng chọn thư mục đích");
            return;
        }
        if (sourceFolder == null) {
            closeWithDefaultAction();
            return;
        }
        selectedTarget = target;
        closeWithDefaultAction();
    }
}
