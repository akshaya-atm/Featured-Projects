package com.akshaya.shopsphere.chat;

import com.akshaya.shopsphere.category.Category;
import com.akshaya.shopsphere.category.CategoryService;
import com.akshaya.shopsphere.discount.Discount;
import com.akshaya.shopsphere.discount.DiscountService;
import com.akshaya.shopsphere.product.Product;
import com.akshaya.shopsphere.product.ProductService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.sql.SQLException;
import java.util.List;

public class CommonChatTools {
    private final CategoryService categoryService;
    private final ProductService productService;
    private final DiscountService discountService;

    public CommonChatTools(CategoryService categoryService, ProductService productService, DiscountService discountService) {
        this.categoryService = categoryService;
        this.productService = productService;
        this.discountService = discountService;
    }

    @Tool("Return the list of All available categories, its ID and description")
    public String searchCategory() {
        try {
            List<Category> categoryList = categoryService.getCategories();
            if(categoryList.isEmpty()){
                return "No categories found";
            }
            StringBuilder categories = new StringBuilder();
            for (Category category : categoryList) {
                    categories.append("CategoryID :").append(category.getCategoryId()).append("\n")
                    .append("CategoryName :").append(category.getCategoryName()).append("\n")
                    .append("CategoryDescription :").append(category.getCategoryDescription()).append("\n")
                    .append("Perishable :").append(category.isPerishable()).append("\n\n");
            }
            return categories.toString();

        } catch (SQLException e) {
            return "Technical Error:Couldn't find any products in the database.";
        }
    }

    @Tool("Returns the product catalog for a given categoryID, with each product's name, description, price, unit, and stock level")
    public String getProductsByCategory(
            @P("CategoryID of the products to look up") String categoryID
    ){
        int parsedCategoryId;
        try {
            parsedCategoryId = Integer.parseInt(categoryID);
            System.out.println(parsedCategoryId);
        } catch (NumberFormatException e) {
            return "That's not a valid category ID. Use searchCategory to see valid category IDs first.";
        }
        try {
            List<Product> productList = productService.getProductsByCategory(parsedCategoryId);
            if (productList.isEmpty()) {
                return "No products found in that category.";
            }
            StringBuilder catalog = new StringBuilder();
            for (Product product : productList) {
                catalog.append("ProductID: ").append(product.getProductId()).append("\n");
                catalog.append("Name: ").append(product.getName()).append("\n");
                catalog.append("Unit: ").append(product.getUnitValue()).append("\n");
                catalog.append("Description: ").append(product.getDescription()).append("\n");
                catalog.append("Price: ₹").append(product.getPrice()).append("\n");
                if (product.getDiscountedPrice() != null) {
                    catalog.append("Discounted Price: ₹").append(product.getDiscountedPrice())
                           .append(" (-").append(product.getDiscountPercentage()).append("%)\n");
                }
                catalog.append("Stock: ").append(product.getTotalAvailableQuantity()).append(" available\n");
                catalog.append("\n");
            }
            return catalog.toString();

        } catch (SQLException e){
            return "Technical Error: Couldn't find any products in the database.";
        }

    }

    @Tool("Returns a list of all currently active discounts and sales in the store.")
    public String getActiveDiscounts() {
        try {
            List<Discount> activeDiscounts = discountService.getActiveDiscounts();
            if (activeDiscounts.isEmpty()) {
                return "There are no active discounts right now.";
            }
            StringBuilder result = new StringBuilder();
            for (Discount d : activeDiscounts) {
                result.append("Name: ").append(d.getName()).append("\n")
                      .append("Type: ").append(d.getDiscountType()).append("\n")
                      .append("Value: ").append(d.getDiscountValue());
                if ("PERCENTAGE".equalsIgnoreCase(d.getDiscountType())) {
                    result.append("%\n");
                } else {
                    result.append(" flat\n");
                }
                if (d.getMinOrderAmount() != null) {
                    result.append("Min Order Amount: Rs ").append(d.getMinOrderAmount()).append("\n");
                }
                if (d.getTargetPincode() != null && !d.getTargetPincode().trim().isEmpty()) {
                    result.append("Valid only for Pincode: ").append(d.getTargetPincode()).append("\n");
                }
                if (d.isBirthdayOnly()) {
                    result.append("Note: This discount is only valid on your birthday!\n");
                }
                result.append("\n");
            }
            return result.toString();
        } catch (SQLException e) {
            return "Technical Error: Couldn't fetch active discounts right now.";
        }
    }
}
