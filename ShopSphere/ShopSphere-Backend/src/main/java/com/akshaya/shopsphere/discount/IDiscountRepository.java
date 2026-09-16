package com.akshaya.shopsphere.discount;

import java.sql.SQLException;
import java.util.List;

public interface IDiscountRepository {
    int addDiscount(Discount discount) throws SQLException;
    boolean linkProductDiscount(int productId, int discountId) throws SQLException;
    // Replaces the product a discount is linked to; pass null to clear the link entirely
    void relinkProductDiscount(int discountId, Integer productId) throws SQLException;
    boolean updateDiscount(Discount discount) throws SQLException;
    boolean deleteDiscount(int discountId) throws SQLException;
    List<Discount> getCurrentlyActiveDiscounts() throws SQLException;
    List<Discount> getAllDiscounts() throws SQLException;
    Discount getDiscountById(int discountId) throws SQLException;
    List<Discount> getActiveProductDiscounts(int productId) throws SQLException;
    List<Discount> getActiveOrderDiscounts() throws SQLException;
}
