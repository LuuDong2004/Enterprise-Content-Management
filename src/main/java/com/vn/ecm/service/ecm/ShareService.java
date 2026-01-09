package com.vn.ecm.service.ecm;

import com.vn.ecm.entity.*;
import io.jmix.core.DataManager;
import io.jmix.core.security.CurrentAuthentication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

@Service
public class ShareService {

    private final DataManager dataManager;
    private final PermissionService permissionService;
    private final CurrentAuthentication currentAuthentication;

    @Autowired(required = false)
    private MailSender mailSender;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    @Value("${ecm.share.base-url:http://localhost:8080}")
    private String baseUrl;

    public ShareService(DataManager dataManager,
            PermissionService permissionService,
            CurrentAuthentication currentAuthentication) {
        this.dataManager = dataManager;
        this.permissionService = permissionService;
        this.currentAuthentication = currentAuthentication;
    }

    /**
     * Tạo share link cho folder hoặc file
     */
    @Transactional
    public ShareLink createShareLink(Folder folder, FileDescriptor file,
            String recipientEmail,
            LocalDateTime expiryDate,
            String message) {
        if (folder == null && file == null) {
            throw new IllegalArgumentException("Folder hoặc File phải được chỉ định");
        }

        User currentUser = (User) currentAuthentication.getUser();
        if (currentUser == null) {
            throw new IllegalStateException("User chưa đăng nhập");
        }

        // Kiểm tra quyền của owner
        if (folder != null) {
            if (!permissionService.hasPermission(currentUser, PermissionType.READ, folder)) {
                throw new SecurityException("Bạn không có quyền chia sẻ folder này");
            }
        } else {
            if (!permissionService.hasPermission(currentUser, PermissionType.READ, file)) {
                throw new SecurityException("Bạn không có quyền chia sẻ file này");
            }
        }

        // Tạo token duy nhất
        String token = generateUniqueToken();

        // Tạo ShareLink
        ShareLink shareLink = dataManager.create(ShareLink.class);
        shareLink.setToken(token);
        shareLink.setFolder(folder);
        shareLink.setFile(file);
        shareLink.setOwner(currentUser);
        shareLink.setRecipientEmail(recipientEmail);
        shareLink.setCreatedDate(LocalDateTime.now());
        shareLink.setExpiryDate(expiryDate);
        shareLink.setPermissionType("READ"); // Mặc định chỉ READ
        shareLink.setMessage(message);
        shareLink.setActive(true);
        shareLink.setAccessCount(0L);

        return dataManager.save(shareLink);
    }

    /**
     * Lấy ShareLink theo token
     */
    @Transactional
    public ShareLink getShareLinkByToken(String token) {
        return dataManager.load(ShareLink.class)
                .query("select s from ShareLink s where s.token = :token")
                .parameter("token", token)
                .optional()
                .orElse(null);
    }

    /**
     * Validate và tăng access count
     */
    @Transactional
    public ShareLink validateAndAccess(String token) {
        ShareLink shareLink = getShareLinkByToken(token);
        if (shareLink == null) {
            throw new IllegalArgumentException("Link chia sẻ không hợp lệ");
        }

        if (!shareLink.isValid()) {
            throw new IllegalStateException("Link chia sẻ đã hết hạn hoặc đã bị vô hiệu hóa");
        }

        // Tăng access count
        shareLink.setAccessCount(shareLink.getAccessCount() + 1);
        return dataManager.save(shareLink);
    }

    /**
     * Tạo permission cho recipient khi truy cập link chia sẻ
     */
    @Transactional
    public void grantSharePermission(ShareLink shareLink, User recipient) {
        if (shareLink == null || recipient == null) {
            return;
        }

        PermissionType permissionType = PermissionType.READ; // Mặc  địnhREAD
        if ("MODIFY".equals(shareLink.getPermissionType())) {
            permissionType = PermissionType.MODIFY;
        } else if ("FULL".equals(shareLink.getPermissionType())) {
            permissionType = PermissionType.FULL;
        }

        // Tạo permission cho recipient (chỉ trên chính đối tượng, không cascade)
        if (shareLink.getFolder() != null) {
            Permission existing = permissionService.loadPermission(recipient, shareLink.getFolder());
            if (existing == null) {
                Permission perm = dataManager.create(Permission.class);
                perm.setUser(recipient);
                perm.setFolder(shareLink.getFolder());
                perm.setPermissionMask(permissionType.getValue());
                perm.setInherited(false);
                perm.setInheritEnabled(false); // không cho kế thừa tiếp
                perm.setAppliesTo(AppliesTo.THIS_FOLDER_ONLY); // chỉ folder này
                dataManager.save(perm);
            } else {
                int currentMask = Optional.ofNullable(existing.getPermissionMask()).orElse(0);
                int newMask = permissionType.getValue();
                if (newMask > currentMask) {
                    existing.setPermissionMask(newMask);
                    existing.setInheritEnabled(false);
                    existing.setAppliesTo(AppliesTo.THIS_FOLDER_ONLY);
                    dataManager.save(existing);
                }
            }
        } else if (shareLink.getFile() != null) {
            Permission existing = permissionService.loadPermission(recipient, shareLink.getFile());
            if (existing == null) {
                Permission perm = dataManager.create(Permission.class);
                perm.setUser(recipient);
                perm.setFile(shareLink.getFile());
                perm.setPermissionMask(permissionType.getValue());
                perm.setInherited(false);
                perm.setInheritEnabled(false);
                perm.setAppliesTo(AppliesTo.THIS_FOLDER_ONLY);
                dataManager.save(perm);
            } else {
                int currentMask = Optional.ofNullable(existing.getPermissionMask()).orElse(0);
                int newMask = permissionType.getValue();
                if (newMask > currentMask) {
                    existing.setPermissionMask(newMask);
                    existing.setInheritEnabled(false);
                    existing.setAppliesTo(AppliesTo.THIS_FOLDER_ONLY);
                    dataManager.save(existing);
                }
            }
        }
    }

    /**
     * Tạo permission request
     */
    @Transactional
    public SharePermissionRequest createPermissionRequest(ShareLink shareLink,
            String requesterEmail,
            String requestedPermission,
            String message) {
        SharePermissionRequest request = dataManager.create(SharePermissionRequest.class);
        request.setShareLink(shareLink);
        request.setRequesterEmail(requesterEmail);
        request.setRequestedPermission(requestedPermission);
        request.setMessage(message);
        request.setStatus("PENDING");
        request.setCreatedDate(LocalDateTime.now());
        return dataManager.save(request);
    }

    /**
     * Xử lý permission request (approve/reject)
     */
    @Transactional
    public void processPermissionRequest(SharePermissionRequest request, boolean approve) {
        User currentUser = (User) currentAuthentication.getUser();
        request.setStatus(approve ? "APPROVED" : "REJECTED");
        request.setRespondedDate(LocalDateTime.now());
        request.setRespondedBy(currentUser);
        dataManager.save(request);

        if (approve) {
            // Cập nhật permission của share link
            ShareLink shareLink = request.getShareLink();
            shareLink.setPermissionType(request.getRequestedPermission());
            dataManager.save(shareLink);

            // Tìm user theo email và grant permission
            User recipient = findUserByEmail(request.getRequesterEmail());
            if (recipient != null) {
                grantSharePermission(shareLink, recipient);
            }
        }
    }

    /**
     * Tìm user theo email
     */
    @Transactional
    public User findUserByEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return dataManager.load(User.class)
                .query("select u from User u where u.email = :email")
                .parameter("email", email)
                .optional()
                .orElse(null);
    }

    /**
     * Tạo URL chia sẻ
     */
    public String generateShareUrl(ShareLink shareLink) {
        String url = baseUrl;
        if (contextPath != null && !contextPath.isEmpty()) {
            url += contextPath;
        }
        return url + "/share/" + shareLink.getToken();
    }

    /**
     * Gửi email với link chia sẻ
     */
    public void sendShareEmail(ShareLink shareLink, String recipientEmail) {
        if (mailSender == null || recipientEmail == null || recipientEmail.isBlank()) {
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(recipientEmail);
            message.setSubject("Chia sẻ dữ liệu từ " + shareLink.getOwner().getDisplayName());

            String shareUrl = generateShareUrl(shareLink);
            String itemName = shareLink.getFolder() != null
                    ? shareLink.getFolder().getName()
                    : shareLink.getFile().getName();

            StringBuilder emailBody = new StringBuilder();
            emailBody.append("Xin chào,\n\n");
            emailBody.append(shareLink.getOwner().getDisplayName())
                    .append(" đã chia sẻ với bạn: ").append(itemName).append("\n\n");

            if (shareLink.getMessage() != null && !shareLink.getMessage().isBlank()) {
                emailBody.append("Lời nhắn: ").append(shareLink.getMessage()).append("\n\n");
            }

            emailBody.append("Truy cập link: ").append(shareUrl).append("\n\n");
            emailBody.append(
                    "Lưu ý: Bạn chỉ có quyền xem. Nếu cần quyền chỉnh sửa hoặc toàn quyền, vui lòng gửi yêu cầu qua link trên.\n\n");
            emailBody.append("Trân trọng,\n");
            emailBody.append("Hệ thống ECM");

            message.setText(emailBody.toString());
            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("Không thể gửi email: " + e.getMessage(), e);
        }
    }
    /**
     * Generate unique token
     */
    private String generateUniqueToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        // Đảm bảo token là duy nhất
        while (getShareLinkByToken(token) != null) {
            secureRandom.nextBytes(randomBytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        }

        return token;
    }
}
