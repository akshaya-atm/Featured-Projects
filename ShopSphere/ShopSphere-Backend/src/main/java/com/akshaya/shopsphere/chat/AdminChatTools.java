package com.akshaya.shopsphere.chat;

import com.akshaya.shopsphere.discount.Discount;
import com.akshaya.shopsphere.discount.DiscountService;
import com.akshaya.shopsphere.product.ProductBatchService;
import com.akshaya.shopsphere.product.SaleCandidate;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class AdminChatTools {
    private final ProductBatchService productBatchService;
    private final DiscountService discountService;

    public AdminChatTools(ProductBatchService productBatchService, DiscountService discountService) {
        this.productBatchService = productBatchService;
        this.discountService = discountService;
    }

    @Tool("ADMIN ONLY -- suggests which products should be put on sale, based on stock that's " +
            "expiring soon while still sitting on significant quantity (the classic 'this will " +
            "expire and we still have a lot of it, discount it to move stock' retail signal). " +
            "Refuses for anyone who isn't logged in as an admin -- never usable by customers or guests.")
    public String suggestProductsForSale(){
        if (!CurrentUser.isAdmin()) {
            return "This is an admin-only feature. Log in with an admin account to use it.";
        }
        try {
            List<SaleCandidate> candidates = productBatchService.getSaleCandidates(3, 10, 5);
            if (candidates.isEmpty()) {
                return "No sale candidates right now -- nothing is both expiring within 3 days " +
                        "and still holding 10+ units of stock.";
            }
            StringBuilder result = new StringBuilder();
            result.append("Top sale candidates (expiring within 3 days, 10+ units still in stock):\n\n");
            for (SaleCandidate candidate : candidates) {
                result.append("Product: ").append(candidate.getProductName())
                        .append(" (ProductID: ").append(candidate.getProductId()).append(")\n")
                        .append("Current Price: Rs ").append(candidate.getPrice()).append("\n")
                        .append("Stock Remaining: ").append(candidate.getAvailableQuantity()).append(" units\n")
                        .append("Expires: ").append(candidate.getExpiryDate()).append("\n\n");
            }
            return result.toString();
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (SQLException e) {
            return "Database Error: could not run the sale-candidate analysis right now.";
        }
    }

    @Tool("ADMIN ONLY -- Returns a list of all discounts in the system, including active, future, and expired ones.")
    public String getAllDiscounts() {
        if (!CurrentUser.isAdmin()) return "This is an admin-only feature.";
        try {
            List<Discount> discounts = discountService.getAllDiscounts();
            if (discounts.isEmpty()) return "No discounts exist in the system.";
            
            StringBuilder result = new StringBuilder("All Discounts:\n\n");
            for (Discount d : discounts) {
                result.append("DiscountID: ").append(d.getDiscountId()).append("\n")
                      .append("Name: ").append(d.getName()).append("\n")
                      .append("Type: ").append(d.getDiscountType()).append("\n")
                      .append("Value: ").append(d.getDiscountValue()).append("\n")
                      .append("Target Scope: ").append(d.getTargetScope()).append("\n")
                      .append("Valid From: ").append(d.getValidFrom()).append("\n")
                      .append("Valid Until: ").append(d.getValidUntil()).append("\n");
                
                if (d.getMinOrderAmount() != null) result.append("Min Order: ").append(d.getMinOrderAmount()).append("\n");
                if (d.getTargetPincode() != null && !d.getTargetPincode().isEmpty()) result.append("Pincode: ").append(d.getTargetPincode()).append("\n");
                if (d.getProductId() != null) result.append("ProductID: ").append(d.getProductId()).append("\n");
                if (d.isBirthdayOnly()) result.append("Birthday Only: true\n");
                result.append("\n");
            }
            return result.toString();
        } catch (SQLException e) {
            return "Database Error: Could not fetch discounts.";
        }
    }

    @Tool("ADMIN ONLY -- Adds a new discount. Required: name, discountType (PERCENTAGE/FLAT_AMOUNT), discountValue, targetScope (PRODUCT/AREA/MIN_ORDER/BIRTHDAY/CUSTOMER), validFrom (YYYY-MM-DD), validUntil (YYYY-MM-DD). Optional parameters must be explicitly passed as empty strings if not needed: minOrderAmount, targetPincode, productId, targetUserId, isBirthdayOnly (true/false).")
    public String addDiscount(
            @P("name") String name,
            @P("discountType") String discountType,
            @P("discountValue") String discountValue,
            @P("targetScope") String targetScope,
            @P("validFrom") String validFrom,
            @P("validUntil") String validUntil,
            @P("minOrderAmount") String minOrderAmount,
            @P("targetPincode") String targetPincode,
            @P("productId") String productId,
            @P("targetUserId") String targetUserId,
            @P("isBirthdayOnly") String isBirthdayOnly
    ) {
        if (!CurrentUser.isAdmin()) return "This is an admin-only feature.";
        try {
            Discount d = new Discount();
            d.setName(name);
            d.setDiscountType(discountType);
            d.setDiscountValue(new BigDecimal(discountValue));
            d.setTargetScope(targetScope);
            
            d.setValidFrom(LocalDate.parse(validFrom).atStartOfDay());
            d.setValidUntil(LocalDate.parse(validUntil).atTime(23, 59, 59));
            
            if (minOrderAmount != null && !minOrderAmount.trim().isEmpty()) d.setMinOrderAmount(new BigDecimal(minOrderAmount));
            if (targetPincode != null && !targetPincode.trim().isEmpty()) d.setTargetPincode(targetPincode);
            if (productId != null && !productId.trim().isEmpty()) d.setProductId(Integer.parseInt(productId));
            if (targetUserId != null && !targetUserId.trim().isEmpty()) d.setTargetUserId(Integer.parseInt(targetUserId));
            if (isBirthdayOnly != null && !isBirthdayOnly.trim().isEmpty()) d.setBirthdayOnly(Boolean.parseBoolean(isBirthdayOnly));
            
            int id = discountService.addDiscount(d, d.getProductId());
            return "Discount added successfully with ID: " + id;
        } catch (Exception e) {
            return "Error adding discount: " + e.getMessage();
        }
    }
    
    @Tool("ADMIN ONLY -- Updates an existing discount. Takes discountId and all parameters just like addDiscount. Pass empty strings for optional parameters to clear them.")
    public String updateDiscount(
            @P("discountId") String discountId,
            @P("name") String name,
            @P("discountType") String discountType,
            @P("discountValue") String discountValue,
            @P("targetScope") String targetScope,
            @P("validFrom") String validFrom,
            @P("validUntil") String validUntil,
            @P("minOrderAmount") String minOrderAmount,
            @P("targetPincode") String targetPincode,
            @P("productId") String productId,
            @P("targetUserId") String targetUserId,
            @P("isBirthdayOnly") String isBirthdayOnly
    ) {
        if (!CurrentUser.isAdmin()) return "This is an admin-only feature.";
        try {
            Discount d = new Discount();
            d.setDiscountId(Integer.parseInt(discountId));
            d.setName(name);
            d.setDiscountType(discountType);
            d.setDiscountValue(new BigDecimal(discountValue));
            d.setTargetScope(targetScope);
            
            d.setValidFrom(LocalDate.parse(validFrom).atStartOfDay());
            d.setValidUntil(LocalDate.parse(validUntil).atTime(23, 59, 59));
            
            if (minOrderAmount != null && !minOrderAmount.trim().isEmpty()) d.setMinOrderAmount(new BigDecimal(minOrderAmount));
            if (targetPincode != null && !targetPincode.trim().isEmpty()) d.setTargetPincode(targetPincode);
            if (productId != null && !productId.trim().isEmpty()) d.setProductId(Integer.parseInt(productId));
            if (targetUserId != null && !targetUserId.trim().isEmpty()) d.setTargetUserId(Integer.parseInt(targetUserId));
            if (isBirthdayOnly != null && !isBirthdayOnly.trim().isEmpty()) d.setBirthdayOnly(Boolean.parseBoolean(isBirthdayOnly));
            
            boolean updated = discountService.updateDiscount(d);
            return updated ? "Discount updated successfully." : "Failed to update discount.";
        } catch (Exception e) {
            return "Error updating discount: " + e.getMessage();
        }
    }

    @Tool("ADMIN ONLY -- Deletes a discount by its ID.")
    public String deleteDiscount(@P("discountId") String discountId) {
        if (!CurrentUser.isAdmin()) return "This is an admin-only feature.";
        try {
            boolean deleted = discountService.deleteDiscount(Integer.parseInt(discountId));
            return deleted ? "Discount deleted successfully." : "Failed to delete discount. It may not exist.";
        } catch (Exception e) {
            return "Error deleting discount: " + e.getMessage();
        }
    }
}
