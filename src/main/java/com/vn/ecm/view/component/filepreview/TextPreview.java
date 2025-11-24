package com.vn.ecm.view.component.filepreview;


import com.vn.ecm.ecm.storage.DynamicStorageManager;

import gr.netmechanics.jmix.tinymce.component.TinyMceEditor;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@ViewController(id = "TextPreview")
@ViewDescriptor(path = "text-preview.xml")
@DialogMode(width = "80%", height = "100%")
public class TextPreview extends StandardView {
    @ViewComponent
    private TinyMceEditor textFormField;
    private FileRef inputFile;

    @Autowired
    private DynamicStorageManager dynamicStorageManager;
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
        String storageName = inputFile.getStorageName();
        try {
            FileStorage storage = dynamicStorageManager.getFileStorageByName(storageName);
            try (InputStream is = storage.openStream(inputFile)) {
                String resultText = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                textFormField.setValue(resultText);
                textFormField.setWidthFull();
                textFormField.setHeight("1000%");
            }
        } catch (Exception e) {
            notifications.create("Không đọc được file: " + e.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
        }

    }
}
