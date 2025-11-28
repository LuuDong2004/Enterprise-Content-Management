package com.vn.ecm.view.blockinheritance;

import com.vaadin.flow.router.Route;
import com.vn.ecm.view.main.MainView;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

import com.vn.ecm.entity.FileDescriptor;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.entity.User;

import com.vaadin.flow.component.ClickEvent;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.*;
import io.jmix.securitydata.entity.ResourceRoleEntity;

@Route(value = "block-inheritance", layout = MainView.class)
@ViewController(id = "BlockInheritance")
@ViewDescriptor(path = "block-inheritance.xml")
public class BlockInheritance extends StandardView {

    private User targetUser;
    private ResourceRoleEntity targetRole;
    private Folder targetFolder;
    private FileDescriptor targetFile;

    // Lưu action được chọn
    private BlockInheritanceAction selectedAction = BlockInheritanceAction.CANCEL;

    public void setTargetUserFolder(User user, Folder folder) {
        this.targetUser = user;
        this.targetFolder = folder;
        this.targetFile = null;
        this.targetRole = null;
    }

    public void setTargetUserFile(User user, FileDescriptor file) {
        this.targetUser = user;
        this.targetFile = file;
        this.targetFolder = null;
        this.targetRole = null;
    }

    public void setTargetRoleFolder(ResourceRoleEntity role, Folder folder) {
        this.targetRole = role;
        this.targetFolder = folder;
        this.targetFile = null;
        this.targetUser = null;
    }

    public void setTargetRoleFile(ResourceRoleEntity role, FileDescriptor file) {
        this.targetRole = role;
        this.targetFile = file;
        this.targetFolder = null;
        this.targetUser = null;
    }

    @Subscribe(id = "convertBtn", subject = "clickListener")
    public void onConvertBtnClick(final ClickEvent<JmixButton> event) {
        // Chỉ đánh dấu action, không thực hiện ngay
        selectedAction = BlockInheritanceAction.CONVERT;
        close(StandardOutcome.SAVE);
    }

    @Subscribe(id = "removeBtn", subject = "clickListener")
    public void onRemoveBtnClick(final ClickEvent<JmixButton> event) {
        // Chỉ đánh dấu action, không thực hiện ngay
        selectedAction = BlockInheritanceAction.REMOVE;
        close(StandardOutcome.SAVE);
    }

    @Subscribe(id = "cancelBtn", subject = "clickListener")
    public void onCancelBtnClick(final ClickEvent<JmixButton> event) {
        selectedAction = BlockInheritanceAction.CANCEL;
        close(StandardOutcome.CLOSE);
    }

    public BlockInheritanceAction getSelectedAction() {
        return selectedAction;
    }

    public User getTargetUser() {
        return targetUser;
    }

    public ResourceRoleEntity getTargetRole() {
        return targetRole;
    }

    public Folder getTargetFolder() {
        return targetFolder;
    }

    public FileDescriptor getTargetFile() {
        return targetFile;
    }
}