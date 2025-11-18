package com.vn.ecm.view.component.filepreview;


import com.vaadin.flow.router.Route;
import com.vn.ecm.view.main.MainView;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "Image-preview", layout = MainView.class)
@ViewController(id = "ImagePreview")
@ViewDescriptor(path = "Image-preview.xml")
public class ImagePreview extends StandardView {
}