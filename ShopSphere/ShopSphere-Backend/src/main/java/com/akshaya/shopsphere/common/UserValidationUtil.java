package com.akshaya.shopsphere.common;

import java.time.LocalDate;

public class UserValidationUtil {

    public static void validateName(String name) {
        if (name == null || name.length() < 3) {
            throw new IllegalArgumentException("Name cannot be less than 3 characters");
        }
    }

    public static void validateContact(String contact) {
        if (contact == null || contact.trim().length() < 10) {
            throw new IllegalArgumentException("Contact cannot be less than 10 numbers");
        }
    }

    public static void validatePassword(String password) {
        if (password == null || !password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[!@#$%]).{8,}$")) {
            throw new IllegalArgumentException("Password must be at least 8 characters long and contain at least one uppercase letter, one lowercase letter, one number, and one special character (!@#$%).");
        }
    }

    public static void validateEmail(String email) {
        if (email == null || !email.matches("^[a-zA-Z0-9+._-]+@[a-zA-Z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new IllegalArgumentException("Enter a valid email address");
        }
    }

    public static void validateBirthday(LocalDate birthday) {
        if (birthday == null) {
            throw new IllegalArgumentException("Birthday is required");
        }
        LocalDate today = LocalDate.now();
        LocalDate earliestAllowedBirthday = today.minusYears(100);
        LocalDate latestAllowedBirthday = today.minusYears(18);
        if (birthday.isBefore(earliestAllowedBirthday) || birthday.isAfter(latestAllowedBirthday)) {
            throw new IllegalArgumentException("Birthday must be between 18 years and 100 years");
        }
    }
}
