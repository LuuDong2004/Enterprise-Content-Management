package com.vn.ecm.view.sourcestorage;

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Route;
import com.vn.ecm.ecm.storage.s3.S3ClientFactory;
import com.vn.ecm.entity.SourceStorage;
import com.vn.ecm.entity.StorageType;
import com.vn.ecm.view.main.MainView;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.formlayout.JmixFormLayout;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.DataContext;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;


@Route(value = "source-storages/edit/:id", layout = MainView.class)
@ViewController(id = "SourceStorage.detail")
@ViewDescriptor(path = "source-storage-detail-view.xml")
@EditedEntityContainer("sourceStorageDc")
@DialogMode(width = "60%", height = "90%")
public class SourceStorageDetailView extends StandardDetailView<SourceStorage> {
    @ViewComponent
    private Select<StorageType> typeField;

    @ViewComponent
    private JmixFormLayout s3Group;
    @ViewComponent
    private JmixFormLayout webDirGroup;
    @ViewComponent
    private JmixButton testConnection;
    @Autowired
    private S3ClientFactory s3ClientFactory;
    @Autowired
    private Notifications notifications;

    @Subscribe
    public void onInit(final InitEvent event) {
        updateGroups(typeField.getValue());
    }

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        updateGroups(typeField.getValue());
        SourceStorage s = getEditedEntity();
        testConnection.setVisible(s.getType() == StorageType.S3);
    }


    @Subscribe(id = "testConnection", subject = "clickListener")
    public void onTestConnectionClick(final ClickEvent<JmixButton> event) {
        SourceStorage sourceStorage = getEditedEntityContainer().getItemOrNull();
        String result = s3ClientFactory.testConnection(sourceStorage);
        Notifications.Type type = result.trim().startsWith("Kết nối thành công")
                || result.trim().startsWith("✅")
                ? Notifications.Type.SUCCESS
                : Notifications.Type.ERROR;
        notifications.create(result)
                .withType(type)
                .withCloseable(false)
                .withDuration(2000)
                .show();
    }

    @Subscribe(target = Target.DATA_CONTEXT)
    public void onPreSave(DataContext.PreSaveEvent event) {
        SourceStorage s = getEditedEntity();
        if (s.getType() == StorageType.S3) {
            String mess = s3ClientFactory.testConnection(s);
            if (!mess.startsWith("Kết nối thành công")) {
                notifications.create(mess + "( Cấu hình vẫn đươc lưu !)")
                        .withType(Notifications.Type.WARNING)
                        .withCloseable(false)
                        .withDuration(2000)
                        .show();
            }
            // event.preventSave();
        }
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