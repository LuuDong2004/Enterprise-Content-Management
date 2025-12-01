package com.vn.ecm.view.sourcestorage;

import com.vaadin.flow.router.Route;
import com.vn.ecm.entity.FtpStorageEntity;
import com.vn.ecm.entity.SourceStorage;
import com.vn.ecm.entity.StorageType;
import com.vn.ecm.view.main.MainView;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
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

    @ViewComponent
    private InstanceLoader<FtpStorageEntity> ftpStorageEntityDl;


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


}