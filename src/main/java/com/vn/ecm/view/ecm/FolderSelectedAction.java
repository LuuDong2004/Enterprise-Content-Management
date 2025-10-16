package com.vn.ecm.view.ecm;

import com.vn.ecm.entity.Folder;
import io.jmix.flowui.action.ActionType;
import io.jmix.flowui.action.list.ItemTrackingAction;
import java.util.function.Consumer;

@ActionType("folderActionSelected")
public class FolderSelectedAction extends ItemTrackingAction<Folder> {

    private Consumer<Folder> onSelect;

    public FolderSelectedAction(String id) {
        super(id);
        setText("Open");
    }

    public FolderSelectedAction() {
        this("folderActionSelected");
    }

    public FolderSelectedAction onSelect(Consumer<Folder> handler) {
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
        Folder selected = getTarget().getSingleSelectedItem();
        if (selected == null) return;
        if (onSelect != null) onSelect.accept(selected);
    }
}
