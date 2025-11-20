package com.vn.ecm.view.component.filepreview;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.html.Div;
import io.jmix.core.FileRef;
import io.jmix.flowui.view.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@DialogMode(width = "90%", height = "90%")
@ViewController("ExcelPreview")
@ViewDescriptor("excel-preview.xml")
public class ExcelPreview extends StandardView {

    @ViewComponent("univerContainer")
    private Div univerContainer;

    private FileRef inputFile;

    public void setInputFile(FileRef inputFile) {
        this.inputFile = inputFile;
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
    }

    @Subscribe
    public void onReady(ReadyEvent event) {
        if (inputFile == null) {
            return;
        }
        univerContainer.setWidthFull();
        univerContainer.setHeightFull();
        String excelUrl = buildExcelUrl(inputFile);
        univerContainer.getElement().executeJs(
                """
                const container = this;
                const excelUrl = $0;

                (async function (container, excelUrl) {
                    console.log("[ExcelPreview JS] start", excelUrl);

                    // Chuẩn bị div mount Univer
                    container.innerHTML = "";
                    const app = document.createElement("div");
                    app.id = "univer-app";
                    app.style.width = "100%";
                    app.style.height = "100%";
                    container.appendChild(app);

                    function loadCss(href) {
                        if (!document.querySelector("link[href='" + href + "']")) {
                            const l = document.createElement("link");
                            l.rel = "stylesheet";
                            l.href = href;
                            document.head.appendChild(l);
                        }
                    }

                    function loadScript(src) {
                        return new Promise((resolve, reject) => {
                            const exists = document.querySelector("script[src='" + src + "']");
                            if (exists) {
                                return resolve();
                            }
                            const s = document.createElement("script");
                            s.src = src;
                            s.onload = () => resolve();
                            s.onerror = () => reject(new Error("Cannot load " + src));
                            document.head.appendChild(s);
                        });
                    }

                    try {
                        // ==============================
                        // 1) LOAD LIBS CHỈ MỘT LẦN
                        // ==============================
                        if (!window._univerPreviewLibs) {
                            window._univerPreviewLibs = {
                                ready: false,
                                promise: (async () => {
                                    // CSS & core libs
                                    loadCss("https://unpkg.com/@univerjs/preset-sheets-core/lib/index.css");

                                    await loadScript("https://unpkg.com/react@18.3.1/umd/react.production.min.js");
                                    await loadScript("https://unpkg.com/react-dom@18.3.1/umd/react-dom.production.min.js");
                                    await loadScript("https://unpkg.com/rxjs/dist/bundles/rxjs.umd.min.js");
                                    await loadScript("https://unpkg.com/echarts@5.6.0/dist/echarts.min.js");

                                    await loadScript("https://unpkg.com/@univerjs/presets/lib/umd/index.js");
                                    await loadScript("https://unpkg.com/@univerjs/preset-sheets-core/lib/umd/index.js");
                                    await loadScript("https://unpkg.com/@univerjs/preset-sheets-core/lib/umd/locales/en-US.js");

                                    // SheetJS
                                    await loadScript("https://cdn.sheetjs.com/xlsx-0.20.0/package/dist/xlsx.full.min.js");

                                    const { createUniver } = window.UniverPresets || {};
                                    const { LocaleType, mergeLocales } = window.UniverCore || {};
                                    const { UniverSheetsCorePreset } = window.UniverPresetSheetsCore || {};
                                    const presetLocaleEnUS = window.UniverPresetSheetsCoreEnUS;

                                    if (!createUniver || !LocaleType || !mergeLocales ||
                                            !UniverSheetsCorePreset || !presetLocaleEnUS) {
                                        console.error("[ExcelPreview JS] Globals =", {
                                            UniverPresets: window.UniverPresets,
                                            UniverCore: window.UniverCore,
                                            UniverPresetSheetsCore: window.UniverPresetSheetsCore,
                                            UniverPresetSheetsCoreEnUS: window.UniverPresetSheetsCoreEnUS
                                        });
                                        throw new Error("Univer CDN globals not loaded correctly");
                                    }

                                    window._univerPreviewLibs.ready = true;
                                    window._univerPreviewLibs.api = {
                                        createUniver,
                                        LocaleType,
                                        mergeLocales,
                                        UniverSheetsCorePreset,
                                        presetLocaleEnUS
                                    };
                                })()
                            };
                        }

                        // Đợi libs load xong (lần sau sẽ vào nhanh)
                        await window._univerPreviewLibs.promise;
                        const { createUniver, LocaleType, mergeLocales,
                                UniverSheetsCorePreset, presetLocaleEnUS } = window._univerPreviewLibs.api;

                        // ==============================
                        // 2) TẠO UNIVER CHO LẦN XEM NÀY
                        // ==============================
                        const { univerAPI } = createUniver({
                            locale: LocaleType.EN_US,
                            locales: {
                                [LocaleType.EN_US]: mergeLocales(presetLocaleEnUS),
                            },
                            presets: [
                                UniverSheetsCorePreset({
                                    container: "univer-app",
                                }),
                            ],
                        });

                        console.log("[ExcelPreview JS] Univer initialized", univerAPI);

                        // Tạo workbook & sheet trống để hiển thị
                        univerAPI.createWorkbook({ name: "Preview" });

                        // ==============================
                        // 3) LOAD & ĐỔ DỮ LIỆU EXCEL
                        // ==============================
                        const resp = await fetch(excelUrl);
                        if (!resp.ok) {
                            console.error("[ExcelPreview JS] fetch excel failed", resp.status);
                            return;
                        }
                        const buf = await resp.arrayBuffer();
                        console.log("[ExcelPreview JS] Excel size", buf.byteLength);

                        const wb = XLSX.read(buf, { type: "array" });
                        console.log("[ExcelPreview JS] workbook sheets", wb.SheetNames);

                        if (!wb.SheetNames || wb.SheetNames.length === 0) {
                            console.warn("[ExcelPreview JS] workbook has no sheets");
                            return;
                        }

                        const firstSheetName = wb.SheetNames[0];
                        const ws = wb.Sheets[firstSheetName];

                        if (!ws) {
                            console.warn("[ExcelPreview JS] first sheet not found in workbook");
                            return;
                        }

                        const range = XLSX.utils.decode_range(ws["!ref"] || "A1");
                        const values = [];

                        for (let r = range.s.r; r <= range.e.r; r++) {
                            const row = [];
                            for (let c = range.s.c; c <= range.e.c; c++) {
                                const addr = XLSX.utils.encode_cell({ r, c });
                                const cell = ws[addr];
                                row.push(cell ? cell.v : null);
                            }
                            values.push(row);
                        }

                        console.log("[ExcelPreview JS] parsed rows", values.length);

                        const fWorkbook = univerAPI.getActiveWorkbook();
                        if (!fWorkbook) {
                            console.error("[ExcelPreview JS] Active workbook not found");
                            return;
                        }
                        const fSheet = fWorkbook.getActiveSheet();
                        if (!fSheet) {
                            console.error("[ExcelPreview JS] Active sheet not found");
                            return;
                        }

                        if (values.length === 0) {
                            console.warn("[ExcelPreview JS] no data in Excel sheet");
                            return;
                        }

                        const rowCount = values.length;
                        const colCount = values[0].length;

                        const fRange = fSheet.getRange(0, 0, rowCount, colCount);
                        fRange.setValues(values);

                        console.log("[ExcelPreview JS] Data filled into sheet");

                    } catch (e) {
                        console.error("[ExcelPreview JS] ERROR", e);
                        container.innerHTML =
                                "<div style='padding:10px;color:red'>Lỗi preview Excel: "
                                + (e && e.message ? e.message : e)
                                + "</div>";
                    }
                })(container, excelUrl);
                """,
                excelUrl
        );
    }

    private String buildExcelUrl(FileRef ref) {
        return "/api/excel-preview/file"
                + "?storageName=" + urlEncode(ref.getStorageName())
                + "&path=" + urlEncode(ref.getPath())
                + "&fileName=" + urlEncode(ref.getFileName());
    }

    private String urlEncode(String v) {
        if (v == null) return "";
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }
}
