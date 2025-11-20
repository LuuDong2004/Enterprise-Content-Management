// Univer Sheets initialization for Excel preview
// Using CDN approach for easier integration with Jmix
window.initUniverSheets = function(containerId, excelDataUrl) {
    const container = document.getElementById(containerId);
    if (!container) {
        console.error('Container element not found:', containerId);
        return;
    }

    // Clear container
    container.innerHTML = '';

    // Create Univer container div
    const univerContainer = document.createElement('div');
    univerContainer.id = 'univer-container-' + containerId;
    univerContainer.style.width = '100%';
    univerContainer.style.height = '100%';
    univerContainer.style.position = 'relative';
    container.appendChild(univerContainer);

    // Load Univer Sheets from CDN if not already loaded
    if (typeof window.Univer === 'undefined' || typeof window.UniverSheets === 'undefined') {
        loadUniverFromCDN(univerContainer, excelDataUrl);
    } else {
        initializeUniverSheets(univerContainer, excelDataUrl);
    }
};

function loadUniverFromCDN(container, excelDataUrl) {
    // Load Univer core
    const coreScript = document.createElement('script');
    coreScript.src = 'https://unpkg.com/@univerjs/core@latest/dist/index.umd.js';
    
    coreScript.onload = () => {
        // Load Univer Sheets
        const sheetsScript = document.createElement('script');
        sheetsScript.src = 'https://unpkg.com/@univerjs/sheets@latest/dist/index.umd.js';
        
        sheetsScript.onload = () => {
            // Load Univer Sheets UI
            const sheetsUIScript = document.createElement('script');
            sheetsUIScript.src = 'https://unpkg.com/@univerjs/sheets-ui@latest/dist/index.umd.js';
            
            sheetsUIScript.onload = () => {
                // Load Univer Sheets Import XLSX
                const importScript = document.createElement('script');
                importScript.src = 'https://unpkg.com/@univerjs/sheets-import-xlsx@latest/dist/index.umd.js';
                
                importScript.onload = () => {
                    initializeUniverSheets(container, excelDataUrl);
                };
                
                importScript.onerror = () => {
                    console.warn('Failed to load Univer Sheets Import XLSX, trying alternative approach');
                    initializeUniverSheets(container, excelDataUrl);
                };
                
                document.head.appendChild(importScript);
            };
            
            sheetsUIScript.onerror = () => {
                console.error('Failed to load Univer Sheets UI');
            };
            
            document.head.appendChild(sheetsUIScript);
        };
        
        sheetsScript.onerror = () => {
            console.error('Failed to load Univer Sheets');
        };
        
        document.head.appendChild(sheetsScript);
    };
    
    coreScript.onerror = () => {
        console.error('Failed to load Univer Core');
    };
    
    document.head.appendChild(coreScript);
}

function initializeUniverSheets(container, excelDataUrl) {
    try {
        // Check if Univer is available
        if (typeof window.Univer === 'undefined') {
            console.error('Univer is not available');
            return;
        }

        // Create Univer instance
        const univer = window.Univer.newInstance({
            theme: 'default',
            locale: 'en',
        });

        // Register Sheets plugin
        if (window.UniverSheets && window.UniverSheets.UniverSheetsPlugin) {
            univer.registerPlugin(window.UniverSheets.UniverSheetsPlugin);
        }

        // Register Sheets UI plugin
        if (window.UniverSheetsUI && window.UniverSheetsUI.UniverSheetsUIPlugin) {
            univer.registerPlugin(window.UniverSheetsUI.UniverSheetsUIPlugin);
        }

        // Create sheet unit
        const sheetData = {
            id: 'sheet-1',
            name: 'Sheet1',
            cellData: {},
            rowCount: 1000,
            columnCount: 100,
        };

        univer.createUnit(window.Univer.UnitType.UNIVER_SHEET, sheetData);

        // Get the render engine and mount to container
        const renderEngine = univer.getPlugin('RenderEngine');
        if (renderEngine) {
            renderEngine.mount(container);
        }

        // Load Excel file if URL provided
        if (excelDataUrl) {
            loadExcelFile(univer, excelDataUrl);
        }

        // Store univer instance for cleanup
        container._univerInstance = univer;

    } catch (error) {
        console.error('Error initializing Univer Sheets:', error);
        // Fallback: show error message
        container.innerHTML = '<div style="padding: 20px; text-align: center; color: red;">Error loading Excel preview: ' + error.message + '</div>';
    }
}

function loadExcelFile(univer, excelDataUrl) {
    fetch(excelDataUrl)
        .then(response => {
            if (!response.ok) {
                throw new Error('Failed to load Excel file: ' + response.statusText);
            }
            return response.arrayBuffer();
        })
        .then(data => {
            // Try to import using Univer's import functionality
            if (window.UniverSheetsImportXlsx && window.UniverSheetsImportXlsx.importXlsx) {
                window.UniverSheetsImportXlsx.importXlsx(univer, data);
            } else {
                // Alternative: parse Excel manually or use SheetJS
                console.warn('Univer Sheets Import XLSX plugin not available, trying alternative');
                tryImportWithSheetJS(univer, data);
            }
        })
        .catch(error => {
            console.error('Error loading Excel file:', error);
            const container = document.getElementById('univer-container-' + excelDataUrl.split('/').pop());
            if (container) {
                container.innerHTML = '<div style="padding: 20px; text-align: center; color: red;">Error loading Excel file: ' + error.message + '</div>';
            }
        });
}

function tryImportWithSheetJS(univer, data) {
    // Load SheetJS if available
    if (typeof XLSX !== 'undefined') {
        const workbook = XLSX.read(data, { type: 'array' });
        const sheetName = workbook.SheetNames[0];
        const worksheet = workbook.Sheets[sheetName];
        
        // Convert to Univer format
        const cellData = {};
        const range = XLSX.utils.decode_range(worksheet['!ref'] || 'A1');
        
        for (let row = range.s.r; row <= range.e.r; row++) {
            for (let col = range.s.c; col <= range.e.c; col++) {
                const cellAddress = XLSX.utils.encode_cell({ r: row, c: col });
                const cell = worksheet[cellAddress];
                if (cell) {
                    if (!cellData[row]) {
                        cellData[row] = {};
                    }
                    cellData[row][col] = {
                        v: cell.v,
                        t: cell.t,
                        s: cell.s,
                    };
                }
            }
        }
        
        // Update Univer sheet with data
        const sheet = univer.getActiveUnit();
        if (sheet && sheet.setCellData) {
            sheet.setCellData(cellData);
        }
    } else {
        console.warn('SheetJS not available for Excel import');
    }
}

// Cleanup function
window.destroyUniverSheets = function(containerId) {
    const container = document.getElementById(containerId);
    if (container && container._univerInstance) {
        try {
            container._univerInstance.dispose();
        } catch (e) {
            console.warn('Error disposing Univer instance:', e);
        }
        delete container._univerInstance;
    }
};
