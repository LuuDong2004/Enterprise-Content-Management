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

@Route(value = "storage/webdir", layout = MainView.class)
@ViewController( id = "ecm_WebDirView")
@ViewDescriptor("ECM-view.xml")
//@PageTitle("Web Directory")
public class WebDirectoryView extends BaseEcmView {
    @Autowired
    private DataManager dataManager;

    @Override
    protected SourceStorage getCurrentStorage() {
        return dataManager.load(SourceStorage.class)
                .query("select s from SourceStorage s where s.type = :type and s.active = true")
                .parameter("type", StorageType.WEBDIR)
                .optional()
                .orElse(null);
    }
}

