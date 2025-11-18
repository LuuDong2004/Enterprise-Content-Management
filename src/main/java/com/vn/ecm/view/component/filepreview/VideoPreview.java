package com.vn.ecm.view.component.filepreview;


import com.vaadin.flow.component.html.IFrame;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import com.vn.ecm.ecm.storage.DynamicStorageManager;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.InputStream;


@ViewController(id = "VideoPreview")
@ViewDescriptor(path = "video-preview.xml")
@DialogMode(width = "80%", height = "100%")
public class VideoPreview extends StandardView {
    @ViewComponent
    private IFrame videoPreView;

    @Autowired
    private DynamicStorageManager dynamicStorageManager;
    private FileRef inputFile;

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

        String contentType = inputFile.getContentType();


        DownloadHandler handler = DownloadHandler.fromInputStream(downloadEvent -> {
            InputStream is = storage.openStream(inputFile);
            return new DownloadResponse(
                    is,
                    inputFile.getFileName(),
                    contentType,
                    -1
            );
        }).inline();
        videoPreView.setSrc(handler);
    }

}