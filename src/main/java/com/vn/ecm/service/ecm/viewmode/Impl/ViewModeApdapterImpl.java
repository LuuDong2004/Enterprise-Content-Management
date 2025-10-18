package com.vn.ecm.service.ecm.viewmode.Impl;

import com.vn.ecm.service.ecm.viewmode.IViewModeAdapter;
import io.jmix.flowui.component.grid.DataGrid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vn.ecm.entity.FileDescriptor;
import com.vn.ecm.entity.Folder;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@SuppressWarnings("unchecked")
public class ViewModeApdapterImpl<T> implements IViewModeAdapter<T> {
    public enum Kind { FILE, FOLDER }

    private final Kind kind;
    private final NumberFormat nf = NumberFormat.getIntegerInstance(new Locale("vi", "VN"));
    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public ViewModeApdapterImpl(Kind kind) {
        this.kind = kind;
    }
    @Override
    public void applyDefault(DataGrid<T> grid) {
        grid.getColumns().forEach(grid::removeColumn);

        if (kind == Kind.FILE) {
            // Name (icon + text)
            grid.addComponentColumn(item -> {
                FileDescriptor fd = (FileDescriptor) item;
                Icon icon = fileIcon(fd).create();
                icon.setSize("16px");
                icon.addClassName("file-icon");
                icon.addClassName(fileTypeClass(fd));
                Span name = new Span(fd.getName());
                HorizontalLayout row = new HorizontalLayout(icon, name);
                row.setAlignItems(FlexComponent.Alignment.CENTER);
                return row;
            }).setHeader("Tên").setAutoWidth(true);

            grid.addColumn(item -> {
                FileDescriptor fd = (FileDescriptor) item;
                return fd.getLastModified() != null ? dtf.format(fd.getLastModified()) : "";
            }).setHeader("Ngày tạo").setAutoWidth(true);

            grid.addColumn(item -> {
                FileDescriptor fd = (FileDescriptor) item;
                return fd.getExtension();
            }).setHeader("Kiểu").setAutoWidth(true);

            grid.addColumn(item -> {
                FileDescriptor fd = (FileDescriptor) item;
                return fd.getSize() != null ? nf.format(fd.getSize()) : "";
            }).setHeader("Kích thước").setAutoWidth(true);

        } else { // FOLDER
            grid.addComponentColumn(item -> {
                Folder f = (Folder) item;
                Icon icon = VaadinIcon.FOLDER.create();
                icon.setSize("16px");
                Span name = new Span(f.getName());
                HorizontalLayout row = new HorizontalLayout(icon, name);
                row.setAlignItems(FlexComponent.Alignment.CENTER);
                return row;
            }).setHeader("Folder").setAutoWidth(true);

            grid.addColumn(item -> {
                Folder f = (Folder) item;
                return f.getCreatedDate() != null ? dtf.format(f.getCreatedDate()) : "";
            }).setHeader("Created").setAutoWidth(true);
        }
    }
    @Override
    public void applyList(DataGrid<T> grid) {
        grid.getColumns().forEach(grid::removeColumn);

        if (kind == Kind.FILE) {
            grid.addComponentColumn(item -> {
                FileDescriptor fd = (FileDescriptor) item;
                Icon icon = fileIcon(fd).create();
                icon.setSize("16px");
                icon.addClassName("file-icon");
                icon.addClassName(fileTypeClass(fd));
                Span name = new Span(fd.getName());
                HorizontalLayout row = new HorizontalLayout(icon, name);
                row.setAlignItems(FlexComponent.Alignment.CENTER);
                row.addClassName("file-list-cell");
                return row;
            }).setHeader("Tên").setAutoWidth(true).setResizable(true).setKey("name");
        } else { // FOLDER
            grid.addComponentColumn(item -> {
                Folder f = (Folder) item;
                Icon icon = VaadinIcon.FOLDER.create();
                icon.setSize("16px");
                Span name = new Span(f.getName());
                HorizontalLayout row = new HorizontalLayout(icon, name);
                row.setAlignItems(FlexComponent.Alignment.CENTER);
                row.addClassName("file-list-cell");
                return row;
            }).setHeader("Folder").setAutoWidth(true).setResizable(true).setKey("name");
        }
    }
    @Override
    public void applyMediumIcons(DataGrid<T> grid) {
        grid.getColumns().forEach(grid::removeColumn);
        grid.getElement().getThemeList().add("no-border");
        grid.getElement().getThemeList().remove("compact");
        grid.setAllRowsVisible(true);
        if (kind == Kind.FILE) {
            grid.addComponentColumn(item -> {
                FileDescriptor fd = (FileDescriptor) item;
                Icon icon = fileIcon(fd).create();
                icon.setSize("48px");
                icon.addClassName("file-icon");
                icon.addClassName(fileTypeClass(fd));
                Span name = new Span(fd.getName());
                name.addClassName("file-name");
                VerticalLayout tile = new VerticalLayout(icon, name);
                tile.setPadding(false);
                tile.setSpacing(false);
                tile.setAlignItems(FlexComponent.Alignment.CENTER);
                tile.addClassName("file-tile");
                return tile;
            }).setHeader("") // hide header like Windows icons view
              .setAutoWidth(true)
              .setResizable(false)
              .setKey("tile");
        } else { // FOLDER
            grid.addComponentColumn(item -> {
                Folder f = (Folder) item;
                Icon icon = VaadinIcon.FOLDER.create();
                icon.setSize("48px");
                icon.addClassName("file-icon");
                icon.addClassName("folder");
                Span name = new Span(f.getName());
                name.addClassName("file-name");
                VerticalLayout tile = new VerticalLayout(icon, name);
                tile.setPadding(false);
                tile.setSpacing(false);
                tile.setAlignItems(FlexComponent.Alignment.CENTER);
                tile.addClassName("file-tile");
                return tile;
            }).setHeader("")
              .setAutoWidth(true)
              .setResizable(false)
              .setKey("tile");
        }
    }


    private String fileTypeClass(FileDescriptor fd) {
        String ext = fd.getExtension() == null ? "" : fd.getExtension().toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "png", "jpg", "jpeg", "gif", "bmp", "svg" -> "ext-image";
            case "pdf" -> "ext-pdf";
            case "xls", "xlsx" -> "ext-excel";
            case "doc", "docx" -> "ext-word";
            case "ppt", "pptx" -> "ext-powerpoint";
            case "zip", "rar", "7z" -> "ext-archive";
            case "mp3", "wav", "flac" -> "ext-audio";
            case "mp4", "avi", "mkv", "mov" -> "ext-video";
            case "txt" -> "ext-text";
            default -> "ext-file";
        };
    }
    private VaadinIcon fileIcon(FileDescriptor fd) {
        String ext = fd.getExtension() == null ? "" : fd.getExtension().toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "png", "jpg", "jpeg", "gif", "bmp", "svg" -> VaadinIcon.PICTURE;
            case "pdf" -> VaadinIcon.FILE_TEXT;
            case "xls", "xlsx" -> VaadinIcon.FILE_TABLE;
            case "doc", "docx" -> VaadinIcon.FILE_TEXT_O;
            case "ppt", "pptx" -> VaadinIcon.FILE_PRESENTATION;
            case "zip", "rar", "7z" -> VaadinIcon.ARCHIVE;
            case "mp3", "wav", "flac" -> VaadinIcon.MUSIC;
            case "mp4", "avi", "mkv", "mov" -> VaadinIcon.FILM;
            default -> VaadinIcon.FILE_O;
        };
    }
}
