package com.vn.ecm.ecmmodule.view.blockinheritance;



import com.vaadin.flow.router.Route;
import com.vn.ecm.ecmmodule.service.ecm.PermissionService;
import com.vn.ecm.ecmmodule.view.main.MainView;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

import com.vn.ecm.ecmmodule.entity.FileDescriptor;
import com.vn.ecm.ecmmodule.entity.Folder;
import com.vn.ecm.ecmmodule.entity.User;

import com.vaadin.flow.component.ClickEvent;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.*;
import io.jmix.securitydata.entity.ResourceRoleEntity;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "block-inheritance", layout = MainView.class)
@ViewController(id = "BlockInheritance")
@ViewDescriptor(path = "block-inheritance.xml")
public class BlockInheritance extends StandardView {

    @Autowired
    private PermissionService permissionService;

    private User targetUser;
    private ResourceRoleEntity targetRole;
    private Folder targetFolder;
    private FileDescriptor targetFile;

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
        // Convert inherited permissions to explicit
        if (targetUser != null) {
            if (targetFolder != null) {
                permissionService.disableInheritance(targetUser, targetFolder, true);
            } else if (targetFile != null) {
                permissionService.disableInheritance(targetUser, targetFile, true);
            }
        } else if (targetRole != null) {
            if (targetFolder != null) {
                permissionService.disableInheritance(targetRole, targetFolder, true);
            } else if (targetFile != null) {
                permissionService.disableInheritance(targetRole, targetFile, true);
            }
        }

        close(StandardOutcome.SAVE);
    }

    @Subscribe(id = "removeBtn", subject = "clickListener")
    public void onRemoveBtnClick(final ClickEvent<JmixButton> event) {
        // Remove inherited permissions
        if (targetUser != null) {
            if (targetFolder != null) {
                permissionService.disableInheritance(targetUser, targetFolder, false);
            } else if (targetFile != null) {
                permissionService.disableInheritance(targetUser, targetFile, false);
            }
        } else if (targetRole != null) {
            if (targetFolder != null) {
                permissionService.disableInheritance(targetRole, targetFolder, false);
            } else if (targetFile != null) {
                permissionService.disableInheritance(targetRole, targetFile, false);
            }
        }

        close(StandardOutcome.SAVE);
    }

    @Subscribe(id = "cancelBtn", subject = "clickListener")
    public void onCancelBtnClick(final ClickEvent<JmixButton> event) {
        close(StandardOutcome.CLOSE);
    }
}