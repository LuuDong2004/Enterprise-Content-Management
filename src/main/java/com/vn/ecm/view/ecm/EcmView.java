package com.vn.ecm.view.ecm;


import com.vaadin.flow.router.Route;
import com.vn.ecm.entity.File;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.view.main.MainView;
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;

@Route(value = "ECM-view", layout = MainView.class)
@ViewController(id = "EcmView")
@ViewDescriptor(path = "ECM-view.xml")
public class EcmView extends StandardView {
    @ViewComponent
    private CollectionContainer<Folder> foldersDc;
    @ViewComponent
    private CollectionContainer<File> filesDc;
    @ViewComponent
    private TreeDataGrid<Folder> foldersTree;
    @ViewComponent
    private CollectionLoader<Folder> foldersDl;


    @Subscribe
    protected void onInit(InitEvent event) {

    }
    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
       foldersDl.load();
       // mở từng cấp
       foldersTree.expandRecursively(foldersDc.getItems(),1);
    }
}