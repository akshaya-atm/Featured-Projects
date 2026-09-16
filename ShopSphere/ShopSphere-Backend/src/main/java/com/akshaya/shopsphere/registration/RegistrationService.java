package com.akshaya.shopsphere.registration;

import java.sql.SQLException;
import java.time.LocalDate;

public class RegistrationService {

    private final IRegistrationRepository registrationRepository;

    public RegistrationService(IRegistrationRepository registrationRepository) {
        this.registrationRepository = registrationRepository;
    }

    public boolean registerUser(RegistrationRequest user) throws SQLException {
        validateUserCredentials(user);
        return registrationRepository.registerUser(user);
    }

    public void validateUserCredentials(RegistrationRequest user){
        com.akshaya.shopsphere.common.UserValidationUtil.validateName(user.name());
        com.akshaya.shopsphere.common.UserValidationUtil.validateContact(user.contact());
        com.akshaya.shopsphere.common.UserValidationUtil.validatePassword(user.password());
        com.akshaya.shopsphere.common.UserValidationUtil.validateEmail(user.email());
        com.akshaya.shopsphere.common.UserValidationUtil.validateBirthday(user.birthday());
    }
}
