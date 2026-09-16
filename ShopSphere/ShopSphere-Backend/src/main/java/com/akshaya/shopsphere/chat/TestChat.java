package com.akshaya.shopsphere.chat;

import com.akshaya.shopsphere.address.AddressRepository;
import com.akshaya.shopsphere.address.AddressService;
import com.akshaya.shopsphere.address.IAddressRepository;
import com.akshaya.shopsphere.cart.CartRepository;
import com.akshaya.shopsphere.cart.CartService;
import com.akshaya.shopsphere.cart.ICartRepository;
import com.akshaya.shopsphere.category.CategoryRepository;
import com.akshaya.shopsphere.category.CategoryService;
import com.akshaya.shopsphere.category.ICategoryRepository;
import com.akshaya.shopsphere.common.IPasswordService;
import com.akshaya.shopsphere.common.PasswordService;
import com.akshaya.shopsphere.discount.DiscountRepository;
import com.akshaya.shopsphere.discount.DiscountService;
import com.akshaya.shopsphere.discount.IDiscountRepository;
import com.akshaya.shopsphere.order.IOrderRepository;
import com.akshaya.shopsphere.order.OrderRepository;
import com.akshaya.shopsphere.order.OrderService;
import com.akshaya.shopsphere.product.IProductBatchRepository;
import com.akshaya.shopsphere.product.IProductRepository;
import com.akshaya.shopsphere.product.ProductBatchRepository;
import com.akshaya.shopsphere.product.ProductBatchService;
import com.akshaya.shopsphere.product.ProductRepository;
import com.akshaya.shopsphere.product.ProductService;
import com.akshaya.shopsphere.registration.IRegistrationRepository;
import com.akshaya.shopsphere.registration.RegistrationRepository;
import com.akshaya.shopsphere.registration.RegistrationService;

public class TestChat {
    public static void main(String[] args) {
        try {
            IPasswordService passwordService = new PasswordService();
            IRegistrationRepository registrationRepository = new RegistrationRepository(passwordService);
            RegistrationService registrationService = new RegistrationService(registrationRepository);

            IProductRepository productRepository = new ProductRepository();
            ProductService productService = new ProductService(productRepository);

            ICategoryRepository categoryRepository = new CategoryRepository();
            CategoryService categoryService = new CategoryService(categoryRepository);

            ICartRepository cartRepository = new CartRepository();
            CartService cartService = new CartService(cartRepository, productRepository);

            IAddressRepository addressRepository = new AddressRepository();
            AddressService addressService = new AddressService(addressRepository);

            IDiscountRepository discountRepository = new DiscountRepository();
            DiscountService discountService = new DiscountService(discountRepository);
            IProductBatchRepository productBatchRepository = new ProductBatchRepository();
            IOrderRepository orderRepository = new OrderRepository();
            OrderService orderService = new OrderService(
                    orderRepository, cartRepository, productRepository, discountService,
                    productBatchRepository, addressRepository
            );

            ProductBatchService productBatchService = new ProductBatchService(productBatchRepository);

            CommonChatTools commonTools = new CommonChatTools(categoryService, productService, discountService);
            GuestChatTools guestTools = new GuestChatTools(registrationService);
            AuthChatTools authTools = new AuthChatTools(cartService, addressService, orderService, productService);
            AdminChatTools adminTools = new AdminChatTools(productBatchService, discountService);

            AIChatService guestAiService = new AIChatService(commonTools, guestTools);
            System.out.println("Testing guestAiService...");
            String reply = guestAiService.chat("testSession", "guest", "Hi");
            System.out.println("Reply: " + reply);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
