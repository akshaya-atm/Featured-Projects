package com.akshaya.shopsphere.discount;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class DiscountService {
    private IDiscountRepository discountRepository;

    public DiscountService(IDiscountRepository discountRepository) {
        this.discountRepository = discountRepository;
    }

    public int addDiscount(Discount discount, Integer productId) throws SQLException {
        if (discount.getName() == null || discount.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Discount name is required");
        }
        if (discount.getDiscountValue() == null || discount.getDiscountValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Discount value must be greater than zero");
        }
        if (discount.getValidFrom() == null || discount.getValidUntil() == null) {
            throw new IllegalArgumentException("Valid from and valid until dates are required");
        }
        if (discount.getValidUntil().isBefore(discount.getValidFrom())) {
            throw new IllegalArgumentException("Valid until date must be after valid from date");
        }

        int discountId = discountRepository.addDiscount(discount);
        if (discountId > 0 && "PRODUCT".equalsIgnoreCase(discount.getTargetScope()) && productId != null && productId > 0) {
            discountRepository.linkProductDiscount(productId, discountId);
        }
        return discountId;
    }

    public boolean updateDiscount(Discount discount) throws SQLException {
        if (discount.getDiscountId() <= 0) {
            throw new IllegalArgumentException("Invalid discount ID");
        }
        if (discount.getName() == null || discount.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Discount name is required");
        }
        if (discount.getDiscountValue() == null || discount.getDiscountValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Discount value must be greater than zero");
        }
        if (discount.getValidFrom() == null || discount.getValidUntil() == null) {
            throw new IllegalArgumentException("Valid from and valid until dates are required");
        }
        if (discount.getValidUntil().isBefore(discount.getValidFrom())) {
            throw new IllegalArgumentException("Valid until date must be after valid from date");
        }
        // A PRODUCT-scope discount must actually name a product, same as at creation
        if ("PRODUCT".equalsIgnoreCase(discount.getTargetScope()) &&
                (discount.getProductId() == null || discount.getProductId() <= 0)) {
            throw new IllegalArgumentException("Please select which product this discount applies to");
        }

        boolean updated = discountRepository.updateDiscount(discount);
        if (updated) {
            // Resync the product link both ways so a discount edited away from PRODUCT doesn't leave a stale link
            Integer linkedProductId = "PRODUCT".equalsIgnoreCase(discount.getTargetScope()) ? discount.getProductId() : null;
            discountRepository.relinkProductDiscount(discount.getDiscountId(), linkedProductId);
        }
        return updated;
    }

    public boolean deleteDiscount(int discountId) throws SQLException {
        if (discountId <= 0) {
            throw new IllegalArgumentException("Invalid discount ID");
        }
        return discountRepository.deleteDiscount(discountId);
    }

    public List<Discount> getAllDiscounts() throws SQLException {
        return discountRepository.getAllDiscounts();
    }

    // Public endpoint -- only currently-active discounts are shown, never future/expired ones
    public List<Discount> getActiveDiscounts() throws SQLException {
        return discountRepository.getCurrentlyActiveDiscounts();
    }

    // Evaluate Cart-level Order Discounts for Checkout
    public List<OrderDiscount> evaluateOrderDiscounts(BigDecimal orderSubtotal, String userPincode, LocalDate userBirthday) throws SQLException {
        List<OrderDiscount> applicable = new ArrayList<>();
        List<Discount> activeDiscounts = discountRepository.getActiveOrderDiscounts();

        for (Discount d : activeDiscounts) {
            boolean isEligible = true;

            if (d.getMinOrderAmount() != null && orderSubtotal.compareTo(d.getMinOrderAmount()) < 0) {
                isEligible = false;
            }

            if (isEligible && d.getTargetPincode() != null && !d.getTargetPincode().equalsIgnoreCase(userPincode)) {
                isEligible = false;
            }

            if (isEligible && d.isBirthdayOnly()) {
                if (userBirthday == null || !(userBirthday.getMonth() == LocalDate.now().getMonth() && userBirthday.getDayOfMonth() == LocalDate.now().getDayOfMonth())) {
                    isEligible = false;
                }
            }

            if (isEligible) {
                BigDecimal savings;
                if ("PERCENTAGE".equalsIgnoreCase(d.getDiscountType())) {
                    savings = orderSubtotal.multiply(d.getDiscountValue().divide(new BigDecimal(100)));
                } else {
                    savings = d.getDiscountValue();
                }
                applicable.add(new OrderDiscount(d.getDiscountId(), d.getName(), d.getDiscountType(), d.getDiscountValue(), savings));
            }
        }
        return applicable;
    }
}
