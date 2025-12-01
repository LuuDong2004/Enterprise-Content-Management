package com.vn.ecm.view.sourcestorage;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;


import com.vn.ecm.ecm.storage.DynamicStorageManager;
import com.vn.ecm.entity.FtpStorageEntity;
import com.vn.ecm.entity.SourceStorage;
import com.vn.ecm.entity.StorageType;
import com.vn.ecm.view.ecm.EcmView;
import com.vn.ecm.view.main.MainView;
import groovy.transform.Final;
import io.jmix.core.DataManager;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.UiComponents;

import io.jmix.flowui.action.list.EditAction;
import io.jmix.flowui.action.list.RemoveAction;
import io.jmix.flowui.component.grid.DataGrid;
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
    private DialogWindows dialogWindows;
    @ViewComponent
    private CollectionLoader<SourceStorage> sourceStoragesDl;
    @ViewComponent
    private DataGrid<SourceStorage> sourceStoragesDataGrid;

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
            });
            return viewStorageButton;
        });
    }

    @Subscribe("sourceStoragesDataGrid.localCreateAction")
    public void onSourceStoragesDataGridLocalCreateAction(final ActionPerformedEvent event) {
        SourceStorage newSourceStorage = dataManager.create(SourceStorage.class);
            newSourceStorage.setType(StorageType.WEBDIR);
        DialogWindow<View<?>> window = dialogWindows.detail(this,SourceStorage.class )
                .newEntity(newSourceStorage)
                .build();
        window.addAfterCloseListener(afterCloseEvent -> {
            sourceStoragesDl.load();
        });
        window.setWidth("auto");
        window.setHeight("50%");
        window.setResizable(true);
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
        window.setWidth("30%");
        window.setHeight("auto");
        window.setResizable(true);
        window.open();
    }

    @Subscribe("sourceStoragesDataGrid.ftpCreateAction")
    public void onSourceStoragesDataGridFtpCreateAction(final ActionPerformedEvent event) {
        FtpStorageEntity selected = dataManager.create(FtpStorageEntity.class);
        DialogWindow<View<?>> window = dialogWindows.detail(this,FtpStorageEntity.class )
                .newEntity(selected)
                .build();
        window.addAfterCloseListener(afterCloseEvent -> {
            sourceStoragesDl.load();
        });
        window.setWidth("40%");
        window.setHeight("auto");
        window.setResizable(true);
        window.open();
    }

    @Subscribe("sourceStoragesDataGrid.editAction")
    public void onSourceStoragesDataGridEditAction(final ActionPerformedEvent event) {
        SourceStorage selected = sourceStoragesDataGrid.getSingleSelectedItem();
        if(selected == null) {
            return;
        }
        DialogWindow<View<?>> window = dialogWindows.detail(this,SourceStorage.class )
                .newEntity(selected)
                .build();
        window.addAfterCloseListener(afterCloseEvent -> {
            sourceStoragesDl.load();
        });
        window.setWidth("40%");
        window.setHeight("auto");
        window.setResizable(true);
        window.open();
    }


    @Subscribe("sourceStoragesDataGrid.removeAction")
    public void onSourceStoragesDataGridRemoveAction(final ActionPerformedEvent event) {
        SourceStorage selected = sourceStoragesDataGrid.getSingleSelectedItem();
        ConfirmDialog dlg = new ConfirmDialog();
        dlg.setHeader("Xác nhận");
        dlg.setText("Xóa kho lưu trữ '" + selected.getName() + "' ?");
        dlg.setCancelable(true);
        dlg.setConfirmText("Xóa");
        dlg.addConfirmListener(e2 -> {
            dataManager.remove(selected);
            sourceStoragesDl.load();
        });
        dlg.open();
    }


    

}
