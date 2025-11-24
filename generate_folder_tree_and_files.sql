-- =============================================
-- Script tạo cây thư mục 10 cấp và 10,000 file ngẫu nhiên
-- Database: ECM_DB
-- =============================================
-- 
-- HƯỚNG DẪN SỬ DỤNG:
-- 1. Chạy lần đầu: Chạy toàn bộ script để tạo thư mục và file
-- 2. Nếu dừng giữa chừng: Chạy lại script từ đầu, script sẽ tự động:
--    - Phát hiện số file đã có
--    - Chỉ tạo tiếp những file còn thiếu
--    - Tái tạo bảng tạm nếu cần
-- 3. Để xóa dữ liệu cũ: Bỏ comment ở dòng 12-14
-- =============================================

USE [ECM_DB]
GO

-- Xóa dữ liệu cũ (nếu cần) - BỎ COMMENT ĐỂ XÓA DỮ LIỆU CŨ
-- DELETE FROM [FILE_Descriptor]
-- DELETE FROM [FOLDER]
-- GO

-- =============================================
-- BƯỚC 1: Tạo cây thư mục 10 cấp
-- =============================================

DECLARE @RootFolderId UNIQUEIDENTIFIER = NEWID()
DECLARE @StorageId UNIQUEIDENTIFIER = 'C6BE8256-E331-4768-9995-670A66A61774' -- Storage ID mặc định
DECLARE @CurrentDate DATETIME = GETDATE()
DECLARE @Level INT = 0
DECLARE @ParentId UNIQUEIDENTIFIER = NULL
DECLARE @FolderId UNIQUEIDENTIFIER
DECLARE @FolderName NVARCHAR(255)
DECLARE @FullPath NVARCHAR(MAX) = ''
DECLARE @Counter INT = 0

-- Tạo thư mục root
INSERT INTO [FOLDER] ([ID], [name], [parent_ID], [createdDate], [FULL_PATH], [SOURCE_STORAGE_ID], [IN_TRASH], [DELETED_BY], [DELETE_DATE])
VALUES (@RootFolderId, 'Root', NULL, @CurrentDate, 'Root/', @StorageId, 0, NULL, NULL)

SET @ParentId = @RootFolderId
SET @FullPath = 'Root/'

-- Xóa bảng tạm nếu đã tồn tại (từ lần chạy trước)
IF OBJECT_ID('tempdb..#FolderTree') IS NOT NULL
BEGIN
    DROP TABLE #FolderTree
END

-- Tạo bảng tạm để lưu thông tin folder theo level
CREATE TABLE #FolderTree (
    Level INT,
    FolderId UNIQUEIDENTIFIER,
    ParentId UNIQUEIDENTIFIER,
    FolderName NVARCHAR(255),
    FullPath NVARCHAR(MAX)
)

INSERT INTO #FolderTree VALUES (0, @RootFolderId, NULL, 'Root', 'Root/')

-- Tạo 10 cấp thư mục
DECLARE @FoldersPerLevel INT = 3 -- Số thư mục con mỗi cấp (3 folders/level = ~88,572 folders total)
DECLARE @CurrentLevel INT = 1

WHILE @CurrentLevel <= 10
BEGIN
    DECLARE @ParentFolders TABLE (FolderId UNIQUEIDENTIFIER, FullPath NVARCHAR(MAX))
    
    -- Lấy tất cả folder ở level hiện tại - 1
    INSERT INTO @ParentFolders
    SELECT FolderId, FullPath
    FROM #FolderTree
    WHERE Level = @CurrentLevel - 1
    
    DECLARE parent_cursor CURSOR FOR
    SELECT FolderId, FullPath FROM @ParentFolders
    
    OPEN parent_cursor
    FETCH NEXT FROM parent_cursor INTO @ParentId, @FullPath
    
    WHILE @@FETCH_STATUS = 0
    BEGIN
        DECLARE @ChildCounter INT = 1
        DECLARE @ParentFullPath NVARCHAR(MAX) = @FullPath
        
        -- Tạo @FoldersPerLevel thư mục con cho mỗi thư mục cha
        WHILE @ChildCounter <= @FoldersPerLevel
        BEGIN
            SET @FolderId = NEWID()
            SET @FolderName = 'Folder_L' + CAST(@CurrentLevel AS VARCHAR) + '_' + CAST(@ChildCounter AS VARCHAR)
            SET @FullPath = @ParentFullPath + @FolderName + '/'
            
            INSERT INTO [FOLDER] ([ID], [name], [parent_ID], [createdDate], [FULL_PATH], [SOURCE_STORAGE_ID], [IN_TRASH], [DELETED_BY], [DELETE_DATE])
            VALUES (@FolderId, @FolderName, @ParentId, DATEADD(SECOND, @Counter, @CurrentDate), @FullPath, @StorageId, 0, NULL, NULL)
            
            INSERT INTO #FolderTree VALUES (@CurrentLevel, @FolderId, @ParentId, @FolderName, @FullPath)
            
            SET @Counter = @Counter + 1
            SET @ChildCounter = @ChildCounter + 1
        END
        
        FETCH NEXT FROM parent_cursor INTO @ParentId, @FullPath
    END
    
    CLOSE parent_cursor
    DEALLOCATE parent_cursor
    
    SET @CurrentLevel = @CurrentLevel + 1
END

-- =============================================
-- BƯỚC 2: Tạo 10,000 file ngẫu nhiên và phân bổ vào các thư mục
-- =============================================

DECLARE @FileCount INT = 10000
DECLARE @CurrentFileCount INT = 0
DECLARE @FileCounter INT = 1
DECLARE @FileId UNIQUEIDENTIFIER
DECLARE @FileName NVARCHAR(255)
DECLARE @Extension NVARCHAR(10)
DECLARE @FileSize BIGINT
DECLARE @LastModified DATETIME
DECLARE @TargetFolderId UNIQUEIDENTIFIER
DECLARE @FileRef NVARCHAR(255)
DECLARE @CreateBy NVARCHAR(255)
DECLARE @RandomFolderCount INT

-- Kiểm tra số file hiện có (để có thể tiếp tục nếu đã dừng giữa chừng)
SELECT @CurrentFileCount = COUNT(*) FROM [FILE_Descriptor]

IF @CurrentFileCount >= @FileCount
BEGIN
    PRINT 'Đã có đủ ' + CAST(@CurrentFileCount AS VARCHAR) + ' file. Không cần tạo thêm.'
    SET @FileCounter = @FileCount + 1 -- Bỏ qua vòng lặp
END
ELSE IF @CurrentFileCount > 0
BEGIN
    PRINT 'Đã phát hiện ' + CAST(@CurrentFileCount AS VARCHAR) + ' file hiện có trong database.'
    PRINT 'Sẽ tạo tiếp ' + CAST(@FileCount - @CurrentFileCount AS VARCHAR) + ' file còn lại...'
    SET @FileCounter = @CurrentFileCount + 1
END
ELSE
BEGIN
    PRINT 'Bắt đầu tạo mới ' + CAST(@FileCount AS VARCHAR) + ' file...'
    SET @FileCounter = 1
END

-- Tái tạo bảng tạm #FolderTree từ dữ liệu thực tế (nếu bảng đã bị xóa)
IF OBJECT_ID('tempdb..#FolderTree') IS NULL
BEGIN
    -- Kiểm tra xem có folder nào chưa
    DECLARE @FolderExists INT
    SELECT @FolderExists = COUNT(*) FROM [FOLDER]
    
    IF @FolderExists = 0
    BEGIN
        PRINT 'LỖI: Chưa có thư mục nào trong database. Vui lòng chạy phần tạo thư mục trước!'
        RETURN
    END
    
    PRINT 'Tái tạo bảng tạm #FolderTree từ dữ liệu FOLDER...'
    CREATE TABLE #FolderTree (
        Level INT,
        FolderId UNIQUEIDENTIFIER,
        ParentId UNIQUEIDENTIFIER,
        FolderName NVARCHAR(255),
        FullPath NVARCHAR(MAX)
    )
    
    -- Tính level và insert vào bảng tạm
    ;WITH FolderLevels AS (
        SELECT ID, parent_ID, name, FULL_PATH, 0 AS Level
        FROM [FOLDER]
        WHERE parent_ID IS NULL
        
        UNION ALL
        
        SELECT f.ID, f.parent_ID, f.name, f.FULL_PATH, fl.Level + 1
        FROM [FOLDER] f
        INNER JOIN FolderLevels fl ON f.parent_ID = fl.ID
    )
    INSERT INTO #FolderTree (Level, FolderId, ParentId, FolderName, FullPath)
    SELECT Level, ID, parent_ID, name, ISNULL(FULL_PATH, name + '/')
    FROM FolderLevels
    
    PRINT 'Đã tái tạo bảng tạm với ' + CAST(@@ROWCOUNT AS VARCHAR) + ' thư mục.'
END
ELSE
BEGIN
    -- Bảng tạm đã tồn tại, xóa và tái tạo để đảm bảo dữ liệu đúng
    PRINT 'Bảng tạm #FolderTree đã tồn tại. Đang xóa và tái tạo...'
    DROP TABLE #FolderTree
    
    CREATE TABLE #FolderTree (
        Level INT,
        FolderId UNIQUEIDENTIFIER,
        ParentId UNIQUEIDENTIFIER,
        FolderName NVARCHAR(255),
        FullPath NVARCHAR(MAX)
    )
    
    -- Tính level và insert vào bảng tạm
    ;WITH FolderLevels AS (
        SELECT ID, parent_ID, name, FULL_PATH, 0 AS Level
        FROM [FOLDER]
        WHERE parent_ID IS NULL
        
        UNION ALL
        
        SELECT f.ID, f.parent_ID, f.name, f.FULL_PATH, fl.Level + 1
        FROM [FOLDER] f
        INNER JOIN FolderLevels fl ON f.parent_ID = fl.ID
    )
    INSERT INTO #FolderTree (Level, FolderId, ParentId, FolderName, FullPath)
    SELECT Level, ID, parent_ID, name, ISNULL(FULL_PATH, name + '/')
    FROM FolderLevels
    
    PRINT 'Đã tái tạo bảng tạm với ' + CAST(@@ROWCOUNT AS VARCHAR) + ' thư mục.'
END

-- Danh sách extension phổ biến
DECLARE @Extensions TABLE (Ext NVARCHAR(10))
INSERT INTO @Extensions VALUES ('txt'), ('pdf'), ('docx'), ('xlsx'), ('pptx'), ('jpg'), ('png'), ('gif'), ('mp4'), ('mp3'), ('zip'), ('rar'), ('xml'), ('json'), ('csv')

-- Danh sách tên file mẫu
DECLARE @FileNames TABLE (NamePrefix NVARCHAR(50))
INSERT INTO @FileNames VALUES ('Document'), ('Report'), ('Image'), ('Video'), ('Audio'), ('Data'), ('Backup'), ('Archive'), ('Script'), ('Config'), ('Template'), ('Presentation'), ('Spreadsheet'), ('Manual'), ('Guide')

-- Danh sách user
DECLARE @Users TABLE (UserName NVARCHAR(255))
INSERT INTO @Users VALUES ('admin'), ('Duc Anh'), ('User1'), ('User2'), ('User3')

-- Lấy số lượng folder có sẵn
SELECT @RandomFolderCount = COUNT(*) FROM #FolderTree

-- Tạo file còn thiếu (từ @FileCounter đến @FileCount)
WHILE @FileCounter <= @FileCount
BEGIN
    SET @FileId = NEWID()
    
    -- Chọn extension ngẫu nhiên
    SELECT TOP 1 @Extension = Ext 
    FROM @Extensions 
    ORDER BY NEWID()
    
    -- Chọn tên file ngẫu nhiên
    DECLARE @NamePrefix NVARCHAR(50)
    SELECT TOP 1 @NamePrefix = NamePrefix 
    FROM @FileNames 
    ORDER BY NEWID()
    
    SET @FileName = @NamePrefix + '_' + CAST(@FileCounter AS VARCHAR) + '.' + @Extension
    
    -- Kích thước file ngẫu nhiên (từ 1KB đến 10MB)
    SET @FileSize = CAST((RAND() * 10485760 + 1024) AS BIGINT)
    
    -- Ngày sửa đổi ngẫu nhiên (trong vòng 1 năm gần đây)
    SET @LastModified = DATEADD(DAY, -CAST(RAND() * 365 AS INT), GETDATE())
    SET @LastModified = DATEADD(SECOND, -CAST(RAND() * 86400 AS INT), @LastModified)
    
    -- Chọn folder ngẫu nhiên
    SELECT TOP 1 @TargetFolderId = FolderId 
    FROM #FolderTree 
    ORDER BY NEWID()
    
    -- Tạo FILE_REF giả lập
    DECLARE @Year INT = YEAR(@LastModified)
    DECLARE @Month INT = MONTH(@LastModified)
    SET @FileRef = 's3-c6be8256-e331-4768-9995-670a66a61774://' + CAST(@Year AS VARCHAR) + '/' + CAST(@Month AS VARCHAR) + '/' + CAST(@FileId AS VARCHAR(36)) + '_' + @FileName
    
    -- Chọn user ngẫu nhiên
    SELECT TOP 1 @CreateBy = UserName 
    FROM @Users 
    ORDER BY NEWID()
    
    -- Quyết định file có bị xóa không (5% khả năng)
    DECLARE @IsDeleted BIT = CASE WHEN RAND() < 0.05 THEN 1 ELSE 0 END
    DECLARE @DeletedBy NVARCHAR(255) = NULL
    DECLARE @DeleteDate DATETIME = NULL
    
    IF @IsDeleted = 1
    BEGIN
        SELECT TOP 1 @DeletedBy = UserName FROM @Users ORDER BY NEWID()
        SET @DeleteDate = DATEADD(DAY, CAST(RAND() * 30 AS INT), @LastModified)
    END
    
    -- Insert file
    INSERT INTO [FILE_Descriptor] (
        [ID], 
        [NAME], 
        [SOURCE_STORAGE_ID], 
        [EXTENSION], 
        [SIZE], 
        [LASTMODIFIED], 
        [FOLDER_ID], 
        [FILE_REF], 
        [CREATE_BY], 
        [DELETED_BY], 
        [DELETE_DATE], 
        [IN_TRASH]
    )
    VALUES (
        @FileId,
        @FileName,
        @StorageId,
        @Extension,
        @FileSize,
        @LastModified,
        @TargetFolderId,
        @FileRef,
        @CreateBy,
        @DeletedBy,
        @DeleteDate,
        @IsDeleted
    )
    
    SET @FileCounter = @FileCounter + 1
    
    -- Hiển thị tiến trình mỗi 1000 file
    IF (@FileCounter - @CurrentFileCount) % 1000 = 0 OR @FileCounter = @FileCount
    BEGIN
        PRINT 'Đã tạo ' + CAST(@FileCounter - @CurrentFileCount AS VARCHAR) + ' file mới. Tổng: ' + CAST(@FileCounter AS VARCHAR) + '/' + CAST(@FileCount AS VARCHAR) + ' file...'
    END
END

-- Dọn dẹp bảng tạm (chỉ xóa nếu đã tạo xong tất cả file)
IF @FileCounter > @FileCount
BEGIN
    DROP TABLE #FolderTree
END

-- =============================================
-- BƯỚC 3: Hiển thị thống kê
-- =============================================

PRINT '========================================'
PRINT 'THỐNG KÊ DỮ LIỆU ĐÃ TẠO'
PRINT '========================================'

DECLARE @TotalFolders INT
DECLARE @TotalFiles INT
DECLARE @MaxLevel INT

SELECT @TotalFolders = COUNT(*) FROM [FOLDER]
SELECT @TotalFiles = COUNT(*) FROM [FILE_Descriptor]

-- Tính level tối đa bằng recursive CTE
;WITH FolderLevels AS (
    -- Root folders (level 0)
    SELECT ID, parent_ID, 0 AS Level
    FROM [FOLDER]
    WHERE parent_ID IS NULL
    
    UNION ALL
    
    -- Child folders
    SELECT f.ID, f.parent_ID, fl.Level + 1
    FROM [FOLDER] f
    INNER JOIN FolderLevels fl ON f.parent_ID = fl.ID
)
SELECT @MaxLevel = MAX(Level) FROM FolderLevels

PRINT 'Tổng số thư mục: ' + CAST(@TotalFolders AS VARCHAR)
PRINT 'Tổng số file: ' + CAST(@TotalFiles AS VARCHAR)
PRINT 'Số cấp thư mục tối đa: ' + CAST(@MaxLevel AS VARCHAR)

-- Thống kê file theo extension
PRINT ''
PRINT 'Thống kê file theo extension:'
SELECT 
    [EXTENSION], 
    COUNT(*) AS [Số lượng],
    CAST(COUNT(*) * 100.0 / @TotalFiles AS DECIMAL(5,2)) AS [Tỷ lệ %]
FROM [FILE_Descriptor]
WHERE [EXTENSION] IS NOT NULL
GROUP BY [EXTENSION]
ORDER BY COUNT(*) DESC

-- Thống kê file theo folder level
PRINT ''
PRINT 'Thống kê file theo cấp thư mục:'
;WITH FolderLevels AS (
    SELECT ID, parent_ID, 0 AS Level
    FROM [FOLDER]
    WHERE parent_ID IS NULL
    
    UNION ALL
    
    SELECT f.ID, f.parent_ID, fl.Level + 1
    FROM [FOLDER] f
    INNER JOIN FolderLevels fl ON f.parent_ID = fl.ID
)
SELECT 
    fl.Level AS [Cấp],
    COUNT(fd.ID) AS [Số file]
FROM FolderLevels fl
LEFT JOIN [FILE_Descriptor] fd ON fd.FOLDER_ID = fl.ID
GROUP BY fl.Level
ORDER BY fl.Level

PRINT ''
PRINT 'Hoàn thành!'

GO

