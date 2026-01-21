package com.vn.ecm.view.component.filepreview;


import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.Route;
import com.vn.ecm.ecm.storage.DynamicStorageManager;
import com.vn.ecm.view.main.MainView;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.view.*;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.InputStream;
import java.util.List;

@Route(value = "docx-preview", layout = MainView.class)
@ViewController(id = "DocxPreview")
@ViewDescriptor(path = "docx-preview.xml")
@DialogMode(width = "80%", height = "100%")
public class DocxPreview extends StandardView {
    @Autowired
    private DynamicStorageManager dynamicStorageManager;

    private FileRef inputFile;
    @Autowired
    private Notifications notifications;

    @ViewComponent
    private Div contentBox;

    public void setInputFile(FileRef inputFile) {
        this.inputFile = inputFile;
    }

    @Subscribe
    public void onReady(final ReadyEvent event) {
        if (inputFile == null) {
            notifications.create("Không tìm thấy file Word để xem trước")
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }

        String fileName = inputFile.getFileName();
        String ext = getExtension(fileName);

        if (!"docx".equals(ext)) {
            notifications.create("Hiện chỉ hỗ trợ xem trước định dạng .docx (Word mới)")
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }

        try {
            String storageName = inputFile.getStorageName();
            FileStorage storage = dynamicStorageManager.getFileStorageByName(storageName);

            try (InputStream is = storage.openStream(inputFile)) {
                String html = convertDocxToHtml(is);
                contentBox.getElement().setProperty("innerHTML", html);
            }
        } catch (Exception e) {
            notifications.create("Không đọc được file Word: " + e.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
        }

    }

    private String getExtension(String fileName) {
        if (fileName == null) return "";
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(idx + 1).toLowerCase();
    }
    private String convertDocxToHtml(InputStream is) throws Exception {
        XWPFDocument document = new XWPFDocument(is);

        StringBuilder html = new StringBuilder();

        // Chữ to hơn + đậm nhẹ + giãn dòng
        html.append("""
            <div class="docx-preview"
                 style="padding:24px;
                        font-family:Segoe UI,system-ui,Roboto,Arial,sans-serif;
                        font-size:16px;
                        font-weight:500;
                        line-height:1.6;">
        """);

        List<IBodyElement> bodyElements = document.getBodyElements();
        for (IBodyElement elem : bodyElements) {

            if (elem instanceof XWPFParagraph paragraph) {
                String pHtml = paragraphToHtml(paragraph);
                if (!pHtml.isBlank()) {
                    html.append("<p style=\"margin:6px 0;\">")
                            .append(pHtml)
                            .append("</p>");
                }
            } else if (elem instanceof XWPFTable table) {
                html.append(tableToHtml(table));
            }
        }

        html.append("</div>");
        return html.toString();
    }

    private String paragraphToHtml(XWPFParagraph paragraph) {
        StringBuilder sb = new StringBuilder();

        // dấu bullet đơn giản
        if (paragraph.getNumID() != null) {
            sb.append("<span style=\"margin-right:8px;\">•</span>");
        }

        for (XWPFRun run : paragraph.getRuns()) {
            String text = run.text();
            if (text == null || text.isEmpty()) {
                continue;
            }

            String escaped = escapeHtml(text);

            String open = "";
            String close = "";

            if (run.isBold()) {
                open += "<b>";
                close = "</b>" + close;
            }
            if (run.isItalic()) {
                open += "<i>";
                close = "</i>" + close;
            }
            if (run.getUnderline() != UnderlinePatterns.NONE) {
                open += "<u>";
                close = "</u>" + close;
            }

            sb.append(open).append(escaped).append(close);
        }

        return sb.toString();
    }

    private String tableToHtml(XWPFTable table) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
            <table style="border-collapse:collapse;
                          border:1px solid #ddd;
                          margin:12px 0;
                          width:100%;
                          font-size:15px;">
        """);
        for (XWPFTableRow row : table.getRows()) {
            sb.append("<tr>");
            for (XWPFTableCell cell : row.getTableCells()) {
                sb.append("<td style=\"border:1px solid #ddd;padding:6px 8px;\">");
                for (XWPFParagraph p : cell.getParagraphs()) {
                    sb.append("<div>").append(paragraphToHtml(p)).append("</div>");
                }
                sb.append("</td>");
            }
            sb.append("</tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }

    private String escapeHtml(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            switch (c) {
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '&' -> sb.append("&amp;");
                case '"' -> sb.append("&quot;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}