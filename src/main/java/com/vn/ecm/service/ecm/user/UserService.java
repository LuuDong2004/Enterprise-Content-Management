package com.vn.ecm.service.ecm.user;

import com.vn.ecm.entity.User;
import io.jmix.core.DataManager;
import io.jmix.core.security.CurrentAuthentication;
import org.springframework.stereotype.Service;
@Service
public class UserService {
    private final CurrentAuthentication currentAuthentication;
    private final DataManager dataManager;

    public UserService(CurrentAuthentication currentAuthentication, DataManager dataManager) {
        this.currentAuthentication = currentAuthentication;
        this.dataManager = dataManager;
    }

    public User getCurrentUser() {
        String username = currentAuthentication.getUser().getUsername();

        return dataManager.load(User.class)
                .query("select u from User u where u.username = :username")
                .parameter("username", username)
                .one();
    }


}
