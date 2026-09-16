package com.akshaya.shopsphere.registration;

import java.sql.SQLException;

public interface IRegistrationRepository {
    boolean registerUser(RegistrationRequest user) throws SQLException;
}
