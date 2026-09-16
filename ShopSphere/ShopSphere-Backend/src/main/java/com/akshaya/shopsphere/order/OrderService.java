package com.akshaya.shopsphere.order;

import com.akshaya.shopsphere.address.Address;
import com.akshaya.shopsphere.address.IAddressRepository;
import com.akshaya.shopsphere.cart.CartItem;
import com.akshaya.shopsphere.cart.ICartRepository;
import com.akshaya.shopsphere.discount.DiscountService;
import com.akshaya.shopsphere.discount.OrderDiscount;
import com.akshaya.shopsphere.product.IProductBatchRepository;
import com.akshaya.shopsphere.product.IProductRepository;
import com.akshaya.shopsphere.product.Product;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class OrderService {
    private final IOrderRepository orderRepository;
    private final ICartRepository cartRepository;
    private final IProductRepository productRepository;
    private final DiscountService discountService;
    private final IProductBatchRepository productBatchRepository;
    private final IAddressRepository addressRepository;

    public OrderService(IOrderRepository orderRepository, ICartRepository cartRepository, IProductRepository productRepository, DiscountService discountService, IProductBatchRepository productBatchRepository, IAddressRepository addressRepository) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.discountService = discountService;
        this.productBatchRepository = productBatchRepository;
        this.addressRepository = addressRepository;
    }

    public Order placeOrder(int userId, Integer addressId, PaymentMethod paymentMethod, Integer selectedDiscountId) throws SQLException {
        int cartId = cartRepository.findCartIdByUserId(userId);
        if (cartId == 0) {
            throw new IllegalArgumentException("No active cart found for user");
        }

        List<CartItem> cartItems = cartRepository.getCartItemsByCartId(cartId);
        if (cartItems.isEmpty()) {
            throw new IllegalArgumentException("Cart is empty");
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (CartItem cartItem : cartItems) {
            Product product = productRepository.getProductById(cartItem.getProductId());
            if (product == null) {
                continue;
            }

            // Enforce stock availability server-side -- client-side checks alone can be bypassed
            if (cartItem.getQuantity() > product.getTotalAvailableQuantity()) {
                throw new IllegalArgumentException(
                        "Insufficient stock for '" + product.getName() + "': only " +
                                product.getTotalAvailableQuantity() + " unit(s) available.");
            }

            BigDecimal unitPrice = product.getDiscountedPrice() != null ? product.getDiscountedPrice() : product.getPrice();
            BigDecimal itemTotal = unitPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            totalAmount = totalAmount.add(itemTotal);

            orderItems.add(new OrderItem(
                    product.getProductId(),
                    product.getName(),
                    cartItem.getQuantity(),
                    unitPrice
            ));
        }

        // A delivery address is mandatory -- also used below so the area/pincode discount reflects real delivery location
        if (addressId == null) {
            throw new IllegalArgumentException("Please select a delivery address before placing your order.");
        }
        Address deliveryAddress = addressRepository.getAddressById(addressId);
        if (deliveryAddress == null || deliveryAddress.getUserId() != userId) {
            throw new IllegalArgumentException("Selected delivery address was not found.");
        }

        // Order-level discounts (birthday / pincode-area / min-order) are mutually exclusive --
        // only the one the customer selected at checkout is applied, never stacked.
        BigDecimal discountAmount = BigDecimal.ZERO;
        UserOrderContext userContext = orderRepository.getUserOrderContext(userId);
        List<OrderDiscount> applicableDiscounts = discountService.evaluateOrderDiscounts(
                totalAmount, deliveryAddress.getPincode(), userContext.birthday());
        if (selectedDiscountId != null) {
            for (OrderDiscount discount : applicableDiscounts) {
                if (discount.getDiscountId() == selectedDiscountId) {
                    discountAmount = discount.getCalculatedSavings() != null ? discount.getCalculatedSavings() : BigDecimal.ZERO;
                    break;
                }
            }
            // If it doesn't match a currently-eligible discount, discountAmount stays ZERO -- never trust a client-supplied amount
        }
        // Safety net: never let a discount exceed the order subtotal.
        if (discountAmount.compareTo(totalAmount) > 0) {
            discountAmount = totalAmount;
        }
        BigDecimal finalAmount = totalAmount.subtract(discountAmount);

        // Deduction is atomic per-product (see decrementStock), but not atomic across the whole
        // order -- if a later item fails, restore what was already deducted before failing.
        List<OrderItem> decrementedItems = new ArrayList<>();
        for (OrderItem item : orderItems) {
            int quantityToDeduct = item.getQuantity();
            boolean deducted = productBatchRepository.decrementStock(item.getProductId(), quantityToDeduct);
            if (!deducted) {
                for (OrderItem alreadyDeducted : decrementedItems) {
                    productBatchRepository.restoreStock(alreadyDeducted.getProductId(), alreadyDeducted.getQuantity(), "RESTORE");
                }
                throw new IllegalArgumentException(
                        "'" + item.getProductName() + "' just sold out. Please update your cart and try again.");
            }
            decrementedItems.add(item);
        }

        Order order = new Order();
        order.setUserId(userId);
        order.setAddressId(addressId);
        order.setTotalAmount(totalAmount);
        order.setDiscountAmount(discountAmount);
        order.setFinalAmount(finalAmount);
        order.setStatus(OrderStatus.PLACED);
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setPaymentMethod(paymentMethod != null ? paymentMethod : PaymentMethod.CASH_ON_DELIVERY);

        int orderId = orderRepository.createOrder(order);
        order.setOrderId(orderId);

        orderRepository.createOrderItems(orderId, orderItems);
        order.setOrderItems(orderItems);

        cartRepository.clearCart(cartId);

        return order;
    }

    public List<Order> getUserOrders(int userId) throws SQLException {
        return orderRepository.getOrdersByUserId(userId);
    }

    // Preview eligible order-level discounts for the checkout picker. addressId may be null
    // before the customer picks one -- area/pincode discounts just won't match anything yet.
    public List<OrderDiscount> getEligibleOrderDiscounts(int userId, BigDecimal subtotal, Integer addressId) throws SQLException {
        UserOrderContext userContext = orderRepository.getUserOrderContext(userId);
        String pincode = null;
        if (addressId != null) {
            Address address = addressRepository.getAddressById(addressId);
            if (address != null && address.getUserId() == userId) {
                pincode = address.getPincode();
            }
        }
        return discountService.evaluateOrderDiscounts(subtotal, pincode, userContext.birthday());
    }

    public List<Order> getAllOrders() throws SQLException {
        return orderRepository.getAllOrders();
    }

    public boolean updateOrderStatus(int orderId, OrderStatus status, PaymentStatus paymentStatus) throws SQLException {
        return orderRepository.updateOrderStatus(orderId, status, paymentStatus);
    }

    // Customer-initiated cancellation — only allowed while the order is still PLACED (hasn't
    // started processing/shipping yet), and only for the requesting customer's own order.
    public void cancelOrderByUser(int userId, int orderId) throws SQLException {
        Order order = orderRepository.getOrderById(orderId);
        if (order == null || order.getUserId() != userId) {
            throw new IllegalArgumentException("Order not found");
        }
        if (order.getStatus() != OrderStatus.PLACED) {
            throw new IllegalArgumentException("This order can no longer be cancelled — it is already " + order.getStatus() + ".");
        }
        performCancellation(order);
    }

    // Admin-initiated cancellation — allowed only up through PROCESSING, i.e. before the order
    // has physically left the warehouse; anything shipped/delivered must go through a separate
    // returns flow instead, since restoring stock here would credit inventory that isn't actually back.
    public void cancelOrderByAdmin(int orderId) throws SQLException {
        Order order = orderRepository.getOrderById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("Order not found");
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return;
        }
        if (order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.DELIVERED) {
            throw new IllegalArgumentException("Order #" + orderId + " has already shipped and can no " +
                    "longer be cancelled — that would restock inventory that isn't actually back in the " +
                    "warehouse. Process it as a return instead.");
        }
        performCancellation(order);
    }

    // Restores stock (logged as CANCELLATION, distinct from the RESTORE type used for checkout
    // rollback) and marks the order CANCELLED; a PAID order auto-flips to REFUNDED for bookkeeping.
    private void performCancellation(Order order) throws SQLException {
        for (OrderItem item : order.getOrderItems()) {
            productBatchRepository.restoreStock(item.getProductId(), item.getQuantity(), "CANCELLATION");
        }
        PaymentStatus newPaymentStatus = order.getPaymentStatus() == PaymentStatus.PAID
                ? PaymentStatus.REFUNDED
                : order.getPaymentStatus();
        orderRepository.updateOrderStatus(order.getOrderId(), OrderStatus.CANCELLED, newPaymentStatus);
    }
}
