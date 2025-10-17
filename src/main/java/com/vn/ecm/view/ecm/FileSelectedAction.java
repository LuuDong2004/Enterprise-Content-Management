package com.vn.ecm.view.ecm;

import com.vn.ecm.entity.FileDescriptor;

import io.jmix.flowui.action.ActionType;
import io.jmix.flowui.action.list.ItemTrackingAction;

import java.util.function.Consumer;


@ActionType("fileActionSelected")
public class FileSelectedAction extends ItemTrackingAction<FileDescriptor> {
    private Consumer<FileDescriptor> onSelect;
    public FileSelectedAction(String id) {
        super(id);
        setText("Open");
    }

    public FileSelectedAction() {
        this("fileActionSelected");
    }
    public FileSelectedAction onSelect(Consumer<FileDescriptor> handler) {
        this.onSelect = handler;
        return this;
    }

    @Override
    protected boolean isApplicable() {
        return getTarget() != null && getTarget().getSingleSelectedItem() != null;
    }

    @Override
    public void execute() {
        if (getTarget() == null) return;
        FileDescriptor selected = getTarget().getSingleSelectedItem();
        if (selected == null) return;
        if (onSelect != null) onSelect.accept(selected);
    }

}
