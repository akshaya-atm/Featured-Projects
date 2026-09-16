package com.akshaya.shopsphere.profile;

import java.time.LocalDate;

public record ProfileResponse(
        String name,
        String email,
        String contact,
        LocalDate birthday
) {
}
