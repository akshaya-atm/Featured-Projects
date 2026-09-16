package com.akshaya.shopsphere.order;

import java.time.LocalDate;

// Minimal user data needed to evaluate order-level discounts (pincode/area, birthday) at checkout.
public record UserOrderContext(String pincode, LocalDate birthday) {
}
