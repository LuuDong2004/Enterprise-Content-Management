package com.vn.ecm.view.sourcestorage;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;

import com.vn.ecm.ecm.storage.DynamicStorageManager;
import com.vn.ecm.ecm.storage.s3.S3ClientFactory;
import com.vn.ecm.entity.SourceStorage;
import com.vn.ecm.entity.StorageType;
import com.vn.ecm.view.ecm.EcmView;
import com.vn.ecm.view.main.MainView;
import io.jmix.core.DataManager;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.UiComponents;

import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.model.CollectionLoader;
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
    @Autowired
    private DialogWindows dialogWindows;
    @ViewComponent
    private CollectionLoader<SourceStorage> sourceStoragesDl;
    @Autowired
    private S3ClientFactory s3ClientFactory;

    @Supply(to = "sourceStoragesDataGrid.actions", subject = "renderer")
    private Renderer<SourceStorage> sourceStoragesDataGridActionsRenderer() {
        return new ComponentRenderer<>(sourcestorage -> {
            SourceStorage reloadedStorage = dataManager.load(SourceStorage.class)
                    .id(sourcestorage.getId())
                    .one();
            Button viewStorageButton = uiComponents.create(Button.class);
           viewStorageButton.setIcon(new Icon(VaadinIcon.FOLDER_OPEN));
           // viewStorageButton.setIcon(new Icon(VaadinIcon.LOCK));
            viewStorageButton.setText("Truy cập");
            if(!Boolean.TRUE.equals(reloadedStorage.getActive())) {
                viewStorageButton.setEnabled(false);
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

    @Subscribe("sourceStoragesDataGrid.ftpCreateAction")
    public void onSourceStoragesDataGridFtpCreateAction(final ActionPerformedEvent event) {
        SourceStorage newSourceStorage = dataManager.create(SourceStorage.class);
            newSourceStorage.setType(StorageType.WEBDIR);
        DialogWindow<View<?>> window = dialogWindows.detail(this,SourceStorage.class )
                .newEntity(newSourceStorage)
                .build();

        window.addAfterCloseListener(afterCloseEvent -> {
            sourceStoragesDl.load();
        });
        window.setWidth("auto");
        window.setHeight("auto");
        window.open();
    }

    @Subscribe("sourceStoragesDataGrid.s3CreateAction")
    public void onSourceStoragesDataGridS3CreateAction(final ActionPerformedEvent event) {
        SourceStorage newSourceStorage = dataManager.create(SourceStorage.class);
        newSourceStorage.setType(StorageType.S3);
        DialogWindow<View<?>> window = dialogWindows.detail(this,SourceStorage.class )
                .newEntity(newSourceStorage)
                .build();
        window.addAfterCloseListener(afterCloseEvent -> {
            sourceStoragesDl.load();
        });
        window.setWidth("auto");
        window.setHeight("auto");
        window.open();
    }

}
