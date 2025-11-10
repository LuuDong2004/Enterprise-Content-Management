package com.vn.ecm.view.folder;
 
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.textfield.TextField;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.entity.SourceStorage;
import com.vn.ecm.service.ecm.folderandfile.IFolderService;
import io.jmix.core.DataManager;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

@ViewController("create-folder-dialog-view")
@ViewDescriptor("create-folder-dialog-view.xml")
public class CreateFolderDialogView extends StandardView {
	@ViewComponent
	private TextField nameField;
	@Autowired
	private IFolderService folderService;
	@Autowired
	private Notifications notifications;
	@ViewComponent
	private MessageBundle messageBundle;
	@Autowired
	private DataManager dataManager;

	private Folder parent;
	private SourceStorage storage;

	public void setContext(Folder parent, SourceStorage storage) {
		this.parent = parent;
		this.storage = storage;
	}

 	@Subscribe(id = "okBtn", subject = "clickListener")
 	public void onOkBtnClick(final ClickEvent<JmixButton> event) {

		String value = nameField.getValue() == null ? "" : nameField.getValue().trim();
		if (value.isEmpty()) {
			nameField.setInvalid(true);
			nameField.setErrorMessage("Tên thư mục không được để trống");
			return;
		}
		Folder exists = folderService.findExistingFolder(parent, storage, value);
		if (exists != null) {
			nameField.setInvalid(true);
			nameField.setErrorMessage(messageBundle.getMessage("ecmCreateFolderExistAlert"));
			return;
		}

		nameField.setInvalid(false);
		Folder f = dataManager.create(Folder.class);
		f.setName(value);
		f.setParent(parent);
		f.setSourceStorage(storage);
		folderService.createFolder(f);
		notifications.show(messageBundle.getMessage("ecmCreateFolderAlert"));
		close(StandardOutcome.SAVE);
	}

 	@Subscribe(id = "cancelBtn", subject = "clickListener")
 	public void onCancelBtnClick(final ClickEvent<JmixButton> event) {
		close(StandardOutcome.CLOSE);
	}
}


