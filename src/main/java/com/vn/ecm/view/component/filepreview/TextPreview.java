package com.vn.ecm.view.component.filepreview;


import com.vn.ecm.ecm.storage.DynamicStorageManager;

import gr.netmechanics.jmix.tinymce.component.TinyMceEditor;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import io.jmix.core.FileStorageLocator;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.view.*;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
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
        if (inputFile == null) {
            return;
        }
        String storageName = inputFile.getStorageName();
        String fileName = inputFile.getFileName().toLowerCase();
        try {
            FileStorage storage = dynamicStorageManager.getFileStorageByName(storageName);

            try (InputStream is = storage.openStream(inputFile)) {
                String resultText;
                if (fileName.endsWith(".docx")) {
                    try (XWPFDocument docx = new XWPFDocument(is);
                         XWPFWordExtractor extractor = new XWPFWordExtractor(docx)) {
                        resultText = extractor.getText();
                    }
                } else if (fileName.endsWith(".doc")) {
                    try (HWPFDocument doc = new HWPFDocument(is);
                         WordExtractor extractor = new WordExtractor(doc)) {
                        resultText = extractor.getText();
                    }
                } else {
                    resultText = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
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
