package com.akshaya.shopsphere.common;

import org.mindrot.jbcrypt.BCrypt;

public class PasswordService implements IPasswordService {

    @Override
    public String hashPassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty");
        }
        return BCrypt.hashpw(password, BCrypt.gensalt(10));
    }

    @Override
    public boolean checkPassword(String password, String storedHash) {
        if (password == null || storedHash == null || storedHash.isEmpty()) {
            return false;
        }
        try {
            return BCrypt.checkpw(password, storedHash);
        } catch (Exception e) {
            return false;
        }
    }
}
