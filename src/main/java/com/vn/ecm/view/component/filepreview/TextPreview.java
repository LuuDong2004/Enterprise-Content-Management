package com.vn.ecm.view.component.filepreview;

import com.vaadin.flow.router.Route;
import com.vn.ecm.ecm.storage.DynamicStorageManager;
import com.vn.ecm.view.main.MainView;
import gr.netmechanics.jmix.tinymce.component.TinyMceEditor;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import io.jmix.core.FileStorageLocator;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.formlayout.JmixFormLayout;
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
    private FileStorageLocator fileStorageLocator;
    @Autowired
    private DynamicStorageManager dynamicStorageManager;
    @Autowired
    private Notifications notifications;


    public void setInputFile(FileRef inputFile) {
        this.inputFile = inputFile;
    }

    @Subscribe
    public void onReady(ReadyEvent event) {
        if(inputFile == null){
            return;
        }
        String storageName = inputFile.getStorageName();
        try {
            FileStorage storage = dynamicStorageManager.getFileStorageByName(storageName);

            try (InputStream is = storage.openStream(inputFile)) {
                String text = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                textFormField.setValue(text);
                textFormField.setHeight("1000%");
                textFormField.setWidthFull();
            }
        } catch (Exception e) {
            notifications.create("Không đọc được file: " + e.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
        }
    }

    


}
