package com.vn.ecm.view.sharepermissionrequestlist;


import com.vaadin.flow.router.Route;
import com.vn.ecm.view.main.MainView;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.Route;
import com.vn.ecm.entity.SharePermissionRequest;
import com.vn.ecm.entity.User;
import com.vn.ecm.service.ecm.ShareService;
import com.vn.ecm.view.main.MainView;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;

@Route(value = "share-permission-request-list-view", layout = MainView.class)
@ViewController(id = "SharePermissionRequestListView")
@ViewDescriptor(path = "share-permission-request-list-view.xml")
public class SharePermissionRequestListView extends StandardView {

    @ViewComponent
    private DataGrid<SharePermissionRequest> requestsDataGrid;

    @ViewComponent
    private CollectionLoader<SharePermissionRequest> requestsDl;

    @Autowired
    private ShareService shareService;

    @Autowired
    private CurrentAuthentication currentAuthentication;

    @Autowired
    private Notifications notifications;

    @Subscribe
    public void onInit(InitEvent event) {
        setupColumns();
    }

    private void setupColumns() {
        // Thêm cột Quyền yêu cầu
        requestsDataGrid.addColumn(new ComponentRenderer<>(request -> {
                    String permissionType = request.getRequestedPermission();
                    String text;
                    String theme;

                    if ("READ".equals(permissionType)) {
                        text = "Chỉ xem";
                        theme = "badge";
                    } else if ("MODIFY".equals(permissionType)) {
                        text = "Chỉnh sửa";
                        theme = "badge primary";
                    } else if ("FULL".equals(permissionType)) {
                        text = "Toàn quyền";
                        theme = "badge success";
                    } else {
                        text = permissionType != null ? permissionType : "";
                        theme = "badge";
                    }

                    Span badge = new Span(text);
                    badge.getElement().getThemeList().addAll(List.of(theme.split(" ")));
                    return badge;
                }))
                .setHeader("Quyền yêu cầu")
                .setAutoWidth(true)
                .setFlexGrow(0);

        // Thêm cột Mục được chia sẻ
        requestsDataGrid.addColumn(new ComponentRenderer<>(request -> {
                    if (request.getShareLink() == null) {
                        return new Span("-");
                    }

                    String itemName;
                    if (request.getShareLink().getFolder() != null) {
                        itemName = "📁 " + request.getShareLink().getFolder().getName();
                    } else if (request.getShareLink().getFile() != null) {
                        itemName = "📄 " + request.getShareLink().getFile().getName();
                    } else {
                        itemName = "-";
                    }

                    return new Span(itemName);
                }))
                .setHeader("Mục được chia sẻ")
                .setAutoWidth(true)
                .setFlexGrow(1);
    }

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        User currentUser = (User) currentAuthentication.getUser();
        requestsDl.setParameter("current_user_id", currentUser.getId());
        requestsDl.load();
    }

    @Subscribe("approveBtn")
    public void onApproveBtnClick(ClickEvent<JmixButton> event) {
        Set<SharePermissionRequest> selected = requestsDataGrid.getSelectedItems();
        if (selected.isEmpty()) {
            notifications.create("Vui lòng chọn yêu cầu cần phê duyệt")
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }

        try {
            for (SharePermissionRequest request : selected) {
                shareService.processPermissionRequest(request, true);
            }

            notifications.create("Đã phê duyệt " + selected.size() + " yêu cầu")
                    .withType(Notifications.Type.SUCCESS)
                    .show();

            requestsDl.load();
        } catch (Exception e) {
            notifications.create("Lỗi: " + e.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
        }
    }

    @Subscribe("rejectBtn")
    public void onRejectBtnClick(ClickEvent<JmixButton> event) {
        Set<SharePermissionRequest> selected = requestsDataGrid.getSelectedItems();
        if (selected.isEmpty()) {
            notifications.create("Vui lòng chọn yêu cầu cần từ chối")
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }

        try {
            for (SharePermissionRequest request : selected) {
                shareService.processPermissionRequest(request, false);
            }

            notifications.create("Đã từ chối " + selected.size() + " yêu cầu")
                    .withType(Notifications.Type.SUCCESS)
                    .show();

            requestsDl.load();
        } catch (Exception e) {
            notifications.create("Lỗi: " + e.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
        }
    }

    @Subscribe("refreshBtn")
    public void onRefreshBtnClick(ClickEvent<JmixButton> event) {
        requestsDl.load();
        notifications.create("Đã làm mới danh sách")
                .withType(Notifications.Type.SUCCESS)
                .show();
    }

}