package com.vn.ecm.view.sourcestorage;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.Route;
import com.vn.ecm.ecm.storage.ftp.FtpTestConnect;
import com.vn.ecm.entity.FtpStorageEntity;
import com.vn.ecm.entity.SourceStorage;
import com.vn.ecm.entity.StorageType;
import com.vn.ecm.view.main.MainView;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.model.InstanceLoader;
import io.jmix.flowui.view.*;
import org.bson.BinaryVector;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "ftp-storage-entities/:id", layout = MainView.class)
@ViewController(id = "FtpStorageEntity.detail")
@ViewDescriptor(path = "ftp-storage-entity-detail-view.xml")
@EditedEntityContainer("ftpStorageEntityDc")
public class FtpStorageEntityDetailView extends StandardDetailView<FtpStorageEntity> {

    @Autowired
    private DialogWindows dialogWindows;

    @Autowired
    private FtpTestConnect  ftpTestConnect;
    @ViewComponent
    private InstanceContainer<FtpStorageEntity> ftpStorageEntityDc;
    @ViewComponent
    private InstanceLoader<FtpStorageEntity> ftpStorageEntityDl;
    @Autowired
    private Notifications notifications;


    @Subscribe("editDetailFtpStorage")
    public void onEditDetailFtpStorage(final ActionPerformedEvent event) {
        FtpStorageEntity selected = getEditedEntity();

        DialogWindow<View<?>> window = dialogWindows.detail(this,FtpStorageEntity.class )
                .newEntity(selected)
                .build();
        window.addAfterCloseListener(afterCloseEvent -> {
            ftpStorageEntityDl.getEntityId();
        });
        window.setWidth("40%");
        window.setHeight("auto");
        window.setResizable(true);
        window.open();
    }


    @Subscribe(id = "detailActions", subject = "clickListener")
    public void onDetailActionsClick(final ClickEvent<HorizontalLayout> event) {
        FtpStorageEntity selected = ftpStorageEntityDc.getItem();
        int port = Integer.parseInt(selected.getPort());
        try {
            ftpTestConnect.testConnection(selected.getHost(),
                    port, selected.getUsername(),
                    selected.getPassword(),
                    Boolean.TRUE.equals(selected.getPassiveMode())
            );

            notifications.create("Kết nối FTP thành công!")
                    .withType(Notifications.Type.SUCCESS)
                    .withCloseable(false)
                    .withDuration(2000)
                    .show();

        } catch (Exception e) {
            notifications.create("Kết nối thất bại: " + e.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .withCloseable(false)
                    .withDuration(2000)
                    .show();
        }

    }

}