package com.vn.ecm.view.confirmreplacefolder;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.router.Route;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.view.main.MainView;
import io.jmix.flowui.kit.component.button.JmixButton;


import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.*;

@Route(value = "confirm-replace-folder-view", layout = MainView.class)
@ViewController(id = "ConfirmReplaceFolderView")
@ViewDescriptor(path = "confirm-replace-folder-view.xml")
public class ConfirmReplaceFolderView extends StandardView {
    @ViewComponent
    private InstanceContainer<Folder> newFolderDc;
    @ViewComponent
    private InstanceContainer<Folder> existingFolderDc;

    public void setFolderData(Folder existingFolder, Folder newFolder) {
        existingFolderDc.setItem(existingFolder);
        newFolderDc.setItem(newFolder);
    }

    @Subscribe(id = "yesBtn", subject = "clickListener")
    public void onYesBtnClick(final ClickEvent<JmixButton> event) {
        close(StandardOutcome.SAVE);
    }

    @Subscribe(id = "noBtn", subject = "clickListener")
    public void onNoBtnClick(final ClickEvent<JmixButton> event) {
        close(StandardOutcome.CLOSE);
    }
}