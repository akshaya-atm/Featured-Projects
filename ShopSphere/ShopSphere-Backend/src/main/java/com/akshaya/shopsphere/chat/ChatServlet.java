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
import com.akshaya.shopsphere.common.JwtUtil;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

@WebServlet("/chat")
public class ChatServlet extends HttpServlet {
    private AIChatService guestAiService;
    private AIChatService userAiService;
    private AIChatService adminAiService;
    @Override
    public void init() throws ServletException {
        super.init();
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

        this.guestAiService = new AIChatService(commonTools, guestTools);
        this.userAiService = new AIChatService(commonTools, authTools);
        this.adminAiService = new AIChatService(commonTools, authTools, adminTools);
    }
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
       response.setContentType("application/json");
       response.setCharacterEncoding("UTF-8");
       try {
           // /chat is deliberately not behind UserAuthFilter (guests must be able to use the
           // assistant too), so unlike CartServlet etc. there's no request attribute already
           // populated for us -- we resolve the JWT ourselves, the same way
           // resolveConversationKey below already does for the memory key. userType comes along
           // for the ride here too, so admin-only tools (see ChatTools.suggestProductsForSale)
           // can be gated in code via CurrentUser.isAdmin(), the same server-verified-JWT trust
           // boundary as everything else in this class -- never trusted from the model/customer.
           Claims claims = extractClaims(request);
           Integer userId = claims != null ? claims.get("userId", Integer.class) : null;
           String userType = claims != null ? claims.get("userType", String.class) : null;
           System.out.println("[DEBUG ChatServlet] Authorization header = " + request.getHeader("Authorization"));
           System.out.println("[DEBUG ChatServlet] resolved userId = " + userId + ", userType = " + userType);
           CurrentUser.set(userId, userType);
           ObjectMapper mapper = new ObjectMapper();
           Map<?, ?> body = mapper.readValue(request.getReader(), Map.class);
           if (body == null) {
               response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
               mapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Request body is required"));
               return;
           }
           Object rawMsg = body.get("message");
           Object rawSessionId = body.get("sessionId");
           if (rawMsg == null || rawSessionId == null) {
               response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
               mapper.writeValue(response.getWriter(), Map.of("success", false, "message", "Both message and sessionId are required"));
               return;
           }
           String message = rawMsg.toString();
           String guestSessionId = rawSessionId.toString();
           Object rawUserName = body.get("userName");
           String userName = (rawUserName != null) ? rawUserName.toString() : "a guest who hasn't logged in yet";
           
           try {
               CurrentUser.set(userId, userType);

               String conversationKey = (userId != null) ? "user:" + userId : "guest:" + guestSessionId;
               String reply;
               if (CurrentUser.isAdmin()) {
                   reply = adminAiService.chat(conversationKey, userName + " (Store Admin)", message);
               } else if (userId != null) {
                   reply = userAiService.chat(conversationKey, userName, message);
               } else {
                   reply = guestAiService.chat(conversationKey, userName, message);
               }

               response.setStatus(HttpServletResponse.SC_OK);
               mapper.writeValue(response.getWriter(), Map.of("success", true, "reply", reply));
           } finally {
               CurrentUser.clear();
           }

       } finally {
           CurrentUser.clear();
       }

    }
    private Claims extractClaims(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                Claims claims = JwtUtil.validateAndExtractClaims(authHeader.substring(7));
                System.out.println("[DEBUG ChatServlet] JWT valid, userId claim = " + claims.get("userId", Integer.class)
                        + ", userType claim = " + claims.get("userType", String.class));
                return claims;
            } catch (Exception e) {
                System.out.println("[DEBUG ChatServlet] JWT validation failed: " + e);
            }
        } else {
            System.out.println("[DEBUG ChatServlet] no Bearer token present on request");
        }
        return null;
    }
}
