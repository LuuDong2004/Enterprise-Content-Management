-- =============================================
-- Script thêm index để tối ưu hiệu năng
-- Database: PostgreSQL
-- =============================================
-- 
-- Mục đích: Tăng tốc độ query cho FOLDER và FILE_Descriptor
-- Chạy script này sau khi đã có dữ liệu
-- =============================================

-- Kết nối đến database (chạy từ psql hoặc pgAdmin)
-- \c ecm_db;

DO $$
BEGIN
    RAISE NOTICE 'Bắt đầu thêm index để tối ưu hiệu năng...';
END $$;

-- =============================================
-- INDEX CHO BẢNG FOLDER
-- =============================================

-- Index cho query: WHERE SOURCE_STORAGE_ID = ? AND IN_TRASH = 0
-- PostgreSQL sử dụng partial index (tương đương filtered index)
CREATE INDEX IF NOT EXISTS IDX_FOLDER_SOURCE_STORAGE_IN_TRASH 
ON "FOLDER" ("SOURCE_STORAGE_ID", "IN_TRASH")
WHERE "IN_TRASH" = false;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'IDX_FOLDER_SOURCE_STORAGE_IN_TRASH') THEN
        RAISE NOTICE 'Đã tạo index: IDX_FOLDER_SOURCE_STORAGE_IN_TRASH';
    END IF;
END $$;

-- Index cho query hierarchy: WHERE parent_ID = ?
CREATE INDEX IF NOT EXISTS IDX_FOLDER_PARENT 
ON "FOLDER" ("parent_ID")
WHERE "parent_ID" IS NOT NULL;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'IDX_FOLDER_PARENT') THEN
        RAISE NOTICE 'Đã tạo index: IDX_FOLDER_PARENT';
    END IF;
END $$;

-- Index cho query: WHERE parent_ID IS NULL AND SOURCE_STORAGE_ID = ? AND IN_TRASH = 0
CREATE INDEX IF NOT EXISTS IDX_FOLDER_ROOT_STORAGE 
ON "FOLDER" ("SOURCE_STORAGE_ID", "IN_TRASH")
WHERE "parent_ID" IS NULL AND "IN_TRASH" = false;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'IDX_FOLDER_ROOT_STORAGE') THEN
        RAISE NOTICE 'Đã tạo index: IDX_FOLDER_ROOT_STORAGE';
    END IF;
END $$;

-- =============================================
-- INDEX CHO BẢNG FILE_Descriptor
-- =============================================

-- Index cho query: WHERE SOURCE_STORAGE_ID = ? AND IN_TRASH = 0 AND FOLDER_ID IS NULL
CREATE INDEX IF NOT EXISTS IDX_FILE_SOURCE_STORAGE_ROOT 
ON "FILE_Descriptor" ("SOURCE_STORAGE_ID", "IN_TRASH")
WHERE "FOLDER_ID" IS NULL AND "IN_TRASH" = false;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'IDX_FILE_SOURCE_STORAGE_ROOT') THEN
        RAISE NOTICE 'Đã tạo index: IDX_FILE_SOURCE_STORAGE_ROOT';
    END IF;
END $$;

-- Index cho query: WHERE FOLDER_ID = ? AND IN_TRASH = 0
CREATE INDEX IF NOT EXISTS IDX_FILE_FOLDER_IN_TRASH 
ON "FILE_Descriptor" ("FOLDER_ID", "IN_TRASH")
WHERE "FOLDER_ID" IS NOT NULL AND "IN_TRASH" = false;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'IDX_FILE_FOLDER_IN_TRASH') THEN
        RAISE NOTICE 'Đã tạo index: IDX_FILE_FOLDER_IN_TRASH';
    END IF;
END $$;

-- Index cho query: WHERE SOURCE_STORAGE_ID = ? AND IN_TRASH = 0
CREATE INDEX IF NOT EXISTS IDX_FILE_SOURCE_STORAGE_IN_TRASH 
ON "FILE_Descriptor" ("SOURCE_STORAGE_ID", "IN_TRASH")
WHERE "IN_TRASH" = false;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'IDX_FILE_SOURCE_STORAGE_IN_TRASH') THEN
        RAISE NOTICE 'Đã tạo index: IDX_FILE_SOURCE_STORAGE_IN_TRASH';
    END IF;
END $$;

-- =============================================
-- THỐNG KÊ INDEX
-- =============================================

DO $$
BEGIN
    RAISE NOTICE '';
    RAISE NOTICE '========================================';
    RAISE NOTICE 'THỐNG KÊ INDEX ĐÃ TẠO';
    RAISE NOTICE '========================================';
END $$;

SELECT 
    tablename AS "Table Name",
    indexname AS "Index Name",
    indexdef AS "Index Definition"
FROM pg_indexes
WHERE schemaname = 'public'
    AND tablename IN ('FOLDER', 'FILE_Descriptor')
    AND indexname LIKE 'IDX_%'
ORDER BY tablename, indexname;

DO $$
BEGIN
    RAISE NOTICE '';
    RAISE NOTICE 'Hoàn thành! Các index đã được tạo để tối ưu hiệu năng.';
    RAISE NOTICE 'Lưu ý: Index có thể mất vài phút để build nếu có nhiều dữ liệu.';
END $$;

