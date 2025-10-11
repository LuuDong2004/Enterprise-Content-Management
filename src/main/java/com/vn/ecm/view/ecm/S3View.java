package com.vn.ecm.view.ecm;

import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vn.ecm.entity.SourceStorage;
import com.vn.ecm.entity.StorageType;
import com.vn.ecm.view.main.MainView;
import io.jmix.core.DataManager;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "storage/s3", layout = MainView.class)
@ViewController("ecm_S3View")
@ViewDescriptor("ECM-view.xml")
//@PageTitle("S3")
public class S3View extends BaseEcmView {
    @Autowired
    private DataManager dataManager;
    @Override
    protected SourceStorage getCurrentStorage() {
        return dataManager.load(SourceStorage.class)
                .query("select s from SourceStorage s where s.type = :type and s.active = true")
                .parameter("type", StorageType.S3)
                .optional()
                .orElse(null);
    }
}
