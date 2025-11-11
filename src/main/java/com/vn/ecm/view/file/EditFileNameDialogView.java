package com.vn.ecm.view.file;


import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vn.ecm.entity.FileDescriptor;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.entity.SourceStorage;
import com.vn.ecm.entity.User;
import com.vn.ecm.service.ecm.folderandfile.IFileDescriptorService;
import com.vn.ecm.view.main.MainView;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "edit-file-name-dialog-view", layout = MainView.class)
@ViewController(id = "EditFileNameDialogView")
@ViewDescriptor(path = "edit-file-name-dialog-view.xml")
public class EditFileNameDialogView extends StandardView {
    @Autowired
    private CurrentAuthentication currentAuthentication;
    @ViewComponent
    private TextField nameField;
    @Autowired
    private IFileDescriptorService fileDescriptorService;

    @Autowired
    private Notifications notifications;
    @ViewComponent
    private MessageBundle messageBundle;
    private Folder currentFolder;
    private FileDescriptor currentFile;
    private SourceStorage storage;


    public void setContext( Folder folder ,FileDescriptor fileDescriptor, SourceStorage storage) {
        this.currentFile = fileDescriptor;
        this.currentFolder = folder;
        this.storage = storage;

    }

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        if (currentFile != null) {
            nameField.setValue(currentFile.getName());
        }
    }

    @Subscribe(id = "okBtn", subject = "clickListener")
    public void onOkBtnClick(final ClickEvent<JmixButton> event) {
        User user = (User) currentAuthentication.getUser();
        String value = nameField.getValue() == null ? "" : nameField.getValue().trim();
        if (value.isEmpty()) {
            nameField.setInvalid(true);
            nameField.setErrorMessage("Tên thư mục không được để trống");
            return;
        }
        if (currentFile == null) {
            close(StandardOutcome.CLOSE);
            return;
        }
        FileDescriptor existing = fileDescriptorService.findByName(currentFolder,storage, value);
        if (existing != null && !existing.getId().equals(currentFile.getId()) ) {
            nameField.setInvalid(true);
            nameField.setErrorMessage("Tệp này đã tồn tại");
            return;
        }
        nameField.setInvalid(false);
        fileDescriptorService.renameFile(currentFile, value, user.getUsername());
        notifications.show("Đổi tên tệp thành công");
        close(StandardOutcome.SAVE);
    }

    @Subscribe(id = "cancelBtn", subject = "clickListener")
    public void onCancelBtnClick(final ClickEvent<JmixButton> event) {
        close(StandardOutcome.CLOSE);
    }

}