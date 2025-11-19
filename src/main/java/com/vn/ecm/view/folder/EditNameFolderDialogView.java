package com.vn.ecm.view.folder;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.textfield.TextField;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.entity.SourceStorage;
import com.vn.ecm.service.ecm.folderandfile.IFolderService;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

@ViewController("EditNameFolderDialogView")
@ViewDescriptor("edit-name-folder-dialog-view.xml")
public class EditNameFolderDialogView extends StandardView {
    @ViewComponent
    private TextField nameField;
    @Autowired
    private IFolderService folderService;
    @Autowired
    private Notifications notifications;
    @ViewComponent
    private MessageBundle messageBundle;

    private Folder currentFolder;
    private SourceStorage storage;

    public void setContext(Folder folder, SourceStorage storage) {
        this.currentFolder = folder;
        this.storage = storage;
    }

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        if (currentFolder != null) {
            nameField.setValue(currentFolder.getName());
        }
    }

    @Subscribe(id = "okBtn", subject = "clickListener")
    public void onOkBtnClick(final ClickEvent<JmixButton> event) {
        String value = nameField.getValue() == null ? "" : nameField.getValue().trim();
        if (value.isEmpty()) {
            nameField.setInvalid(true);
            nameField.setErrorMessage("Tên thư mục không được để trống");
            return;
        }
        if (currentFolder == null) {
            close(StandardOutcome.CLOSE);
            return;
        }
        Folder existing = folderService.findExistingFolder(currentFolder.getParent(), storage, value);
        if (existing != null && !existing.getId().equals(currentFolder.getId())) {
            nameField.setInvalid(true);
            nameField.setErrorMessage(messageBundle.getMessage("ecmCreateFolderExistAlert"));
            return;
        }
        nameField.setInvalid(false);
        folderService.renameFolder(currentFolder, value);
        notifications.show(messageBundle.getMessage("ecmRenameFolderAlert"));
        close(StandardOutcome.SAVE);
    }

    @Subscribe(id = "cancelBtn", subject = "clickListener")
    public void onCancelBtnClick(final ClickEvent<JmixButton> event) {

        close(StandardOutcome.CLOSE);
	}
}


