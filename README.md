# Enterprise Content Management (ECM) 📁

ECM is a Jmix + Vaadin Flow solution that lets enterprises store, protect, search, and share content across on-prem folders and S3-compatible buckets—all inside a single, desktop-like UI.

## Highlights ✨
- **Multi-storage hub** – register S3 or Web Directory sources and mount them instantly.
- **Explorer UI** – tree navigation, 1 GB uploads, previews, metadata side panel, list/tile views.
- **Smart permissions** – assign to users or roles, convert/break inheritance, restore from trash.
- **OCR search** – Tess4J + PDFBox extract Vietnamese text into MongoDB for fuzzy or exact lookup.
- **Responsive ops** – long actions (move, convert, re-enable inherit) run via Flow UI background tasks.

## Architecture 🏗️

| Layer | Stack |
| --- | --- |
| UI | Vaadin Flow, custom `MainView`, Flow UI descriptors |
| Backend | Spring Boot 3, Jmix 2.7, Java 17, Gradle |
| Persistence | SQL Server (default) / PostgreSQL, Liquibase changelog |
| Storage | Dynamic S3 + WebDir via `DynamicStorageManager`, AWS SDK v2 |
| Search/OCR | MongoDB 6+, Tess4J, Apache PDFBox |
| Security | Jmix security, resource roles, custom bit-mask `PermissionService` |

## Business Modules 🧩
- **Storages** – CRUD on `SourceStorage`; open ECM explorer scoped to any active store.
- **ECM Explorer** – folder CRUD/drag, upload/download, previews, metadata panel, OCR search bar.
- **Permissions** – Assign View for quick masks, Advanced View for inheritance, edit dialogs for conversions.
- **Trash & retention** – soft delete folders/files, restore or purge recursively.
- **Preview stack** – dedicated routes for PDF, Office, image, HTML, and video pop-ups.

## Technology Stack 🛠️
`Jmix · Vaadin Flow · Spring Boot · SQL Server/PostgreSQL · MongoDB · AWS SDK v2 · Tess4J · PDFBox · Gradle`

## Project Layout 🗂️
```
src/main/java/com/vn/ecm
 ├─ entity/        # Folder, FileDescriptor, Permission, User…
 ├─ service/ecm/   # Permissions, folders, files, OCR indexing
 ├─ ecm/storage/   # S3 + WebDir runtimes
 ├─ ocr/log/       # OCR + Mongo repositories
 └─ view/          # Flow UI controllers (Explorer, Permissions, etc.)
src/main/resources/com/vn/ecm  # UI descriptors + Liquibase
src/main/frontend              # Themes + generated Flow glue
Web_Directory_Storage          # Sample WebDir tree
```

## Quick Start 🚀
1. Install JDK 17+, Node 18+, SQL Server (or Postgres), MongoDB 6+, optional Tesseract + ClamAV.
2. Clone repo and configure `application.properties` (DBs, Mongo, login defaults, storage paths).
3. Create at least one `SourceStorage` (S3 creds or WebDir root).
4. `./gradlew bootRun` → browse to `http://localhost:8080` → login `admin/admin`.
5. For production assets run `./gradlew vaadinBuildFrontend bootJar`.

## Key Config ⚙️

| Property | Purpose |
| --- | --- |
| `main.datasource.*` | Primary relational DB |
| `spring.data.mongodb.uri` | OCR index store |
| `spring.servlet.multipart.max-file-size` | Upload limit (1 GB) |
| `ui.login.defaultUsername/password` | Dev convenience (remove in prod) |

## Storage & Search 📦🔍
- WEBDIR requires an accessible root (see `Web_Directory_Storage`).  
- S3 entries need bucket, region, keys, optional endpoint/path-style flag.  
- Uploads duplicate the temp file, OCR it, and store text (with/without diacritics) in MongoDB.  
- Explorer search toggles **Exact** and **Ignore diacritics** for precise matching.

## Security & Permissions 🔐
- Permissions stored as bit masks (`PermissionType`).  
- Assign View shows effective CRUD/FULL toggles; Advanced View manages inheritance actions and confirmation dialogs (`BlockInheritance`, `ConfirmRemove`, `ConfirmReplace`).  
- Background tasks keep UI responsive during conversions or re-enabling inheritance.

## Useful Commands 🧰
| Command | Description |
| --- | --- |
| `./gradlew bootRun` | Launch dev server |
| `./gradlew vaadinBuildFrontend` | Build Flow frontend bundle |
| `./gradlew bootJar` | Package runnable JAR |
| `./gradlew test` | Run Spring + Flow UI tests |

## Testing ✅
Run `./gradlew test`; extend suites under `src/test/java/com/vn/ecm` for new services or views.

## Troubleshooting 🧭
- Vaadin toolchain errors → ensure Node download allowed.
- OCR missing text → verify `TESSDATA_PREFIX` and Mongo availability.
- Storage invisible → check `active` flag and required fields (bucket/root path).
- Permission dialogs stale → confirm DB connections; background tasks rely on live UI sessions.

## License 📜
Internal use only. Contact the ECM team before redistributing.
