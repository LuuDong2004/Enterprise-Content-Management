package com.vn.ecm.listener;

import com.vn.ecm.entity.Folder;
import io.jmix.core.event.EntityChangedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * @deprecated Không còn cần thiết vì đã bỏ closure table.
 *             Listener này giữ lại để tương thích ngược nhưng không làm gì.
 *             Có thể xóa hoàn toàn sau khi đảm bảo không còn reference nào.
 */
@Deprecated
@Component
public class FolderParentChangeListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFolderChanged(EntityChangedEvent<Folder> event) {
        // Không còn cần rebuild closure table nữa vì đã dùng FULL_PATH
        // Method này giữ lại để tương thích ngược nhưng không làm gì
    }
}
