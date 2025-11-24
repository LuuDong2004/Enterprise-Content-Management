-- 1.1: Closure table (lưu ancestor-descendant) và các chỉ mục
IF OBJECT_ID('dbo.folder_closure', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.folder_closure (
        ancestor   UNIQUEIDENTIFIER NOT NULL,
        descendant UNIQUEIDENTIFIER NOT NULL,
        depth      INT NOT NULL, -- 0 = chính nó, 1 = con trực tiếp, ...
        CONSTRAINT pk_folder_closure PRIMARY KEY (ancestor, descendant)
    );
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes WHERE name = 'ix_folder_closure_descendant' AND object_id = OBJECT_ID('dbo.folder_closure')
)
    CREATE INDEX ix_folder_closure_descendant ON dbo.folder_closure(descendant);
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes WHERE name = 'ix_folder_closure_ancestor_depth' AND object_id = OBJECT_ID('dbo.folder_closure')
)
    CREATE INDEX ix_folder_closure_ancestor_depth ON dbo.folder_closure(ancestor, depth);
GO

-- 1.2: Bảng quyền hiệu lực đã tính sẵn cho user -> folder
IF OBJECT_ID('dbo.user_folder_access', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.user_folder_access (
        user_id                    UNIQUEIDENTIFIER NOT NULL,
        folder_id                  UNIQUEIDENTIFIER NOT NULL,
        effective_permission_mask  INT NOT NULL,
        effective_allow            BIT NOT NULL,
        last_calculated            DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT pk_user_folder_access PRIMARY KEY (user_id, folder_id)
    );
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes WHERE name = 'ix_ufa_folder' AND object_id = OBJECT_ID('dbo.user_folder_access')
)
    CREATE INDEX ix_ufa_folder ON dbo.user_folder_access(folder_id);
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes WHERE name = 'ix_ufa_user' AND object_id = OBJECT_ID('dbo.user_folder_access')
)
    CREATE INDEX ix_ufa_user ON dbo.user_folder_access(user_id);
GO

-- 1.3: Hàm scalar cho PermissionService#getEffectiveMask
CREATE OR ALTER FUNCTION dbo.calculate_effective_permission
(
    @UserId   UNIQUEIDENTIFIER,
    @FolderId UNIQUEIDENTIFIER
)
RETURNS INT
AS
BEGIN
    DECLARE @mask INT = 0;

    ;WITH candidate_permissions AS (
        SELECT
            p.PERMISSION_MASK,
            fc.depth,
            ROW_NUMBER() OVER (ORDER BY fc.depth ASC) AS rn
        FROM dbo.folder_closure fc
        JOIN dbo.PERMISSION p
            ON p.FOLDER_ID = fc.ancestor
        WHERE fc.descendant = @FolderId
          AND p.USER_ID = @UserId
          AND p.PERMISSION_MASK IS NOT NULL
          AND (fc.depth = 0 OR ISNULL(p.INHERIT_ENABLED, 1) = 1)
    )
    SELECT @mask = candidate_permissions.PERMISSION_MASK
    FROM candidate_permissions
    WHERE rn = 1;

    RETURN ISNULL(@mask, 0);
END
GO

-- 2.1: Stored procedure hỗ trợ thêm node vào closure
CREATE OR ALTER PROCEDURE dbo.sp_InsertFolderClosureOnNew
    @newId    UNIQUEIDENTIFIER,
    @parentId UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;

    IF @parentId IS NULL
    BEGIN
        IF NOT EXISTS (
            SELECT 1
            FROM dbo.folder_closure fc
            WHERE fc.ancestor = @newId AND fc.descendant = @newId
        )
        BEGIN
            INSERT INTO dbo.folder_closure (ancestor, descendant, depth)
            VALUES (@newId, @newId, 0);
        END
        RETURN;
    END;

    INSERT INTO dbo.folder_closure (ancestor, descendant, depth)
    SELECT f.ancestor, @newId, f.depth + 1
    FROM dbo.folder_closure f
    WHERE f.descendant = @parentId
      AND NOT EXISTS (
            SELECT 1
            FROM dbo.folder_closure fc
            WHERE fc.ancestor = f.ancestor AND fc.descendant = @newId
        );

    IF NOT EXISTS (
        SELECT 1 FROM dbo.folder_closure fc WHERE fc.ancestor = @newId AND fc.descendant = @newId
    )
    BEGIN
        INSERT INTO dbo.folder_closure (ancestor, descendant, depth)
        VALUES (@newId, @newId, 0);
    END
END
GO

-- 2.2: Trigger cập nhật closure ngay khi thêm folder
CREATE OR ALTER TRIGGER trg_Folder_Insert_Closure
    ON dbo.FOLDER
    AFTER INSERT
AS
BEGIN
    SET NOCOUNT ON;

    INSERT INTO dbo.folder_closure (ancestor, descendant, depth)
    SELECT f.ancestor, i.ID, f.depth + 1
    FROM inserted i
    JOIN dbo.folder_closure f ON f.descendant = i.PARENT_ID
    WHERE i.PARENT_ID IS NOT NULL
      AND NOT EXISTS (
            SELECT 1
            FROM dbo.folder_closure fc
            WHERE fc.ancestor = f.ancestor AND fc.descendant = i.ID
        );

    INSERT INTO dbo.folder_closure (ancestor, descendant, depth)
    SELECT i.ID, i.ID, 0
    FROM inserted i
    WHERE NOT EXISTS (
        SELECT 1 FROM dbo.folder_closure fc WHERE fc.ancestor = i.ID AND fc.descendant = i.ID
    );
END
GO

-- 2.3: Rebuild closure toàn bộ (dùng khi migrate/move hàng loạt)
CREATE OR ALTER PROCEDURE dbo.sp_RebuildFolderClosure
AS
BEGIN
    SET NOCOUNT ON;

    TRUNCATE TABLE dbo.folder_closure;

    ;WITH RecursiveClosure AS (
        SELECT
            f.ID AS ancestor,
            f.ID AS descendant,
            0 AS depth
        FROM dbo.FOLDER f
        UNION ALL
        SELECT
            rc.ancestor,
            child.ID,
            rc.depth + 1
        FROM RecursiveClosure rc
        JOIN dbo.FOLDER child
            ON child.PARENT_ID = rc.descendant
    )
    INSERT INTO dbo.folder_closure (ancestor, descendant, depth)
    SELECT ancestor, descendant, depth
    FROM RecursiveClosure
    OPTION (MAXRECURSION 0);
END
GO

-- 4.1: Procedure tính lại quyền hiệu lực cho toàn bộ folder của một user
CREATE OR ALTER PROCEDURE dbo.usp_RecalculateAccessForUser
    @UserId UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;

    CREATE TABLE #tmp_results (
        folder_id                 UNIQUEIDENTIFIER,
        effective_permission_mask INT,
        effective_allow           BIT
    );

    ;WITH perms_on_ancestors AS (
        SELECT
            fc.descendant AS folder_id,
            p.PERMISSION_MASK,
            ROW_NUMBER() OVER (PARTITION BY fc.descendant ORDER BY fc.depth ASC) AS rn
        FROM dbo.PERMISSION p
        JOIN dbo.folder_closure fc
            ON fc.ancestor = p.FOLDER_ID
        WHERE p.USER_ID = @UserId
          AND p.PERMISSION_MASK IS NOT NULL
          AND (fc.depth = 0 OR ISNULL(p.INHERIT_ENABLED, 1) = 1)
    )
    INSERT INTO #tmp_results (folder_id, effective_permission_mask, effective_allow)
    SELECT
        folder_id,
        PERMISSION_MASK,
        CAST(CASE WHEN PERMISSION_MASK IS NOT NULL THEN 1 ELSE 0 END AS BIT)
    FROM perms_on_ancestors
    WHERE rn = 1;

    MERGE dbo.user_folder_access AS target
        USING #tmp_results AS src
        ON target.user_id = @UserId AND target.folder_id = src.folder_id
        WHEN MATCHED THEN
            UPDATE SET effective_permission_mask = src.effective_permission_mask,
                       effective_allow = src.effective_allow,
                       last_calculated = SYSUTCDATETIME()
        WHEN NOT MATCHED THEN
            INSERT (user_id, folder_id, effective_permission_mask, effective_allow, last_calculated)
            VALUES (@UserId, src.folder_id, src.effective_permission_mask, src.effective_allow, SYSUTCDATETIME());

    DROP TABLE #tmp_results;
END
GO

-- 4.2: Bulk propagate inheritance bằng SQL set-based
CREATE OR ALTER PROCEDURE dbo.usp_PermissionPropagateFromFolder
    @FolderId   UNIQUEIDENTIFIER,
    @ParentMask INT,
    @UserId     UNIQUEIDENTIFIER = NULL,
    @RoleCode   NVARCHAR(255) = NULL
AS
BEGIN
    SET NOCOUNT ON;

    IF @FolderId IS NULL
        RETURN;

    IF @UserId IS NULL AND (@RoleCode IS NULL OR LTRIM(RTRIM(@RoleCode)) = '')
        RETURN;

    DECLARE @MaskToApply INT = ISNULL(@ParentMask, 0);
    IF (@MaskToApply & 8) = 8
        SET @MaskToApply = 8;
    ELSE
        SET @MaskToApply = (@MaskToApply & 7);

    ;WITH TargetFolders AS (
        SELECT
            fc.descendant AS folder_id,
            parentFolder.FULL_PATH AS inherited_from
        FROM dbo.folder_closure fc
        JOIN dbo.FOLDER f ON f.ID = fc.descendant
        LEFT JOIN dbo.FOLDER parentFolder ON parentFolder.ID = f.PARENT_ID
        WHERE fc.ancestor = @FolderId
          AND fc.depth > 0
    )
    UPDATE p
    SET
        p.PERMISSION_MASK   = @MaskToApply,
        p.INHERITED         = 1,
        p.INHERIT_ENABLED   = 1,
        p.INHERITED_FROM    = COALESCE(tf.inherited_from, ''),
        p.APPLIES_TO        = 'THIS_FOLDER_SUBFOLDERS_FILES'
    FROM dbo.PERMISSION p
    JOIN TargetFolders tf ON tf.folder_id = p.FOLDER_ID
    WHERE ISNULL(p.INHERIT_ENABLED, 1) = 1
      AND (
            (@UserId IS NOT NULL AND p.USER_ID = @UserId)
            OR
            (@RoleCode IS NOT NULL AND p.ROLE_CODE = @RoleCode)
      );

    ;WITH TargetFiles AS (
        SELECT
            fd.ID AS file_id,
            folder.FULL_PATH AS folder_path
        FROM dbo.FILE_Descriptor fd
        JOIN dbo.folder_closure fc ON fc.descendant = fd.FOLDER_ID
        JOIN dbo.FOLDER folder ON folder.ID = fd.FOLDER_ID
        WHERE fc.ancestor = @FolderId
    )
    UPDATE p
    SET
        p.PERMISSION_MASK   = @MaskToApply,
        p.INHERITED         = 1,
        p.INHERIT_ENABLED   = 1,
        p.INHERITED_FROM    = COALESCE(tf.folder_path, ''),
        p.APPLIES_TO        = 'THIS_FOLDER_ONLY'
    FROM dbo.PERMISSION p
    JOIN TargetFiles tf ON tf.file_id = p.FILE_ID
    WHERE ISNULL(p.INHERIT_ENABLED, 1) = 1
      AND (
            (@UserId IS NOT NULL AND p.USER_ID = @UserId)
            OR
            (@RoleCode IS NOT NULL AND p.ROLE_CODE = @RoleCode)
      );
END
GO

-- 4.3: Replace toàn bộ quyền con bằng mask mới (xóa + tạo lại set-based)
CREATE OR ALTER PROCEDURE dbo.usp_PermissionReplaceChildren
    @FolderId   UNIQUEIDENTIFIER,
    @ParentMask INT,
    @UserId     UNIQUEIDENTIFIER = NULL,
    @RoleCode   NVARCHAR(255) = NULL
AS
BEGIN
    SET NOCOUNT ON;

    IF @FolderId IS NULL
        RETURN;

    IF @UserId IS NULL AND (@RoleCode IS NULL OR LTRIM(RTRIM(@RoleCode)) = '')
        RETURN;

    DECLARE @MaskToApply INT = ISNULL(@ParentMask, 0);
    IF (@MaskToApply & 8) = 8
        SET @MaskToApply = 8;
    ELSE
        SET @MaskToApply = (@MaskToApply & 7);

    CREATE TABLE #DescFolders (
        folder_id      UNIQUEIDENTIFIER,
        inherited_from NVARCHAR(1024)
    );

    INSERT INTO #DescFolders (folder_id, inherited_from)
    SELECT
        fc.descendant,
        parentFolder.FULL_PATH
    FROM dbo.folder_closure fc
    JOIN dbo.FOLDER f ON f.ID = fc.descendant
    LEFT JOIN dbo.FOLDER parentFolder ON parentFolder.ID = f.PARENT_ID
    WHERE fc.ancestor = @FolderId
      AND fc.depth > 0;

    CREATE TABLE #DescFiles (
        file_id        UNIQUEIDENTIFIER,
        inherited_from NVARCHAR(1024)
    );

    INSERT INTO #DescFiles (file_id, inherited_from)
    SELECT
        fd.ID,
        folder.FULL_PATH
    FROM dbo.FILE_Descriptor fd
    JOIN dbo.folder_closure fc ON fc.descendant = fd.FOLDER_ID
    JOIN dbo.FOLDER folder ON folder.ID = fd.FOLDER_ID
    WHERE fc.ancestor = @FolderId;

    DELETE p
    FROM dbo.PERMISSION p
    JOIN #DescFiles df ON df.file_id = p.FILE_ID
    WHERE (@UserId IS NOT NULL AND p.USER_ID = @UserId)
       OR (@RoleCode IS NOT NULL AND p.ROLE_CODE = @RoleCode);

    DELETE p
    FROM dbo.PERMISSION p
    JOIN #DescFolders df ON df.folder_id = p.FOLDER_ID
    WHERE (@UserId IS NOT NULL AND p.USER_ID = @UserId)
       OR (@RoleCode IS NOT NULL AND p.ROLE_CODE = @RoleCode);

    INSERT INTO dbo.PERMISSION (
        ID,
        USER_ID,
        ROLE_CODE,
        FILE_ID,
        PERMISSION_MASK,
        INHERITED,
        INHERIT_ENABLED,
        INHERITED_FROM,
        APPLIES_TO
    )
    SELECT
        NEWID(),
        @UserId,
        @RoleCode,
        df.file_id,
        @MaskToApply,
        1,
        1,
        COALESCE(df.inherited_from, ''),
        'THIS_FOLDER_ONLY'
    FROM #DescFiles df;

    INSERT INTO dbo.PERMISSION (
        ID,
        USER_ID,
        ROLE_CODE,
        FOLDER_ID,
        PERMISSION_MASK,
        INHERITED,
        INHERIT_ENABLED,
        INHERITED_FROM,
        APPLIES_TO
    )
    SELECT
        NEWID(),
        @UserId,
        @RoleCode,
        df.folder_id,
        @MaskToApply,
        1,
        1,
        COALESCE(df.inherited_from, ''),
        'THIS_FOLDER_SUBFOLDERS_FILES'
    FROM #DescFolders df;

    DROP TABLE #DescFiles;
    DROP TABLE #DescFolders;
END
GO

-- 4.4: Cập nhật closure cho subtree khi di chuyển folder sang node khác
CREATE OR ALTER PROCEDURE dbo.usp_UpdateFolderClosureForMove
    @FolderId UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;

    IF @FolderId IS NULL
        RETURN;

    CREATE TABLE #Subtree (
        ID        UNIQUEIDENTIFIER PRIMARY KEY,
        ParentId  UNIQUEIDENTIFIER
    );

    ;WITH Subtree AS (
        SELECT
            f.ID,
            f.PARENT_ID
        FROM dbo.FOLDER f
        WHERE f.ID = @FolderId
        UNION ALL
        SELECT
            child.ID,
            child.PARENT_ID
        FROM dbo.FOLDER child
        JOIN Subtree st ON child.PARENT_ID = st.ID
    )
    INSERT INTO #Subtree (ID, ParentId)
    SELECT ID, Parent_ID
    FROM Subtree;

    DELETE fc
    FROM dbo.folder_closure fc
    JOIN #Subtree st ON fc.descendant = st.ID;

    ;WITH AncestorPaths AS (
        SELECT
            st.ID AS descendant,
            st.ID AS ancestor,
            0 AS depth,
            st.ParentId
        FROM #Subtree st
        UNION ALL
        SELECT
            ap.descendant,
            parent.ID AS ancestor,
            ap.depth + 1 AS depth,
            parent.PARENT_ID
        FROM AncestorPaths ap
        JOIN dbo.FOLDER parent ON ap.ParentId = parent.ID
    )
    INSERT INTO dbo.folder_closure (ancestor, descendant, depth)
    SELECT DISTINCT ancestor, descendant, depth
    FROM AncestorPaths
    OPTION (MAXRECURSION 0);

    DROP TABLE #Subtree;
END
GO

