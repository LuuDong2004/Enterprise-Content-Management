package com.vn.ecm.view.sourcestorage;

import com.vaadin.flow.router.Route;
import com.vn.ecm.entity.SourceStorage;
import com.vn.ecm.view.main.MainView;
import io.jmix.flowui.view.*;


@Route(value = "source-storages", layout = MainView.class)
@ViewController(id = "SourceStorage.list")
@ViewDescriptor(path = "source-storage-list-view.xml")
@LookupComponent("sourceStoragesDataGrid")
@DialogMode(width = "64em")
public class SourceStorageListView extends StandardListView<SourceStorage> {
}