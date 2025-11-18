package com.vn.ecm.view.component.filepreview;


import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import com.vn.ecm.ecm.storage.DynamicStorageManager;
import com.vn.ecm.view.main.MainView;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import io.jmix.core.FileStorageLocator;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.codeeditor.CodeEditor;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@ViewController(id = "HtmlPreview")
@ViewDescriptor(path = "html-preview.xml")
@DialogMode(width = "80%", height = "100%")
public class HtmlPreview extends StandardView {
    @ViewComponent
    private CodeEditor htmlPreview;
    @Autowired
    private DynamicStorageManager dynamicStorageManager;
    private FileRef inputFile;
    @Autowired
    private Notifications notifications;

    public void setInputFile(FileRef inputFile) {
        this.inputFile = inputFile;
    }

    @Subscribe
    public void onReady(ReadyEvent event) {
        if (inputFile == null) {
            return;
        }
        try {
            String storageName = inputFile.getStorageName();
            FileStorage storage = dynamicStorageManager.getFileStorageByName(storageName);

            try (InputStream is = storage.openStream(inputFile)) {
                String text = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                htmlPreview.setValue(text);
                htmlPreview.setReadOnly(true);
            }
        } catch (Exception e) {
            notifications.create("Không đọc được file: " + e.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
        }
    }
}