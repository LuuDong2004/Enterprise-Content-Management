package com.vn.ecm.security;

import com.vn.ecm.entity.User;
import io.jmix.oidc.usermapper.SynchronizingOidcUserMapper;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.security.UserRepository;
import io.jmix.oidc.claimsmapper.ClaimsRolesMapper;
import io.jmix.security.role.RoleGrantedAuthorityUtils;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
public class AppUserMapper extends SynchronizingOidcUserMapper<User> {

    public AppUserMapper(UnconstrainedDataManager dataManager, UserRepository userRepository, ClaimsRolesMapper claimsRolesMapper, RoleGrantedAuthorityUtils roleGrantedAuthorityUtils) {
        super(dataManager, userRepository, claimsRolesMapper, roleGrantedAuthorityUtils);
    }

    @Override
    protected Class<User> getApplicationUserClass() {
        return User.class;
    }

    @Override
    protected void populateUserAttributes(OidcUser oidcUser, User jmixUser) {
        jmixUser.setUsername(getOidcUserUsername(oidcUser));
        jmixUser.setFirstName(oidcUser.getGivenName());
        jmixUser.setLastName(oidcUser.getFamilyName());
        jmixUser.setEmail(oidcUser.getEmail());
    }

    @Override
    protected String getOidcUserUsername(OidcUser oidcUser) {
        return oidcUser.getPreferredUsername();
    }

}
