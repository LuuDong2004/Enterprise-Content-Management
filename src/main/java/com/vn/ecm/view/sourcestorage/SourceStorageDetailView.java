package com.vn.ecm.view.sourcestorage;

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.Route;
import com.vn.ecm.entity.SourceStorage;
import com.vn.ecm.entity.StorageType;
import com.vn.ecm.view.main.MainView;
import io.jmix.flowui.component.formlayout.JmixFormLayout;
import io.jmix.flowui.view.*;


@Route(value = "source-storages/edit/:id", layout = MainView.class)
@ViewController(id = "SourceStorage.detail")
@ViewDescriptor(path = "source-storage-detail-view.xml")
@EditedEntityContainer("sourceStorageDc")
@DialogMode(width = "60%" , height = "90%")
public class SourceStorageDetailView extends StandardDetailView<SourceStorage> {
    @ViewComponent
    private Select<StorageType> typeField;

    @ViewComponent
    private JmixFormLayout s3Group;
    @ViewComponent
    private JmixFormLayout webDirGroup;

    @Subscribe
    public void onInit(final InitEvent event) {
        updateGroups(typeField.getValue());
    }

    @Subscribe("typeField")
    public void onTypeFieldValueChange(final AbstractField.ComponentValueChangeEvent<Select<StorageType>, StorageType> e) {
        updateGroups(e.getValue());
    }
    private void updateGroups(StorageType type) {
        // Ẩn tất cả
        setVisible(s3Group, false);
        setVisible(webDirGroup, false);
        if (type == null) return;
        switch (type) {
            case S3 -> setVisible(s3Group, true);
            case WEBDIR -> setVisible(webDirGroup, true);
        }
    }
    private void setVisible(Component c, boolean v) {
        if (c != null) c.setVisible(v);
    }
}