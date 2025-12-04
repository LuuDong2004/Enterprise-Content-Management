package com.vn.ecm.view.component.filepreview;


import com.vaadin.flow.router.Route;
import com.vn.ecm.view.main.MainView;
import io.jmix.core.FileRef;
import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "zip-preview", layout = MainView.class)
@ViewController(id = "ZipPreview")
@ViewDescriptor(path = "zip-preview.xml")
@DialogMode(width = "80%", height = "100%")
public class ZipPreview extends StandardView {
    private FileRef inputFile;

    public void setInputFile(FileRef fileRef) {
        this.inputFile = fileRef;
    }
}