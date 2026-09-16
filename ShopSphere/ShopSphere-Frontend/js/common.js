// ShopSphere Common Navigation, JWT Auth & Cart Drawer Utility

const BASE_API_URL = "http://localhost:8080/ShopSphere-Backend";


// Chat conversation id -- generated once per page load, sent explicitly with every /chat
// request so the backend can tell one conversation from another without relying on cookies
// (Safari blocks the JSESSIONID one cross-site here) or the JWT (guests can chat too, no login required).
const chatSessionId = crypto.randomUUID();

// Cart drawer discount-picker & delivery-address state — reset every time the drawer re-renders
let cartItemsSubtotal = 0;
let selectedOrderDiscountId = null;
let selectedAddressId = null;
let userAddressesList = [];

function getToken() {
    return localStorage.getItem("token");
}

function getUserName() {
    return localStorage.getItem("userName") || "User";
}

function getUserType() {
    return localStorage.getItem("userType") || "CUSTOMER";
}

function isLoggedIn() {
    return !!getToken();
}

function logout() {
    localStorage.removeItem("token");
    localStorage.removeItem("userName");
    localStorage.removeItem("userType");
    localStorage.removeItem("userBirthday");
    window.location.href = "index.html";
}

async function authFetch(url, options = {}) {
    const token = getToken();
    if (!options.headers) {
        options.headers = {};
    }
    if (token) {
        options.headers["Authorization"] = "Bearer " + token;
    }
    const response = await fetch(url, options);
    if (response.status === 401) {
        console.warn("Session expired or user unauthorized. Logging out...");
        logout();
        throw new Error("Unauthorized");
    }
    return response;
}

// Inject Navbar and Render Universal Auth State
function initCommonComponents() {
    injectNavbar();
    injectLoginModal();
    injectCartDrawer();
    injectChatBotDrawer();
    injectChatBotFab();
}

// Function to adjust item quantity directly from the cart drawer
async function updateCartDrawerQty(productId, change) {
    if (!isLoggedIn()) return;
    try {
        const method = change > 0 ? "POST" : "DELETE";
        const response = await authFetch(`${BASE_API_URL}/cart`, {
            method: method,
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ productId: productId, quantity: Math.abs(change) })
        });
        const result = await response.json();
        if (response.ok && result.success) {
            await refreshCartBadge();
            if (typeof cartQuantities !== 'undefined') {
                if (change > 0) cartQuantities[productId] = (cartQuantities[productId] || 0) + 1;
                else if (cartQuantities[productId] > 0) cartQuantities[productId] -= 1;
                if (cartQuantities[productId] === 0) delete cartQuantities[productId];
                if (typeof filterAndRenderProducts === 'function') filterAndRenderProducts();
            }
            const qtyEl = document.getElementById(`cart-qty-${productId}`);
            const lineEl = document.getElementById(`cart-line-${productId}`);
            const itemEl = document.getElementById(`cart-item-${productId}`);
            if (qtyEl && lineEl && itemEl) {
                const newQty = parseInt(qtyEl.textContent) + change;
                if (newQty <= 0) {
                    itemEl.remove();
                    const bodyEl = document.getElementById("cartDrawerBody");
                    if (bodyEl && !bodyEl.querySelector('[id^="cart-item-"]')) {
                        await renderCartDrawerItemsFromDB();
                        return;
                    }
                } else {
                    qtyEl.textContent = newQty;
                    const unitPrice = parseFloat(itemEl.dataset.price);
                    lineEl.textContent = `₹${(unitPrice * newQty).toFixed(2)}`;
                }
                cartItemsSubtotal = 0;
                document.querySelectorAll('[id^="cart-item-"]').forEach(el => {
                    const p = parseFloat(el.dataset.price);
                    const q = parseInt(el.querySelector('[id^="cart-qty-"]').textContent);
                    cartItemsSubtotal += p * q;
                });
                await refreshOrderOffers();
            } else {
                await renderCartDrawerItemsFromDB();
            }
        } else {
            showToast(result.message || "Failed to update item", "error");
        }
    } catch (e) {
        showToast("Error updating item", "error");
    }
}

if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initCommonComponents);
} else {
    initCommonComponents();
}

function injectNavbar() {
    const header = document.querySelector(".navbar");
    if (!header) return;

    header.innerHTML = `
        <div class="nav-brand">
          <a href="index.html" class="brand-link">
            <span class="logo-icon">🛍️</span>
            <span class="logo-text">ShopSphere</span>
          </a>
        </div>
        
        <nav class="nav-links">
          <a href="index.html" class="nav-link">Home</a>
          <a href="products.html" class="nav-link">Catalog</a>
         

          <!-- Guest Only -->
          <a href="login.html" class="nav-link guest-only">Login</a>
          <a href="registration.html" class="nav-link nav-link-highlight guest-only">Register</a>

          <!-- User Only -->
          <button class="nav-link btn-cart-trigger user-only" onclick="toggleCartDrawer()" style="background: none; border: none; cursor: pointer; font-size: inherit; font-family: inherit; position: relative;">🛒 Cart <span id="cartBadgeCount" style="display: none; position: absolute; top: -6px; right: -10px; background: #ef4444; color: white; font-size: 0.7rem; font-weight: 800; width: 18px; height: 18px; border-radius: 50%; align-items: center; justify-content: center;">0</span></button>
          <a href="profile.html" class="nav-link user-only">Profile</a>
          <a href="orders.html" class="nav-link user-only">My Orders</a>
          <a href="admin.html" class="nav-link admin-only" style="color: #f59e0b; font-weight: 700;">Admin Dashboard</a>
          <span class="user-welcome user-only" style="font-size: 0.9rem; color: #334155;">Hi, <strong id="navUserName">User</strong></span>
          <button class="btn-logout user-only" onclick="logout()" style="padding: 0.35rem 0.75rem; background: #ef4444; color: white; border: none; border-radius: 6px; font-weight: 600; cursor: pointer;">Log Out</button>
        </nav>
    `;

    updateNavbarState();
}

function updateNavbarState() {
    const loggedIn = isLoggedIn();
    const isAdmin = getUserType() === "ADMIN";

    // Toggle Visibility of Guest vs User Elements
    document.querySelectorAll(".guest-only").forEach(el => {
        el.style.display = loggedIn ? "none" : "inline-flex";
    });

    document.querySelectorAll(".user-only").forEach(el => {
        el.style.display = loggedIn ? "inline-flex" : "none";
    });

    document.querySelectorAll(".admin-only").forEach(el => {
        el.style.display = (loggedIn && isAdmin) ? "inline-flex" : "none";
    });

    if (loggedIn) {
        const nameEl = document.getElementById("navUserName");
        if (nameEl) nameEl.textContent = getUserName();
    }
}

// Inject Global Login Modal Popup
function injectLoginModal() {
    if (document.getElementById("globalLoginModalOverlay")) return;

    const modalHtml = `
        <div class="modal-overlay login-modal-overlay" id="globalLoginModalOverlay">
            <div class="login-modal-card">
                <button class="modal-close" onclick="closeLoginModal()" style="position: absolute; top: 1rem; right: 1rem; background: none; border: none; font-size: 1.5rem; cursor: pointer;">&times;</button>
                <div style="font-size: 2.5rem; margin-bottom: 0.5rem;">🔒</div>
                <h2 style="margin-bottom: 0.25rem;">Login Required</h2>
                <p style="color: #64748b; font-size: 0.9rem; margin-bottom: 1.5rem;">
                    Please log in to add items to your cart and place orders.
                </p>

                <form id="modalLoginForm">
                    <div style="margin-bottom: 1rem; text-align: left;">
                        <label style="display: block; font-size: 0.85rem; font-weight: 600; margin-bottom: 0.25rem;">Email Address</label>
                        <input type="email" id="modalEmail" placeholder="user@example.com" required style="width: 100%; padding: 0.6rem; border: 1px solid #cbd5e1; border-radius: 6px;">
                    </div>
                    <div style="margin-bottom: 1.5rem; text-align: left;">
                        <label style="display: block; font-size: 0.85rem; font-weight: 600; margin-bottom: 0.25rem;">Password</label>
                        <input type="password" id="modalPassword" placeholder="••••••••" required style="width: 100%; padding: 0.6rem; border: 1px solid #cbd5e1; border-radius: 6px;">
                    </div>
                    <div id="modalLoginMsg" style="margin-bottom: 1rem; font-size: 0.85rem; color: #ef4444;"></div>
                    <button type="submit" style="width: 100%; padding: 0.75rem; background: #10b981; color: white; border: none; border-radius: 6px; font-weight: 700; cursor: pointer; font-size: 1rem;">
                        Login & Continue
                    </button>
                </form>

                <p style="margin-top: 1rem; font-size: 0.85rem; color: #64748b;">
                    Don't have an account? <a href="registration.html" style="color: #10b981; font-weight: 600;">Register here</a>
                </p>
            </div>
        </div>
    `;

    document.body.insertAdjacentHTML("beforeend", modalHtml);

    document.getElementById("modalLoginForm").addEventListener("submit", async (e) => {
        e.preventDefault();
        const email = document.getElementById("modalEmail").value;
        const password = document.getElementById("modalPassword").value;
        const msgEl = document.getElementById("modalLoginMsg");

        try {
            const response = await fetch(`${BASE_API_URL}/login`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ email, password })
            });

            const result = await response.json();
            if (!response.ok) throw new Error(result.message || "Login failed");

            localStorage.setItem("token", result.token);
            localStorage.setItem("userName", result.name || "Customer");
            localStorage.setItem("userType", result.userType || "CUSTOMER");

            closeLoginModal();
            updateNavbarState();

            if (window.pendingLoginCallback) {
                window.pendingLoginCallback();
                window.pendingLoginCallback = null;
            }
        } catch (err) {
            msgEl.textContent = describeError(err);
        }
    });
}

function openLoginModal(callback) {
    if (callback) window.pendingLoginCallback = callback;
    const overlay = document.getElementById("globalLoginModalOverlay");
    if (overlay) overlay.classList.add("open");
}

function closeLoginModal() {
    const overlay = document.getElementById("globalLoginModalOverlay");
    if (overlay) overlay.classList.remove("open");
}

function injectChatBotDrawer(){
    if(document.getElementById("globalChatBotDrawer")) return;
    const chatBotHtml = `
        <div class="modal-overlay chatbot-drawer-overlay" id="globalChatBotDrawer">
            <div class="chatbot-drawer-panel">
                <div class="chatbot-drawer-header">
                    <div class="chatbot-drawer-header-text">
                        <h2>🛍️ ShopSphere Assistant</h2>
                        <p class="chatbot-drawer-tagline">Hi! I can help you find products, manage your cart, and place orders.</p>
                    </div>
                    <button onclick="toggleChatBotDrawer()">&times;</button>
                </div>
                <div class="chatbot-drawer-body" id="chatBotMessages"></div>
                <form class="chatbot-drawer-footer" id="chatBotForm" onsubmit="handleChatBotSubmit(event)">
                    <input type="text" id="chatBotInput" placeholder="Ask me anything..." autocomplete="off">
                    <button type="submit">Send</button>
                </form>
            </div>
        </div>
    `;
    document.body.insertAdjacentHTML("beforeend", chatBotHtml);
}
// Floating, draggable chat trigger -- replaces the old navbar "💬 Assistant" link so the
// assistant is reachable from anywhere on any page, not just tucked into the nav. Defaults to
// the bottom-right corner; a click/tap opens the drawer, a click-and-hold-drag repositions it
// anywhere on screen instead (including further right/left), and the last dragged spot is
// remembered across reloads via localStorage.
function injectChatBotFab() {
    if (document.getElementById("chatBotFab")) return;

    const fab = document.createElement("button");
    fab.id = "chatBotFab";
    fab.className = "chatbot-fab";
    fab.type = "button";
    fab.textContent = "💬";
    fab.setAttribute("aria-label", "Open ShopSphere Assistant");
    document.body.appendChild(fab);

    // Restore a previously-dragged position, if any -- otherwise the CSS default (fixed
    // bottom-right) applies.
    let savedPos = null;
    try {
        savedPos = JSON.parse(localStorage.getItem("chatFabPosition") || "null");
    } catch (e) {
        savedPos = null;
    }
    if (savedPos && typeof savedPos.left === "number" && typeof savedPos.top === "number") {
        fab.style.left = savedPos.left + "px";
        fab.style.top = savedPos.top + "px";
        fab.style.right = "auto";
        fab.style.bottom = "auto";
    }

    let dragging = false;
    let moved = false;
    let startX, startY, startLeft, startTop;

    function getPoint(e) {
        return (e.touches && e.touches.length) ? e.touches[0] : e;
    }

    function onDragStart(e) {
        dragging = true;
        moved = false;
        const point = getPoint(e);
        const rect = fab.getBoundingClientRect();
        startX = point.clientX;
        startY = point.clientY;
        startLeft = rect.left;
        startTop = rect.top;

        // Switch to left/top-based positioning the instant a drag starts, so the button tracks
        // the pointer 1:1 no matter which corner it was anchored from.
        fab.style.left = startLeft + "px";
        fab.style.top = startTop + "px";
        fab.style.right = "auto";
        fab.style.bottom = "auto";
        fab.classList.add("dragging");

        document.addEventListener("mousemove", onDragMove);
        document.addEventListener("touchmove", onDragMove, { passive: false });
        document.addEventListener("mouseup", onDragEnd);
        document.addEventListener("touchend", onDragEnd);
    }

    function onDragMove(e) {
        if (!dragging) return;
        const point = getPoint(e);
        const dx = point.clientX - startX;
        const dy = point.clientY - startY;
        if (Math.abs(dx) > 4 || Math.abs(dy) > 4) moved = true;
        if (!moved) return;
        if (e.cancelable) e.preventDefault();

        const fabWidth = fab.offsetWidth;
        const fabHeight = fab.offsetHeight;
        let newLeft = startLeft + dx;
        let newTop = startTop + dy;
        // Keep it fully on-screen while dragging.
        newLeft = Math.max(4, Math.min(window.innerWidth - fabWidth - 4, newLeft));
        newTop = Math.max(4, Math.min(window.innerHeight - fabHeight - 4, newTop));

        fab.style.left = newLeft + "px";
        fab.style.top = newTop + "px";
    }

    function onDragEnd() {
        if (!dragging) return;
        dragging = false;
        fab.classList.remove("dragging");
        document.removeEventListener("mousemove", onDragMove);
        document.removeEventListener("touchmove", onDragMove);
        document.removeEventListener("mouseup", onDragEnd);
        document.removeEventListener("touchend", onDragEnd);

        if (moved) {
            // Was a drag, not a tap -- persist the new spot, don't also open the drawer.
            localStorage.setItem("chatFabPosition", JSON.stringify({
                left: parseFloat(fab.style.left),
                top: parseFloat(fab.style.top)
            }));
        } else {
            // Was a simple click/tap -- open the assistant, same as the old navbar button did.
            toggleChatBotDrawer();
        }
    }

    fab.addEventListener("mousedown", onDragStart);
    fab.addEventListener("touchstart", onDragStart, { passive: true });
}

function toggleChatBotDrawer() {
    const panel = document.getElementById("globalChatBotDrawer");
    if (panel){
        const isOpening = !panel.classList.contains("open");
        panel.classList.toggle("open");
        if (isOpening) {
            sendChatBotGreeting();
        }
    }

}

// Fetches a real, personalized opening line from the assistant itself (not a hardcoded string) --
// same /chat pipeline as a normal message, so it goes through the model's own system prompt and
// {{userName}}, just with a synthetic instruction instead of something the customer typed, and no
// user bubble shown for it. Only fires once per page load, the first time the drawer is opened.
let chatBotGreeted = false;
async function sendChatBotGreeting() {
    if (chatBotGreeted) return;
    chatBotGreeted = true;

    showTypingIndicator();
    try {
        const response = await authFetch(`${BASE_API_URL}/chat`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                message: "Greet me by in one short, friendly sentence as a shopping assisant. Don't list your capabilities " +
                        "or introduce yourself in detail -- that's already shown in the drawer header.",
                sessionId: chatSessionId,
                userName: isLoggedIn() ? getUserName() : null
            })
        });
        const result = await response.json();
        removeTypingIndicator();
        if (response.ok) {
            appendChatBotMessage("bot", result.reply || "Hi! How can I help you today?");
        }
    } catch (error) {
        // Silent fail -- this is an automatic action the customer didn't ask for, so if it can't
        // be fetched (e.g. rate limited), just let them type first instead of greeting them with
        // an error before they've said anything.
        removeTypingIndicator();
        console.warn("[Chat Greeting] Could not fetch greeting:", error);
    }
}

async function handleChatBotSubmit(event){
    event.preventDefault();

    const input = document.getElementById("chatBotInput");
    const message = input.value.trim();
    if (!message) return;
    appendChatBotMessage("user", message);
    input.value = "";

    const sendBtn = document.querySelector("#chatBotForm button[type='submit']");
    if (sendBtn) sendBtn.disabled = true;
    showTypingIndicator();

    try{
        console.log("[DEBUG] token before /chat call:", getToken());
        const response = await authFetch(`${BASE_API_URL}/chat`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({message: message,sessionId:chatSessionId,userName: isLoggedIn() ? getUserName() : undefined})
        });
        const result = await response.json();
        if (!response.ok) {
            throw new Error(result.message || "The assistant couldn't process that.");
        }
        removeTypingIndicator();
        appendChatBotMessage("bot", result.reply || "I'm not sure how to respond to that yet.");
        refreshCartBadge();
        // Automatically re-render cart drawer in case the AI modified it while it's open
        if (typeof renderCartDrawerItemsFromDB === 'function') {
            renderCartDrawerItemsFromDB();
        }
    } catch (error) {
        removeTypingIndicator();
        appendChatBotMessage("bot", describeError(error));

    }finally {
        if(sendBtn) sendBtn.disabled = false;
    }

}

function appendChatBotMessage(role, message){
    const messagesEl = document.getElementById("chatBotMessages");
    const senderName = role === "user" ? (isLoggedIn() ? getUserName() : "You") : "ShopSphere Assistant";

    const group = document.createElement("div");
    group.className = `chat-message-group chat-message-group-${role}`;

    const label = document.createElement("div");
    label.className = "chat-sender-label";
    label.textContent = senderName;

    const bubble = document.createElement("div");
    bubble.className = `chat-bubble chat-bubble-${role}`;
    bubble.textContent = message;

    group.appendChild(label);
    group.appendChild(bubble);
    messagesEl.appendChild(group);
    messagesEl.scrollTop = messagesEl.scrollHeight;
}

// Shown while waiting on the /chat response -- styled as its own chat-bubble-bot so it sits in
// the message list exactly like a real bot reply would, just with bouncing dots instead of text.
function showTypingIndicator() {
    const messagesEl = document.getElementById("chatBotMessages");
    if (!messagesEl || document.getElementById("chatBotTypingIndicator")) return;
    const indicator = document.createElement("div");
    indicator.id = "chatBotTypingIndicator";
    indicator.className = "chat-bubble chat-bubble-bot typing-indicator";
    indicator.innerHTML = `<span class="typing-dot"></span><span class="typing-dot"></span><span class="typing-dot"></span>`;
    messagesEl.appendChild(indicator);
    messagesEl.scrollTop = messagesEl.scrollHeight;
}

function removeTypingIndicator() {
    const indicator = document.getElementById("chatBotTypingIndicator");
    if (indicator) indicator.remove();
}
// Inject Global Cart Drawer Popup
function injectCartDrawer() {
    if (document.getElementById("cartDrawerOverlay")) return;

    const drawerHtml = `
        <div class="modal-overlay cart-drawer-overlay" id="cartDrawerOverlay">
            <div class="cart-drawer-panel">
                
                <div class="cart-drawer-header">
                    <h2 style="font-size: 1.25rem; font-weight: 700; color: #0f172a; margin: 0;">🛒 Your Shopping Cart</h2>
                    <button onclick="toggleCartDrawer()" style="background: none; border: none; font-size: 1.5rem; cursor: pointer; color: #64748b;">&times;</button>
                </div>

                <div class="cart-drawer-body" id="cartDrawerBody">
                    <div style="text-align: center; color: #64748b; padding: 2rem;">
                        <p style="margin-bottom: 1rem;">Loading your active cart from database...</p>
                    </div>
                </div>

                <div class="cart-drawer-footer">
                    <div style="display: flex; justify-content: space-between; margin-bottom: 0.5rem; font-size: 0.9rem;">
                        <span style="color: #64748b;">Total Payable:</span>
                        <strong style="color: #10b981; font-size: 1.1rem;" id="cartTotalPayable">₹0.00</strong>
                    </div>
                    <div style="display: flex; justify-content: space-between; margin-bottom: 0.75rem; font-size: 0.85rem;">
                        <span style="color: #64748b;">Payment Method:</span>
                        <strong style="color: #0f172a;">Cash on Delivery (COD)</strong>
                    </div>
                    <button id="btnCheckoutSubmit" class="btn-checkout" onclick="submitCheckoutOrder()">
                        Place Order (Cash on Delivery) &rarr;
                    </button>
                </div>

            </div>
        </div>
    `;

    document.body.insertAdjacentHTML("beforeend", drawerHtml);
}


function toggleCartDrawer() {
    if (!isLoggedIn()) {
        openLoginModal();
        return;
    }
    const drawer = document.getElementById("cartDrawerOverlay");
    if (drawer) {
        const isOpening = !drawer.classList.contains("open");
        drawer.classList.toggle("open", isOpening);
        if (isOpening) {
            renderCartDrawerItemsFromDB();
        }
    }
}

// DB-backed badge refresh, safe to call from any page (unlike products.js's
// updateCartBadgeCount(), which sums a local cartQuantities object that only exists on
// products.html). Used after chat replies, since we can't tell from the AI's free-text reply
// alone whether that particular message actually touched the cart -- a cheap GET /cart re-sync
// is simpler and more reliable than trying to parse that out.
async function refreshCartBadge() {
    if (!isLoggedIn()) return;
    const badge = document.getElementById("cartBadgeCount");
    if (!badge) return;
    try {
        const token = getToken();
        const res = await authFetch(`${BASE_API_URL}/cart`, {
            headers: { "Authorization": "Bearer " + token }
        });
        const items = await res.json();
        const totalItems = Array.isArray(items) ? items.reduce((sum, item) => sum + item.quantity, 0) : 0;
        badge.textContent = totalItems;
        badge.style.display = totalItems > 0 ? "inline-flex" : "none";
    } catch (e) {
        console.warn("[Cart Badge] Could not refresh:", e);
    }
}

// Builds the "Delivery Address" section HTML from userAddressesList/selectedAddressId --
// re-used both on initial cart-drawer render and after adding a new address.
function buildAddressSectionHtml() {
    const savedHtml = userAddressesList.map(a => `
        <label style="display: flex; align-items: flex-start; gap: 0.5rem; padding: 0.5rem 0; cursor: pointer; font-size: 0.85rem;">
            <input type="radio" name="deliveryAddressChoice" value="${a.addressId}" style="margin-top: 0.2rem;"
                   ${a.addressId === selectedAddressId ? 'checked' : ''} onchange="onAddressSelected(this)">
            <span style="flex: 1;">
                ${a.houseNo ? a.houseNo + ', ' : ''}${a.street}${a.landmark ? ', ' + a.landmark : ''}, ${a.city}${a.state ? ', ' + a.state : ''} - ${a.pincode}
                ${a.defaultAddress
                    ? `<span style="color: #f59e0b; font-weight: 700; margin-left: 0.35rem;">★ Default</span>`
                    : `<button type="button" onclick="event.preventDefault(); event.stopPropagation(); setAddressAsDefault(${a.addressId})" style="margin-left: 0.35rem; background: none; border: none; color: #0284c7; font-size: 0.75rem; cursor: pointer; text-decoration: underline; padding: 0;">Set as default</button>`}
            </span>
        </label>
    `).join("");

    const emptyMsg = userAddressesList.length === 0
        ? `<p style="font-size: 0.8rem; color: #94a3b8; margin-bottom: 0.5rem;">No saved addresses yet — add one below to continue.</p>`
        : '';

    return `
        <div style="margin-top: 1rem; padding-top: 1rem; border-top: 1px dashed #cbd5e1;">
            <p style="font-size: 0.85rem; font-weight: 700; color: #334155; margin-bottom: 0.35rem;">Delivery Address</p>
            ${savedHtml}
            ${emptyMsg}
            <button type="button" onclick="toggleAddAddressForm()" id="btnToggleAddAddress"
                    style="background: none; border: none; color: #0284c7; font-weight: 600; font-size: 0.8rem; cursor: pointer; padding: 0.25rem 0;">
                + Add new address
            </button>
            <form id="addAddressForm" onsubmit="handleSaveNewAddress(event)"
                  style="display: ${userAddressesList.length === 0 ? 'block' : 'none'}; margin-top: 0.5rem; padding: 0.75rem; background: #f8fafc; border-radius: 8px;">
                <input type="text" id="newAddrHouseNo" placeholder="House / Flat No. (optional)" style="width: 100%; margin-bottom: 0.4rem; padding: 0.45rem; border: 1px solid #e2e8f0; border-radius: 6px; font-size: 0.8rem; box-sizing: border-box;">
                <input type="text" id="newAddrStreet" placeholder="Street *" required style="width: 100%; margin-bottom: 0.4rem; padding: 0.45rem; border: 1px solid #e2e8f0; border-radius: 6px; font-size: 0.8rem; box-sizing: border-box;">
                <input type="text" id="newAddrLandmark" placeholder="Landmark (optional)" style="width: 100%; margin-bottom: 0.4rem; padding: 0.45rem; border: 1px solid #e2e8f0; border-radius: 6px; font-size: 0.8rem; box-sizing: border-box;">
                <input type="text" id="newAddrCity" placeholder="City *" required style="width: 100%; margin-bottom: 0.4rem; padding: 0.45rem; border: 1px solid #e2e8f0; border-radius: 6px; font-size: 0.8rem; box-sizing: border-box;">
                <input type="text" id="newAddrState" placeholder="State (optional)" style="width: 100%; margin-bottom: 0.4rem; padding: 0.45rem; border: 1px solid #e2e8f0; border-radius: 6px; font-size: 0.8rem; box-sizing: border-box;">
                <input type="text" id="newAddrPincode" placeholder="Pincode *" required style="width: 100%; margin-bottom: 0.4rem; padding: 0.45rem; border: 1px solid #e2e8f0; border-radius: 6px; font-size: 0.8rem; box-sizing: border-box;">
                <label style="display: flex; align-items: center; gap: 0.4rem; margin-bottom: 0.5rem; font-size: 0.8rem; color: #334155; cursor: pointer;">
                    <input type="checkbox" id="newAddrIsDefault" style="margin: 0;">
                    Set as my default address
                </label>
                <div style="display: flex; gap: 0.5rem;">
                    <button type="submit" style="flex: 1; padding: 0.5rem; background: #10b981; color: white; border: none; border-radius: 6px; font-weight: 600; font-size: 0.8rem; cursor: pointer;">Save Address</button>
                    ${userAddressesList.length > 0 ? `<button type="button" onclick="toggleAddAddressForm()" style="flex: 1; padding: 0.5rem; background: #e2e8f0; border: none; border-radius: 6px; font-weight: 600; font-size: 0.8rem; cursor: pointer;">Cancel</button>` : ''}
                </div>
            </form>
        </div>
    `;
}

function toggleAddAddressForm() {
    const form = document.getElementById("addAddressForm");
    if (form) form.style.display = (form.style.display === "none") ? "block" : "none";
}

// Marks an existing saved address as the customer's default (clears it on any other address of
// theirs server-side), also selects it for the CURRENT order since re-fetching offers depends
// on whichever address ends up selected.
async function setAddressAsDefault(addressId) {
    const token = getToken();
    try {
        const res = await authFetch(`${BASE_API_URL}/addresses/default`, {
            method: "PUT",
            headers: {
                "Content-Type": "application/json",
                "Authorization": "Bearer " + token
            },
            body: JSON.stringify({ addressId: addressId })
        });
        let result;
        try {
            result = await res.json();
        } catch (parseErr) {
            throw new Error("The server sent back a response that couldn't be read. Please try again.");
        }
        if (!res.ok) throw new Error(result.message || `Failed to set default address (server returned status ${res.status}).`);

        const addrRes = await authFetch(`${BASE_API_URL}/addresses`, {
            headers: { "Authorization": "Bearer " + token }
        });
        userAddressesList = addrRes.ok ? await addrRes.json() : [];
        if (!Array.isArray(userAddressesList)) userAddressesList = [];
        selectedAddressId = addressId;

        const container = document.getElementById("deliveryAddressContainer");
        if (container) container.innerHTML = buildAddressSectionHtml();
        await refreshOrderOffers();
    } catch (err) {
        alert("Could not set default address: " + describeError(err));
    }
}

// Called when the customer picks a different saved delivery address — the area/pincode
// discount depends on this, so offers are re-fetched against the newly selected address.
function onAddressSelected(radioEl) {
    selectedAddressId = radioEl.value ? parseInt(radioEl.value) : null;
    refreshOrderOffers();
}

// Saves a new address via POST /addresses (the dedicated address endpoint), then refreshes
// the saved-address list, selects the newly added one, and re-fetches offers against it.
// Validates the "Add new address" form BEFORE it ever reaches the network, so bad input gets
// an immediate, specific message instead of an opaque server or browser-level error later.
function validateAddressForm(payload) {
    if (!payload.street) {
        return "Please enter a street address.";
    }
    if (!payload.city) {
        return "Please enter a city.";
    }
    if (!payload.pincode) {
        return "Please enter a pincode.";
    }
    if (!/^\d{6}$/.test(payload.pincode)) {
        return "Pincode must be exactly 6 digits (e.g. 600001).";
    }
    return null;
}

// Turns a caught error into a message worth showing in the UI -- used by every page, customer
// AND admin alike. Both are business users of this product (a shopper, or the person running
// the store day-to-day), neither of whom should see implementation detail like "backend",
// "server", "database", or a stack trace -- that information is only ever useful to whoever is
// actually developing/debugging the app, and they read it from the console, not from an alert()
// or a page. Errors we threw ourselves (client-side validation, or a message the server sent
// back in its JSON body) already carry a clear, specific, business-appropriate .message -- those
// pass through unchanged. Anything else (a network failure, a malformed response, some other raw
// browser-level exception) gets a plain, non-technical fallback instead of surfacing whatever
// raw text the browser happened to produce. Either way, the real error is still console.error'd
// first -- so it's never actually lost, just kept out of the UI.
function describeError(err) {
    console.error(err);
    if (err instanceof TypeError) {
        return "We couldn't complete that — please check your internet connection and try again.";
    }
    return (err && err.message) ? err.message : "Something went wrong. Please try again.";
}

async function handleSaveNewAddress(event) {
    event.preventDefault();
    const token = getToken();

    const payload = {
        houseNo: document.getElementById("newAddrHouseNo").value.trim() || null,
        street: document.getElementById("newAddrStreet").value.trim(),
        landmark: document.getElementById("newAddrLandmark").value.trim() || null,
        city: document.getElementById("newAddrCity").value.trim(),
        state: document.getElementById("newAddrState").value.trim() || null,
        pincode: document.getElementById("newAddrPincode").value.trim(),
        defaultAddress: document.getElementById("newAddrIsDefault").checked
    };

    const validationError = validateAddressForm(payload);
    if (validationError) {
        alert(validationError);
        return;
    }

    try {
        const res = await authFetch(`${BASE_API_URL}/addresses`, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Authorization": "Bearer " + token
            },
            body: JSON.stringify(payload)
        });

        let result;
        try {
            result = await res.json();
        } catch (parseErr) {
            throw new Error("The server sent back a response that couldn't be read. Please try again.");
        }
        if (!res.ok) throw new Error(result.message || `Failed to save address (server returned status ${res.status}).`);

        const addrRes = await authFetch(`${BASE_API_URL}/addresses`, {
            headers: { "Authorization": "Bearer " + token }
        });
        userAddressesList = addrRes.ok ? await addrRes.json() : [];
        if (!Array.isArray(userAddressesList)) userAddressesList = [];
        selectedAddressId = result.address ? result.address.addressId : (userAddressesList[0] ? userAddressesList[0].addressId : null);

        const container = document.getElementById("deliveryAddressContainer");
        if (container) container.innerHTML = buildAddressSectionHtml();

        await refreshOrderOffers();
    } catch (err) {
        alert("Could not save address: " + describeError(err));
    }
}

// Fetches order-level discounts (birthday / pincode-area / min-order) eligible for the current
// subtotal + selected delivery address, renders them into #orderOffersContainer, and updates
// the displayed total. Called on initial cart-drawer render and again whenever the customer
// switches their selected address (area/pincode eligibility depends on it).
async function refreshOrderOffers() {
    const token = getToken();
    const offersEl = document.getElementById("orderOffersContainer");
    const totalEl = document.getElementById("cartTotalPayable");
    if (!offersEl) return;

    selectedOrderDiscountId = null;
    let offersHtml = '';
    let appliedSavings = 0;

    try {
        const addressQuery = selectedAddressId ? `&addressId=${selectedAddressId}` : '';
        const discRes = await authFetch(`${BASE_API_URL}/orders/eligible-discounts?subtotal=${cartItemsSubtotal}${addressQuery}`, {
            headers: { "Authorization": "Bearer " + token }
        });
        const eligibleDiscounts = discRes.ok ? await discRes.json() : [];

        if (Array.isArray(eligibleDiscounts) && eligibleDiscounts.length > 0) {
            const bestDiscount = eligibleDiscounts.reduce((best, d) =>
                parseFloat(d.calculatedSavings) > parseFloat(best.calculatedSavings) ? d : best, eligibleDiscounts[0]);
            selectedOrderDiscountId = bestDiscount.discountId;
            appliedSavings = parseFloat(bestDiscount.calculatedSavings);

            const optionsHtml = eligibleDiscounts.map(d => `
                <label style="display: flex; align-items: center; gap: 0.5rem; padding: 0.5rem 0; cursor: pointer; font-size: 0.85rem;">
                    <input type="radio" name="orderDiscountChoice" value="${d.discountId}" data-savings="${d.calculatedSavings}"
                           ${d.discountId === selectedOrderDiscountId ? 'checked' : ''} onchange="onOrderDiscountChange(this)">
                    <span>${d.name} <strong style="color: #10b981;">— save ₹${parseFloat(d.calculatedSavings).toFixed(2)}</strong></span>
                </label>
            `).join("");

            offersHtml = `
                <div style="margin-top: 1rem; padding-top: 1rem; border-top: 1px dashed #cbd5e1;">
                    <p style="font-size: 0.85rem; font-weight: 700; color: #334155; margin-bottom: 0.35rem;">Available Offers</p>
                    <label style="display: flex; align-items: center; gap: 0.5rem; padding: 0.5rem 0; cursor: pointer; font-size: 0.85rem;">
                        <input type="radio" name="orderDiscountChoice" value="" data-savings="0" onchange="onOrderDiscountChange(this)">
                        <span>No discount</span>
                    </label>
                    ${optionsHtml}
                </div>
            `;
        }
    } catch (discErr) {
        console.warn("[Cart Drawer] Could not load eligible discounts:", discErr);
    }

    offersEl.innerHTML = offersHtml;
    if (totalEl) totalEl.textContent = `₹${Math.max(0, cartItemsSubtotal - appliedSavings).toFixed(2)}`;
}

// Render Cart Items 100% DIRECTLY FROM DATABASE API (GET /cart + GET /products + GET /addresses)
async function renderCartDrawerItemsFromDB() {
    const token = getToken();
    const bodyEl = document.getElementById("cartDrawerBody");
    const totalEl = document.getElementById("cartTotalPayable");
    if (!bodyEl || !token) return;

    cartItemsSubtotal = 0;
    selectedOrderDiscountId = null;
    selectedAddressId = null;
    userAddressesList = [];

    try {
        bodyEl.innerHTML = `<p style="text-align: center; color: #94a3b8; padding: 2rem;">Fetching cart from database...</p>`;

        // 1. Fetch user's cart items from GET /cart (DB table cart_items)
        const cartRes = await authFetch(`${BASE_API_URL}/cart`, {
            headers: { "Authorization": "Bearer " + token }
        });
        const dbCartItems = await cartRes.json();
        console.log("[Cart Drawer] GET /cart response:", cartRes.status, dbCartItems);

        if (!cartRes.ok || !Array.isArray(dbCartItems) || dbCartItems.length === 0) {
            bodyEl.innerHTML = `
                <div style="text-align: center; color: #64748b; padding: 3rem 1rem;">
                    <p style="margin-bottom: 1rem;">Your cart is currently empty.</p>
                    <button onclick="toggleCartDrawer(); location.href='products.html';" style="padding: 0.6rem 1.2rem; background: #10b981; color: white; border: none; border-radius: 6px; font-weight: 600; cursor: pointer;">Browse Catalog</button>
                </div>
            `;
            if (totalEl) totalEl.textContent = "₹0.00";
            return;
        }

        // 2. Fetch product catalog for pricing details
        const prodRes = await fetch(`${BASE_API_URL}/products`);
        const rawProducts = prodRes.ok ? await prodRes.json() : [];
        const products = Array.isArray(rawProducts) ? rawProducts : [];

        let totalPayable = 0;
        const itemsHtml = dbCartItems.map(item => {
            const p = products.find(prod => String(prod.productId) === String(item.productId));
            if (!p) return '';
            const qty = item.quantity;
            const price = p.discountedPrice != null ? p.discountedPrice : p.price;
            const lineTotal = price * qty;
            totalPayable += lineTotal;

            return `
                <div id="cart-item-${item.productId}" data-price="${price}" style="display: flex; justify-content: space-between; align-items: center; padding: 0.75rem 0; border-bottom: 1px solid #f1f5f9;">
                    <div>
                        <strong style="font-size: 0.95rem; color: #0f172a; display: block;">${p.name} ${p.unitValue ? '(' + p.unitValue + ')' : ''}</strong>
                        <div style="display: flex; align-items: center; gap: 0.75rem; margin-top: 0.25rem;">
                            <span style="font-size: 0.8rem; color: #64748b;">₹${price} / ea</span>
                            <div style="display: flex; align-items: center; gap: 0.5rem; background: #f1f5f9; padding: 0.15rem 0.5rem; border-radius: 99px;">
                                <button onclick="updateCartDrawerQty(${item.productId}, -1)" style="border: none; background: none; cursor: pointer; font-weight: bold; color: #0f172a; padding: 0 4px;">−</button>
                                <span id="cart-qty-${item.productId}" style="font-size: 0.8rem; font-weight: 600; min-width: 1rem; text-align: center;">${qty}</span>
                                <button onclick="updateCartDrawerQty(${item.productId}, 1)" style="border: none; background: none; cursor: pointer; font-weight: bold; color: #10b981; padding: 0 4px;">+</button>
                            </div>
                        </div>
                    </div>
                    <strong id="cart-line-${item.productId}" style="color: #0f172a; font-size: 0.95rem;">₹${lineTotal.toFixed(2)}</strong>
                </div>
            `;
        }).join("");

        cartItemsSubtotal = totalPayable;

        // 3. Fetch the customer's saved delivery addresses from the dedicated /addresses
        // endpoint. Required before checkout, and the selected one's pincode drives the
        // area/pincode discount below -- most-recently-added is pre-selected by default.
        try {
            const addrRes = await authFetch(`${BASE_API_URL}/addresses`, {
                headers: { "Authorization": "Bearer " + token }
            });
            userAddressesList = addrRes.ok ? await addrRes.json() : [];
            if (!Array.isArray(userAddressesList)) userAddressesList = [];
        } catch (addrErr) {
            console.warn("[Cart Drawer] Could not load addresses:", addrErr);
            userAddressesList = [];
        }
        selectedAddressId = userAddressesList.length > 0 ? userAddressesList[0].addressId : null;

        bodyEl.innerHTML = itemsHtml +
            `<div id="deliveryAddressContainer">${buildAddressSectionHtml()}</div>` +
            `<div id="orderOffersContainer"></div>`;

        // 4. Now that subtotal + selected address are known, fetch which order-level discounts
        // (birthday / pincode-area / min-order) this customer currently qualifies for, and
        // offer them as a mutually-exclusive choice. Best savings pre-selected.
        await refreshOrderOffers();
    } catch (err) {
        bodyEl.innerHTML = `<p style="color: #ef4444; padding: 1rem;">${describeError(err)}</p>`;
    }
}

// Called when the customer switches their order-discount radio selection — recomputes the
// displayed total live, without re-fetching anything.
function onOrderDiscountChange(radioEl) {
    selectedOrderDiscountId = radioEl.value ? parseInt(radioEl.value) : null;
    const savings = parseFloat(radioEl.dataset.savings || "0");
    const totalEl = document.getElementById("cartTotalPayable");
    if (totalEl) totalEl.textContent = `₹${Math.max(0, cartItemsSubtotal - savings).toFixed(2)}`;
}

// Database-Driven Checkout Handler
async function submitCheckoutOrder() {
    const token = getToken();
    if (!token) {
        openLoginModal();
        return;
    }

    if (!selectedAddressId) {
        alert("Please add or select a delivery address before placing your order.");
        return;
    }

    const btn = document.getElementById("btnCheckoutSubmit");
    if (btn) {
        btn.disabled = true;
        btn.textContent = "Processing Order...";
    }

    try {
        const response = await authFetch(`${BASE_API_URL}/orders/checkout`, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Authorization": "Bearer " + token
            },
            body: JSON.stringify({ paymentMethod: "CASH_ON_DELIVERY", discountId: selectedOrderDiscountId, addressId: selectedAddressId })
        });

        const result = await response.json();
        if (!response.ok) {
            throw new Error(result.message || "Failed to place order");
        }

        alert(`🎉 Success! Order #${result.order ? result.order.orderId : ''} has been placed successfully!\nPayment Status: PAID\nPayment Method: Cash on Delivery`);
        
        toggleCartDrawer();
        window.location.href = "orders.html";
    } catch (err) {
        alert("Checkout Error: " + describeError(err));
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.textContent = "Place Order (Cash on Delivery) →";
        }
    }
}
