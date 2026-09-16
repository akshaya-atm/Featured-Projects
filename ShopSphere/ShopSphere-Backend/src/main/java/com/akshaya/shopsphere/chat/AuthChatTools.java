package com.akshaya.shopsphere.chat;

import com.akshaya.shopsphere.address.Address;
import com.akshaya.shopsphere.address.AddressService;
import com.akshaya.shopsphere.cart.CartItem;
import com.akshaya.shopsphere.cart.CartService;
import com.akshaya.shopsphere.order.Order;
import com.akshaya.shopsphere.order.OrderItem;
import com.akshaya.shopsphere.order.OrderService;
import com.akshaya.shopsphere.order.PaymentMethod;
import com.akshaya.shopsphere.product.Product;
import com.akshaya.shopsphere.product.ProductService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.sql.SQLException;
import java.util.List;

public class AuthChatTools {
    private final CartService cartService;
    private final AddressService addressService;
    private final OrderService orderService;
    private final ProductService productService;

    public AuthChatTools(CartService cartService, AddressService addressService, OrderService orderService, ProductService productService) {
        this.cartService = cartService;
        this.addressService = addressService;
        this.orderService = orderService;
        this.productService = productService;
    }

    @Tool("Adds a product to the current logged-in customer's cart, using the product's ID (from " +
            "getProductsByCategory) and the desired quantity. Never ask the user for their identity " +
            "or user ID -- it's already known from their session.")
    public String addProductToCart(
        @P("productID")String productID,
        @P("quantity")String quantity){
        Integer userId = CurrentUser.getUserId();
        System.out.println("[DEBUG ChatTools] addProductToCart called, CurrentUser.getUserId() = " + userId);
        if(userId==null){
            return "User is not logged in(Cannot add items to cart). Request to login ";
        }

        int productId;
        try{
             productId = Integer.parseInt(productID);
        } catch (NumberFormatException e) {
            return "ProductID error: "+ e.getMessage();
        }
        int quantityInt;
        try{
             quantityInt = Integer.parseInt(quantity);
        } catch (NumberFormatException e) {
            return "Quantity error: "+ e.getMessage();
        }

        try{
            cartService.addItemToCart(new CartItem(productId,quantityInt),CurrentUser.getUserId());
        } catch (IllegalArgumentException e) {
            // Product not found, or requested quantity exceeds stock -- CartService already
            // writes these in customer-friendly language, so hand it straight to the model
            // instead of letting it escape as an uncaught exception.
            System.out.println("[DEBUG ChatTools] addItemToCart rejected: " + e.getMessage());
            return e.getMessage();
        } catch (SQLException e) {
            return "Database Error: could not add items to the cart";
        }
        return "Successfully added product to cart";

    }

    @Tool("Removes a product entirely from the logged-in customer's cart, using the product's ID " +
            "(from getAllItemsInCart or getProductsByCategory). This removes the whole line, " +
            "regardless of how many units are in the cart -- there's no partial-quantity removal.")
    public String removeProductFromCart(
            @P("productID") String productID
    ){
        Integer userId = CurrentUser.getUserId();
        if(userId==null){
            return "User is not logged in. Request to login to update the cart.";
        }
        int productId;
        try{
            productId = Integer.parseInt(productID);
        } catch (NumberFormatException e) {
            return "ProductID error: "+ e.getMessage();
        }
        try {
            // A quantity of 0 tells CartRepository.removeItemFromCart to delete the whole cart
            // line for this product outright, rather than decrementing by some amount -- exactly
            // the "remove this item" semantics chat needs, without having to know how many are
            // currently in the cart first.
            boolean removed = cartService.removeItemFromCart(new CartItem(productId, 0), userId);
            return removed ? "Removed from cart." : "That item wasn't in the cart.";
        } catch (SQLException e) {
            return "Database Error: could not update the cart";
        }
    }

    @Tool("Gets all items currently in the logged-in customer's cart, with product name, quantity, " +
            "and price -- use this to summarize and confirm the order with the customer before calling placeOrder.")
    public String getAllItemsInCart(){
        Integer userId = CurrentUser.getUserId();
        if(userId==null){
            return "User is not logged in. Request to login to view cart items.";
        }
        try {
            List<CartItem> cartItems = cartService.getCartItemsForUser(userId);
            if (cartItems.isEmpty()) {
                return "Cart is empty.";
            }
            StringBuilder items = new StringBuilder();
            for (CartItem cartItem : cartItems) {
                Product product = productService.getProductById(cartItem.getProductId());
                String name = product != null ? product.getName() : ("Product #" + cartItem.getProductId());
                items.append("ProductID: ").append(cartItem.getProductId()).append("\n")
                     .append("Name: ").append(name).append("\n")
                     .append("Quantity: ").append(cartItem.getQuantity()).append("\n");
                if (product != null) {
                    java.math.BigDecimal price = product.getDiscountedPrice() != null ? product.getDiscountedPrice() : product.getPrice();
                    items.append("Price: Rs ").append(price).append("\n");
                }
                items.append("\n");
            }
            return items.toString();
        } catch (SQLException e){
            return "Database Error: could not get items from cart";
        }
    }

    @Tool("Lists the logged-in customer's saved delivery addresses (house/street, city, state, " +
            "pincode, and whether it's their default). Use this to let the customer choose or " +
            "confirm a delivery address before calling placeOrder -- never guess or make up an address.")
    public String getMyAddresses(){
        Integer userId = CurrentUser.getUserId();
        if(userId==null){
            return "User is not logged in. Request to login to view saved addresses.";
        }
        try {
            List<Address> addresses = addressService.getAddressesForUser(userId);
            if (addresses.isEmpty()) {
                return "No saved addresses found. Ask the customer for their delivery address details " +
                        "so one can be added first, since placing an order requires one on file.";
            }
            StringBuilder result = new StringBuilder();
            for (Address address : addresses) {
                result.append("AddressID: ").append(address.getAddressId()).append("\n")
                        .append(address.getHouseNo()).append(", ").append(address.getStreet()).append("\n");
                if (address.getLandmark() != null && !address.getLandmark().isEmpty()) {
                    result.append("Landmark: ").append(address.getLandmark()).append("\n");
                }
                result.append(address.getCity()).append(", ").append(address.getState())
                        .append(" - ").append(address.getPincode()).append("\n")
                        .append("Default: ").append(address.isDefaultAddress()).append("\n\n");
            }
            return result.toString();
        } catch (SQLException e) {
            return "Database Error: could not get saved addresses";
        }
    }

    @Tool("Saves a new delivery address for the logged-in customer. street, city, and pincode are " +
            "required; houseNo, landmark, and state are optional (pass an empty string if not given). " +
            "If this is the customer's first saved address it automatically becomes their default. " +
            "Use getMyAddresses first to check whether the customer already has an address before " +
            "asking them to give you one again.")
    public String addAddress(
            @P("House/flat number, or empty string if not given") String houseNo,
            @P("Street") String street,
            @P("Landmark, or empty string if not given") String landmark,
            @P("City") String city,
            @P("State, or empty string if not given") String state,
            @P("Pincode") String pincode
    ){
        Integer userId = CurrentUser.getUserId();
        if(userId==null){
            return "User is not logged in. Request to login to add an address.";
        }
        Address address = new Address(0, userId, houseNo, street, landmark, city, state, pincode);
        try {
            int addressId = addressService.addAddress(address);
            if (addressId == 0) {
                return "Failed to save the address. Please try again.";
            }
            return "Address saved successfully.";
        } catch (IllegalArgumentException e) {
            // Missing street/city/pincode -- AddressService already writes these in
            // customer-friendly language.
            return e.getMessage();
        } catch (SQLException e) {
            return "Database Error: could not save the address";
        }
    }

    @Tool("Places an order for everything currently in the logged-in customer's cart, shipping to " +
            "the given delivery address (its AddressID, from getMyAddresses). Chat orders are always " +
            "Cash on Delivery. This is a real, irreversible action. " +
            "CRITICAL: Set userHasConfirmed to true ONLY if you have ALREADY shown the user their final cart total and delivery address, AND the user's very last message was an explicit 'yes' or 'confirm'. " +
            "If the user just asked to place an order but hasn't seen the total yet, pass userHasConfirmed=false to get the summary, show it to them, and wait for them to say yes.")
    public String placeOrder(
            @P("AddressID to ship to, from getMyAddresses") String addressID,
            @P("true ONLY if the user has already seen the final summary and explicitly said 'yes' to it. Otherwise false.") boolean userHasConfirmed
    ){
        Integer userId = CurrentUser.getUserId();
        System.out.println("[DEBUG ChatTools] placeOrder called, CurrentUser.getUserId() = " + userId + ", confirmed=" + userHasConfirmed);
        if(userId==null){
            return "User is not logged in. Request to login to place an order.";
        }
        
        if (!userHasConfirmed) {
            return "SYSTEM ACTION REQUIRED: Do not place the order yet. Tell the user the total amount and the address it will be shipped to, and explicitly ask them: 'Are you ready to confirm this order?'. You must wait for their reply before calling this tool again with userHasConfirmed=true.";
        }

        int addressId;
        try {
            addressId = Integer.parseInt(addressID);
        } catch (NumberFormatException e) {
            return "AddressID error: that's not a valid address ID. Use getMyAddresses to find the right one.";
        }

        try {
            // Chat checkout is Cash on Delivery only -- there's no real payment gateway wired up
            // in this project (see OrderService.performCancellation's refund comment), so we never
            // let the model pass a payment method through at all, rather than pretending to accept
            // one it can't actually process. No order-level discount wired up from chat yet either
            // (selectedDiscountId = null) -- OrderService still applies stock checks, address
            // ownership validation, etc. exactly as it does for the regular checkout flow.
            Order order = orderService.placeOrder(userId, addressId, PaymentMethod.CASH_ON_DELIVERY, null);
            return "Order #" + order.getOrderId() + " placed successfully (Cash on Delivery). Total: Rs " + order.getFinalAmount();
        } catch (IllegalArgumentException e) {
            // Empty cart, address not found/not owned by this user, out of stock, etc. --
            // OrderService already writes these in customer-friendly language.
            System.out.println("[DEBUG ChatTools] placeOrder rejected: " + e.getMessage());
            return e.getMessage();
        } catch (SQLException e) {
            return "Database Error: could not place the order right now.";
        }
    }

    @Tool("Gets the logged-in customer's order history -- each order's ID, status, payment status, " +
            "total, and items. Use this to answer questions like 'where's my order' or 'what did I " +
            "order last time', and to find the OrderID needed for cancelOrder.")
    public String getMyOrders(){
        Integer userId = CurrentUser.getUserId();
        if(userId==null){
            return "User is not logged in. Request to login to view orders.";
        }
        try {
            List<Order> orders = orderService.getUserOrders(userId);
            if (orders.isEmpty()) {
                return "No orders found.";
            }
            StringBuilder result = new StringBuilder();
            for (Order order : orders) {
                result.append("OrderID: ").append(order.getOrderId()).append("\n")
                        .append("Date: ").append(order.getCreatedAt()).append("\n")
                        .append("AddressID: ").append(order.getAddressId()).append("\n")
                        .append("Status: ").append(order.getStatus()).append("\n")
                        .append("Payment Method: ").append(order.getPaymentMethod()).append("\n")
                        .append("Payment Status: ").append(order.getPaymentStatus()).append("\n")
                        .append("Total: Rs ").append(order.getFinalAmount()).append("\n")
                        .append("Items:\n");
                for (OrderItem item : order.getOrderItems()) {
                    result.append("  - ").append(item.getProductName())
                            .append(" (ProductID: ").append(item.getProductId()).append(")")
                            .append(" x").append(item.getQuantity())
                            .append(" @ Rs ").append(item.getPricePerUnit()).append(" each\n");
                }
                result.append("\n");
            }
            return result.toString();
        } catch (SQLException e) {
            return "Database Error: could not get order history";
        }
    }

    @Tool("Cancels one of the logged-in customer's own orders, using its OrderID (from getMyOrders). " +
            "Only works while the order is still in PLACED status (hasn't started processing/shipping " +
            "yet) -- OrderService will reject it otherwise. This is irreversible, so always confirm " +
            "with the customer which order and that they want to cancel it before calling this.")
    public String cancelOrder(
            @P("OrderID to cancel, from getMyOrders") String orderID
    ){
        Integer userId = CurrentUser.getUserId();
        if(userId==null){
            return "User is not logged in. Request to login to cancel an order.";
        }
        int orderId;
        try {
            orderId = Integer.parseInt(orderID);
        } catch (NumberFormatException e) {
            return "OrderID error: that's not a valid order ID. Use getMyOrders to find the right one.";
        }
        try {
            orderService.cancelOrderByUser(userId, orderId);
            return "Order #" + orderId + " has been cancelled.";
        } catch (IllegalArgumentException e) {
            // Not found / not this user's order / already past PLACED -- OrderService already
            // writes these in customer-friendly language.
            return e.getMessage();
        } catch (SQLException e) {
            return "Database Error: could not cancel the order right now.";
        }
    }
}
