package com.vn.ecm.view.sourcestorage;

import com.vaadin.flow.router.Route;
import com.vn.ecm.entity.FtpStorageEntity;
import com.vn.ecm.entity.StorageType;
import com.vn.ecm.view.main.MainView;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.view.*;
import org.bson.BinaryVector;

@Route(value = "ftp-storage-entities/:id", layout = MainView.class)
@ViewController(id = "FtpStorageEntity.detail")
@ViewDescriptor(path = "ftp-storage-entity-detail-view.xml")
@EditedEntityContainer("ftpStorageEntityDc")
public class FtpStorageEntityDetailView extends StandardDetailView<FtpStorageEntity> {
//    @ViewComponent
//    private JmixSelect<StorageType> type;
//    @Subscribe
//    public void onInitEntity(InitEntityEvent<FtpStorageEntity> event) {
//        event.getEntity().setType(StorageType.FTP);
//    }
}