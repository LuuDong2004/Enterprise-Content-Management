package com.vn.ecm.view.component.filepreview;

import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import com.vn.ecm.ecm.storage.DynamicStorageManager;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import io.jmix.flowui.component.image.JmixImage;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.InputStream;


@ViewController(id = "ImagePreview")
@ViewDescriptor(path = "image-preview.xml")
@DialogMode(width = "80%", height = "100%")
public class ImagePreview extends StandardView {

    private FileRef inputFile;

    @ViewComponent
    private JmixImage<Object> imagePreView;

    @Autowired
    private DynamicStorageManager dynamicStorageManager;

    public void setInputFile(FileRef inputFile) {

        this.inputFile = inputFile;
    }
    @Subscribe
    public void onReady(ReadyEvent event) {

        if (inputFile == null) {
            return;
        }
        String storageName = inputFile.getStorageName();
        FileStorage storage = dynamicStorageManager.getFileStorageByName(storageName);
        String extention = inputFile.getContentType();

        DownloadHandler handler = DownloadHandler.fromInputStream(downloadEvent -> {
            InputStream is = storage.openStream(inputFile);
            return new DownloadResponse(
                    is,
                    inputFile.getFileName(),
                    extention,
                    -1
            );
        }).inline();
        imagePreView.setSrc(handler);
        imagePreView.getStyle().set("object-fit", "contain");
        imagePreView.getStyle().set("width", "100%");
        imagePreView.getStyle().set("background-color", "black");
    }

}