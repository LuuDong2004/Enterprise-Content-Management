package com.vn.ecm.view.sourcestorage;


import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import com.vn.ecm.entity.SourceStorage;
import com.vn.ecm.view.ecm.EcmView;
import com.vn.ecm.view.main.MainView;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.ViewNavigators;
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
    private ViewNavigators viewNavigators;

    @Supply(to = "sourceStoragesDataGrid.actions", subject = "renderer")
    private Renderer<SourceStorage> sourceStoragesDataGridActionsRenderer() {
        return new ComponentRenderer<>(sourcestorage -> {
            Button viewStorageButton = uiComponents.create(Button.class);

            viewStorageButton.setText("Open");
            viewStorageButton.addClickListener(e -> {
                RouteParameters params = new RouteParameters(
                        java.util.Map.of(
                                "type", sourcestorage.getType().name(),
                                "id",   sourcestorage.getId().toString()
                        )
                );
                UI.getCurrent().navigate(EcmView.class, params);
            });
            return viewStorageButton;
        });
    }




}
