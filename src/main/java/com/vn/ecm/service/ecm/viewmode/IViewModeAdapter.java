package com.vn.ecm.service.ecm.viewmode;

import io.jmix.flowui.component.grid.DataGrid;

public interface IViewModeAdapter<T> {
    void applyDefault(DataGrid<T> grid);
    void applyList(DataGrid<T> grid);
    void applyMediumIcons(DataGrid<T> grid);
}
