package com.akshaya.shopsphere.auth;

import java.sql.SQLException;

public interface ILoginRepository {
    String getPasswordHashforEmail(String email) throws SQLException;
    UserAuthInfo getAuthInfoForEmail(String email) throws SQLException;
}

