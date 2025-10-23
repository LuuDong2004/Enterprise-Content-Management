package com.vn.ecm.view.sourcestorage;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;

import com.vn.ecm.ecm.storage.DynamicStorageManager;
import com.vn.ecm.entity.SourceStorage;
import com.vn.ecm.view.ecm.EcmView;
import com.vn.ecm.view.main.MainView;
import io.jmix.core.DataManager;
import io.jmix.flowui.UiComponents;

import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import com.vaadin.flow.component.UI;



@Route(value = "source-storages", layout = MainView.class)
@ViewController(id = "SourceStorage.list")
@ViewDescriptor(path = "source-storage-list-view.xml")
@LookupComponent("sourceStoragesDataGrid")
@DialogMode(width = "64em")
public class SourceStorageListView extends StandardListView<SourceStorage> {
    @Autowired
    private UiComponents uiComponents;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private DynamicStorageManager dynamicStorageManager;


    @Supply(to = "sourceStoragesDataGrid.actions", subject = "renderer")
    private Renderer<SourceStorage> sourceStoragesDataGridActionsRenderer() {
        return new ComponentRenderer<>(sourcestorage -> {
            SourceStorage reloadedStorage = dataManager.load(SourceStorage.class)
                    .id(sourcestorage.getId())
                    .one();
            Button viewStorageButton = uiComponents.create(Button.class);
            viewStorageButton.setText("Open");
            if(reloadedStorage.getActive().equals(false)) {
                viewStorageButton.setEnabled(false);
                return null;
            }
            viewStorageButton.addClickListener(e -> {
                UI.getCurrent().navigate(EcmView.class,
                        new RouteParameters("id",reloadedStorage.getId().toString()));
                // cập nhập lại kho vào bean
                dynamicStorageManager.refreshFileStorage(reloadedStorage);
            });
            return viewStorageButton;
        });
    }




}
