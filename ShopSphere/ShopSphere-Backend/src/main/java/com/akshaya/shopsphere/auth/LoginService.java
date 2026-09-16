package com.akshaya.shopsphere.auth;

import com.akshaya.shopsphere.common.IPasswordService;

import java.sql.SQLException;

public class LoginService {
    private IPasswordService passwordService;
    private ILoginRepository loginRepository;

    public LoginService(IPasswordService passwordService, ILoginRepository loginRepository) {
        this.passwordService = passwordService;
        this.loginRepository = loginRepository;
    }

    public UserAuthInfo getAuthenticatedUser(String email, String password) throws SQLException, IllegalArgumentException {
        UserAuthInfo authInfo = loginRepository.getAuthInfoForEmail(email);
        if (authInfo != null && passwordService.checkPassword(password, authInfo.passwordHash())) {
            return authInfo;
        }
        return null;
    }

    public boolean authenticate(String email, String password) throws SQLException, IllegalArgumentException {
        return getAuthenticatedUser(email, password) != null;
    }

    public int login(String email, String password) throws SQLException, IllegalArgumentException {
        UserAuthInfo authInfo = getAuthenticatedUser(email, password);
        return authInfo != null ? authInfo.userId() : -1;
    }
}
