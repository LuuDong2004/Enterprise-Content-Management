-- =============================================
-- Script thêm index để tối ưu hiệu năng
-- Database: ECM_DB
-- =============================================
-- 
-- Mục đích: Tăng tốc độ query cho FOLDER và FILE_Descriptor
-- Chạy script này sau khi đã có dữ liệu
-- =============================================

USE [ECM_DB]
GO

PRINT 'Bắt đầu thêm index để tối ưu hiệu năng...'
GO

-- =============================================
-- INDEX CHO BẢNG FOLDER
-- =============================================

-- Index cho query: WHERE SOURCE_STORAGE_ID = ? AND IN_TRASH = 0
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IDX_FOLDER_SOURCE_STORAGE_IN_TRASH' AND object_id = OBJECT_ID('FOLDER'))
BEGIN
    CREATE NONCLUSTERED INDEX [IDX_FOLDER_SOURCE_STORAGE_IN_TRASH] 
    ON [FOLDER] ([SOURCE_STORAGE_ID], [IN_TRASH])
    WHERE [IN_TRASH] = 0;
    PRINT 'Đã tạo index: IDX_FOLDER_SOURCE_STORAGE_IN_TRASH'
END
ELSE
BEGIN
    PRINT 'Index IDX_FOLDER_SOURCE_STORAGE_IN_TRASH đã tồn tại'
END
GO

-- Index cho query hierarchy: WHERE parent_ID = ?
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IDX_FOLDER_PARENT' AND object_id = OBJECT_ID('FOLDER'))
BEGIN
    CREATE NONCLUSTERED INDEX [IDX_FOLDER_PARENT] 
    ON [FOLDER] ([parent_ID])
    WHERE [parent_ID] IS NOT NULL;
    PRINT 'Đã tạo index: IDX_FOLDER_PARENT'
END
ELSE
BEGIN
    PRINT 'Index IDX_FOLDER_PARENT đã tồn tại'
END
GO

-- Index cho query: WHERE parent_ID IS NULL AND SOURCE_STORAGE_ID = ? AND IN_TRASH = 0
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IDX_FOLDER_ROOT_STORAGE' AND object_id = OBJECT_ID('FOLDER'))
BEGIN
    CREATE NONCLUSTERED INDEX [IDX_FOLDER_ROOT_STORAGE] 
    ON [FOLDER] ([SOURCE_STORAGE_ID], [IN_TRASH])
    WHERE [parent_ID] IS NULL AND [IN_TRASH] = 0;
    PRINT 'Đã tạo index: IDX_FOLDER_ROOT_STORAGE'
END
ELSE
BEGIN
    PRINT 'Index IDX_FOLDER_ROOT_STORAGE đã tồn tại'
END
GO

-- =============================================
-- INDEX CHO BẢNG FILE_Descriptor
-- =============================================

-- Index cho query: WHERE SOURCE_STORAGE_ID = ? AND IN_TRASH = 0 AND FOLDER_ID IS NULL
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IDX_FILE_SOURCE_STORAGE_ROOT' AND object_id = OBJECT_ID('FILE_Descriptor'))
BEGIN
    CREATE NONCLUSTERED INDEX [IDX_FILE_SOURCE_STORAGE_ROOT] 
    ON [FILE_Descriptor] ([SOURCE_STORAGE_ID], [IN_TRASH])
    WHERE [FOLDER_ID] IS NULL AND [IN_TRASH] = 0;
    PRINT 'Đã tạo index: IDX_FILE_SOURCE_STORAGE_ROOT'
END
ELSE
BEGIN
    PRINT 'Index IDX_FILE_SOURCE_STORAGE_ROOT đã tồn tại'
END
GO

-- Index cho query: WHERE FOLDER_ID = ? AND IN_TRASH = 0
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IDX_FILE_FOLDER_IN_TRASH' AND object_id = OBJECT_ID('FILE_Descriptor'))
BEGIN
    CREATE NONCLUSTERED INDEX [IDX_FILE_FOLDER_IN_TRASH] 
    ON [FILE_Descriptor] ([FOLDER_ID], [IN_TRASH])
    WHERE [FOLDER_ID] IS NOT NULL AND [IN_TRASH] = 0;
    PRINT 'Đã tạo index: IDX_FILE_FOLDER_IN_TRASH'
END
ELSE
BEGIN
    PRINT 'Index IDX_FILE_FOLDER_IN_TRASH đã tồn tại'
END
GO

-- Index cho query: WHERE SOURCE_STORAGE_ID = ? AND IN_TRASH = 0
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IDX_FILE_SOURCE_STORAGE_IN_TRASH' AND object_id = OBJECT_ID('FILE_Descriptor'))
BEGIN
    CREATE NONCLUSTERED INDEX [IDX_FILE_SOURCE_STORAGE_IN_TRASH] 
    ON [FILE_Descriptor] ([SOURCE_STORAGE_ID], [IN_TRASH])
    WHERE [IN_TRASH] = 0;
    PRINT 'Đã tạo index: IDX_FILE_SOURCE_STORAGE_IN_TRASH'
END
ELSE
BEGIN
    PRINT 'Index IDX_FILE_SOURCE_STORAGE_IN_TRASH đã tồn tại'
END
GO

-- =============================================
-- THỐNG KÊ INDEX
-- =============================================

PRINT ''
PRINT '========================================'
PRINT 'THỐNG KÊ INDEX ĐÃ TẠO'
PRINT '========================================'

SELECT 
    OBJECT_NAME(object_id) AS [Table Name],
    name AS [Index Name],
    type_desc AS [Index Type]
FROM sys.indexes
WHERE object_id IN (OBJECT_ID('FOLDER'), OBJECT_ID('FILE_Descriptor'))
    AND name LIKE 'IDX_%'
ORDER BY OBJECT_NAME(object_id), name

PRINT ''
PRINT 'Hoàn thành! Các index đã được tạo để tối ưu hiệu năng.'
PRINT 'Lưu ý: Index có thể mất vài phút để build nếu có nhiều dữ liệu.'
GO

