package com.vn.ecm.listener;

import com.vn.ecm.entity.Folder;
import com.vn.ecm.service.ecm.PermissionService;
import io.jmix.core.event.EntityChangedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Component
public class FolderParentChangeListener {

    private final PermissionService permissionService;

    public FolderParentChangeListener(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFolderChanged(EntityChangedEvent<Folder> event) {
        if (event.getType() != EntityChangedEvent.Type.UPDATED) {
            return;
        }
        if (!event.getChanges().isChanged("parent")) {
            return;
        }
        UUID folderId = (UUID) event.getEntityId().getValue();
        permissionService.updateFolderClosureForMove(folderId);
    }
}
