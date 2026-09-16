package com.akshaya.shopsphere.registration;

import java.time.LocalDate;

public record RegistrationRequest(
        String name,
        String email,
        String contact,
        LocalDate birthday,
        String password
) {
}
