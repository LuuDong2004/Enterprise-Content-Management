package com.vn.ecm.view.component.filepreview;


import com.vaadin.flow.router.Route;
import com.vn.ecm.dto.ZipFileDto;
import com.vn.ecm.service.ecm.zipfile.ZipPreviewService;
import com.vn.ecm.view.main.MainView;
import io.jmix.core.FileRef;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.download.DownloadFormat;
import io.jmix.flowui.download.Downloader;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

@Route(value = "zip-preview", layout = MainView.class)
@ViewController(id = "ZipPreview")
@ViewDescriptor(path = "zip-preview.xml")
@DialogMode(width = "80%", height = "100%")
public class ZipPreview extends StandardView {
    @ViewComponent
    private TreeDataGrid<ZipFileDto> zipTreeGrid;

    @ViewComponent
    private CollectionContainer<ZipFileDto> zipTreeDc;

    @Autowired
    private ZipPreviewService zipPreviewService;

    @Autowired
    private Notifications notifications;

    @Autowired
    private Downloader downloader;

    // File ZIP được truyền từ ECM view
    private FileRef fileRef;

    // Nếu ZIP cần mật khẩu, ECM view có thể set vào đây (tuỳ bạn dùng hay không)
    private String password;

    public void setInputFile(FileRef fileRef) {
        this.fileRef = fileRef;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        if (fileRef == null) {
            notifications.create("Không tìm thấy file ZIP để xem trước.")
                    .withType(Notifications.Type.ERROR)
                    .show();
            close(StandardOutcome.CLOSE);
            return;
        }

        try {
            // Lấy danh sách root từ service (có children)
            List<ZipFileDto> roots = zipPreviewService.buildZipTree(fileRef, password);

            // Flatten về list để bỏ vào CollectionContainer, đồng thời set parent
            List<ZipFileDto> flatList = new ArrayList<>();
            for (ZipFileDto root : roots) {
                addNodeRecursive(flatList, null, root);
            }

            zipTreeDc.setItems(flatList);

        } catch (IllegalArgumentException ex) {
            // tuỳ quy ước: PASSWORD_REQUIRED, WRONG_PASSWORD...
            notifications.create("Lỗi khi đọc ZIP: " + ex.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
            close(StandardOutcome.CLOSE);
        } catch (Exception ex) {
            ex.printStackTrace();
            notifications.create("Lỗi khi đọc file ZIP: " + ex.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
            close(StandardOutcome.CLOSE);
        }
    }

    private void addNodeRecursive(List<ZipFileDto> flatList, ZipFileDto parent, ZipFileDto current) {
        current.setParent(parent);
        flatList.add(current);

        if (current.getChildren() != null) {
            for (ZipFileDto child : current.getChildren()) {
                addNodeRecursive(flatList, current, child);
            }
        }
    }

    @Subscribe("zipTreeGrid.downloadAction")
    public void onZipTreeGridDownloadAction(ActionPerformedEvent event) {
        ZipFileDto selected = zipTreeGrid.getSingleSelectedItem();
        if (selected == null) {
            notifications.create("Vui lòng chọn một tệp để tải xuống.")
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }

        if (selected.getFolder() == null) {
            notifications.create("Không thể tải xuống thư mục, hãy chọn tệp.")
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }

        try {
            // Lấy bytes thực từ file ZIP
            byte[] bytes = zipPreviewService.loadEntryBytes(
                    fileRef,
                    selected.getKey(),
                    password
            );

            String fileName = selected.getName() != null ? selected.getName() : "file";

            // CHUẨN JMIX 2.7
            downloader.download(
                    bytes,
                    fileName,
                    DownloadFormat.OCTET_STREAM
            );

        } catch (IllegalArgumentException ex) {
            notifications.create("Lỗi: " + ex.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();

        } catch (Exception ex) {
            ex.printStackTrace();
            notifications.create("Không tải được file: " + ex.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
        }
    }

}