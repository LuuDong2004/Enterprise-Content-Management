package com.vn.ecm.view.component.filepreview;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vn.ecm.dto.ZipFileDto;
import com.vn.ecm.service.ecm.zipfile.ZipPreviewService;
import io.jmix.core.FileRef;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.download.DownloadFormat;
import io.jmix.flowui.download.Downloader;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.view.*;
import net.lingala.zip4j.exception.ZipException;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

@ViewController("zipPreview")
@ViewDescriptor("zip-preview.xml")
@DialogMode(width = "80%", height = "100%")
public class ZipPreview extends StandardView {

    private FileRef inputFile;

    @Autowired
    private ZipPreviewService zipPreviewService;

    @Autowired
    private Notifications notifications;

    @Autowired
    private Downloader downloader;

    @ViewComponent
    private TreeDataGrid<ZipFileDto> zipTreeGrid;
    @ViewComponent
    private CollectionContainer<ZipFileDto> zipTreeDc;

    private String currentPassword;

    public void setInputFile(FileRef fileRef) {
        this.inputFile = fileRef;
    }

    @Subscribe
    public void onReady(ReadyEvent event) {
        if (inputFile == null) {
            notifications.create("Không có file nén để xem trước")
                    .withType(Notifications.Type.ERROR)
                    .show();
            return;
        }

        try {
            buildTreeAndFill(null);

        } catch (ZipException ze) {
            openPasswordDialog();

        } catch (Exception e) {
            e.printStackTrace();
            notifications.create("Không kiểm tra được file nén: " + e.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
        }
    }
    private void buildTreeAndFill(String password) throws Exception {
        List<ZipFileDto> roots = zipPreviewService.buildZipTree(inputFile, password);
        this.currentPassword = password;

        List<ZipFileDto> allNodes = new ArrayList<>();
        for (ZipFileDto root : roots) {
            collectNodes(root, null, allNodes);
        }

        zipTreeDc.setItems(allNodes);

        if (!roots.isEmpty()) {
            zipTreeGrid.expand(roots.get(0));
        }
    }

    private void collectNodes(ZipFileDto node, ZipFileDto parent, List<ZipFileDto> acc) {
        node.setParent(parent);
        acc.add(node);
        if (node.getChildren() != null) {
            for (ZipFileDto child : node.getChildren()) {
                collectNodes(child, node, acc);
            }
        }
    }
    private void openPasswordDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Tệp nén có mật khẩu");

        PasswordField pf = new PasswordField("Mật khẩu");
        pf.setWidthFull();

        Button ok = new Button("Giải nén", e -> {
            dialog.close();
            try {
                buildTreeAndFill(pf.getValue());
            } catch (ZipException ze) {
                notifications.create("Mật khẩu không đúng, vui lòng nhập lại.")
                        .withType(Notifications.Type.ERROR)
                        .show();
                openPasswordDialog();   // mở lại dialog mới, giống code cũ
            } catch (Exception ex) {
                ex.printStackTrace();
                notifications.create("Lỗi giải nén: " + ex.getMessage())
                        .withType(Notifications.Type.ERROR)
                        .show();
            }
        });

        VerticalLayout layout = new VerticalLayout(pf, ok);
        layout.setPadding(false);
        layout.setSpacing(true);

        dialog.add(layout);
        dialog.open();
    }

    // ======================================
    // Download
    // ======================================
    @Subscribe("zipTreeGrid.downloadAction")
    public void onDownload(ActionPerformedEvent event) {
        ZipFileDto selected = zipTreeGrid.getSingleSelectedItem();
        if (selected == null || Boolean.TRUE.equals(selected.getFolder())) {
            notifications.create("Vui lòng chọn file (không phải thư mục) để tải xuống.")
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }

        try {
            byte[] bytes = zipPreviewService.loadEntryBytes(
                    inputFile,
                    selected.getKey(),
                    currentPassword
            );

            String fileName = selected.getName() != null ? selected.getName() : "file";
            downloader.download(bytes, fileName, DownloadFormat.OCTET_STREAM);

        } catch (ZipException ze) {
            notifications.create("Không tải được file (có thể mật khẩu sai hoặc thiếu).")
                    .withType(Notifications.Type.ERROR)
                    .show();
        } catch (Exception e) {
            e.printStackTrace();
            notifications.create("Không tải được file: " + e.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
        }
    }
}
