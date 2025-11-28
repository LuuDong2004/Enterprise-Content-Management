package com.vn.ecm.view.sharecontent;


import com.vaadin.flow.router.Route;
import com.vn.ecm.view.main.MainView;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "share-content-view", layout = MainView.class)
@ViewController(id = "ShareContentView")
@ViewDescriptor(path = "share-content-view.xml")
public class ShareContentView extends StandardView {
}