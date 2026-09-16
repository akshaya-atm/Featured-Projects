package com.akshaya.shopsphere.common;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

public interface IPasswordService {
    String hashPassword(String password) throws NoSuchAlgorithmException, InvalidKeySpecException;

    boolean checkPassword(String password, String storedHash);
}
