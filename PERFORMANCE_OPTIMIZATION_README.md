# Hướng dẫn Tối ưu Hiệu năng ECM

## Vấn đề
Ứng dụng bị chậm khi truy cập kho MinIO với hơn 600 file và nhiều thư mục.

## Giải pháp đã áp dụng

### 1. ✅ Thêm Index vào Database (QUAN TRỌNG NHẤT)

**File:** `add_performance_indexes.sql`

Chạy script SQL này để tạo các index tối ưu:

```sql
-- Chạy trong SQL Server Management Studio
USE [ECM_DB]
GO
-- Chạy file: add_performance_indexes.sql
```

**Các index được tạo:**
- `IDX_FOLDER_SOURCE_STORAGE_IN_TRASH` - Tối ưu query load folder theo storage
- `IDX_FOLDER_PARENT` - Tối ưu query hierarchy (parent-child)
- `IDX_FOLDER_ROOT_STORAGE` - Tối ưu query root folders
- `IDX_FILE_SOURCE_STORAGE_ROOT` - Tối ưu query file ở root
- `IDX_FILE_FOLDER_IN_TRASH` - Tối ưu query file trong folder
- `IDX_FILE_SOURCE_STORAGE_IN_TRASH` - Tối ưu query file theo storage

**Lưu ý:** Index có thể mất vài phút để build nếu có nhiều dữ liệu.

### 2. ✅ Cập nhật Entity Annotations

**Files đã cập nhật:**
- `src/main/java/com/vn/ecm/entity/Folder.java`
- `src/main/java/com/vn/ecm/entity/FileDescriptor.java`

Đã thêm `@Index` annotations để JPA tự động tạo index khi deploy.

### 3. ✅ Thêm Pagination cho File Grid

**File đã cập nhật:**
- `src/main/resources/com/vn/ecm/view/ecm/ECM-view.xml`

Đã thêm `pageSize="50"` cho `fileDataGird` để chỉ load 50 file mỗi trang thay vì tất cả.

## Cách áp dụng

### Bước 1: Chạy Script SQL (BẮT BUỘC)
1. Mở SQL Server Management Studio
2. Kết nối đến database `ECM_DB`
3. Mở file `add_performance_indexes.sql`
4. Chạy script (F5)
5. Đợi script hoàn thành (có thể mất vài phút nếu có nhiều dữ liệu)

### Bước 2: Rebuild và Deploy ứng dụng
1. Rebuild project để JPA tạo index từ annotations
2. Deploy lại ứng dụng
3. Test lại hiệu năng

## Kết quả mong đợi

- **Trước:** Load tất cả folder/file cùng lúc → chậm, timeout
- **Sau:** 
  - Query nhanh hơn nhờ index
  - Chỉ load 50 file mỗi trang
  - Giảm memory usage
  - Ứng dụng phản hồi nhanh hơn

## Kiểm tra Index đã tạo

Chạy query sau để kiểm tra:

```sql
SELECT 
    OBJECT_NAME(object_id) AS [Table Name],
    name AS [Index Name],
    type_desc AS [Index Type]
FROM sys.indexes
WHERE object_id IN (OBJECT_ID('FOLDER'), OBJECT_ID('FILE_Descriptor'))
    AND name LIKE 'IDX_%'
ORDER BY OBJECT_NAME(object_id), name
```

## Lưu ý

- Index sẽ chiếm thêm dung lượng database (thường không đáng kể)
- Index sẽ tự động cập nhật khi insert/update/delete
- Nếu có nhiều dữ liệu, việc tạo index có thể mất thời gian
- Sau khi tạo index, query sẽ nhanh hơn đáng kể

## Troubleshooting

### Nếu vẫn chậm sau khi tạo index:
1. Kiểm tra xem index đã được tạo chưa (dùng query trên)
2. Kiểm tra execution plan của query chậm
3. Có thể cần thêm index khác tùy vào query pattern
4. Kiểm tra số lượng dữ liệu - nếu quá nhiều (>100k records), cần xem xét pagination cho folder tree

### Nếu gặp lỗi khi tạo index:
- Đảm bảo database không đang bị lock
- Kiểm tra quyền user có đủ để tạo index
- Xem message lỗi cụ thể để xử lý

