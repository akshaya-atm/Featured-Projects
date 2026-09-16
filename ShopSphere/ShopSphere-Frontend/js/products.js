// ShopSphere Products Catalog Logic with Unit Value, Fresh Today Badge, Login Popup & JWT API Cart Integration

const PRODUCTS_URL = "http://localhost:8080/ShopSphere-Backend/products";
const CART_URL = "http://localhost:8080/ShopSphere-Backend/cart";
const CATEGORIES_URL = "http://localhost:8080/ShopSphere-Backend/categories";

let allProducts = [];
let selectedCategory = "all";
let cartQuantities = {};

const productsGrid = document.getElementById("productsGrid");
const categoryPills = document.getElementById("categoryPills");
const productCount = document.getElementById("productCount");
const searchInput = document.getElementById("searchInput");

document.addEventListener("DOMContentLoaded", async () => {
  const urlParams = new URLSearchParams(window.location.search);
  const categoryParam = urlParams.get("category");
  if (categoryParam) {
    selectedCategory = categoryParam;
  }

  fetchCategories();
  await fetchUserCart();
  fetchProducts();
});

async function fetchUserCart() {
  const token = localStorage.getItem("token");
  if (!token) return;
  try {
    const res = await authFetch(CART_URL);
    if (res.ok) {
      const cartItems = await res.json();
      cartQuantities = {};
      cartItems.forEach(item => {
        cartQuantities[item.productId] = item.quantity;
      });
      updateCartBadgeCount();
    }
  } catch (err) {
    console.error("Failed to fetch cart on load:", err);
  }
}

async function fetchCategories() {
  try {
    const res = await fetch(CATEGORIES_URL);
    if (res.ok) {
      const categories = await res.json();
      renderCategoryPills(categories);
    }
  } catch (err) {
    console.error("Could not fetch categories:", err);
  }
}

function renderCategoryPills(categories) {
  if (!categoryPills) return;
  categoryPills.innerHTML = `<button class="pill ${selectedCategory === 'all' ? 'active' : ''}" data-category="all">All Products</button>`;
  
  const seenNames = new Set();
  categories.forEach(cat => {
    if (!seenNames.has(cat.categoryName)) {
      seenNames.add(cat.categoryName);
      const isAct = String(selectedCategory) === String(cat.categoryId);
      const btn = document.createElement("button");
      btn.className = `pill ${isAct ? 'active' : ''}`;
      btn.dataset.category = cat.categoryId;
      btn.textContent = cat.categoryName;
      categoryPills.appendChild(btn);
    }
  });

  categoryPills.addEventListener("click", (e) => {
    if (e.target.classList.contains("pill")) {
      document.querySelectorAll(".pill").forEach(p => p.classList.remove("active"));
      e.target.classList.add("active");
      selectedCategory = e.target.dataset.category;
      filterAndRenderProducts();
    }
  });
}

async function fetchProducts() {
  try {
    const res = await fetch(PRODUCTS_URL);
    if (!res.ok) throw new Error("Failed to load products");
    allProducts = await res.json();
    filterAndRenderProducts();
  } catch (err) {
    console.error("Backend Connection Error:", err);
    if (productsGrid) productsGrid.innerHTML = `<p style="text-align: center; color: #64748b; padding: 3rem; font-size: 1.05rem;">We are experiencing technical difficulties loading products. Please try again shortly.</p>`;
    if (productCount) productCount.textContent = "0 products";
  }
}

function filterAndRenderProducts() {
  const searchTerm = searchInput ? searchInput.value.toLowerCase().trim() : "";

  const filtered = allProducts.filter(product => {
    const matchesCategory = selectedCategory === "all" || 
      (product.categories && product.categories.some(c => String(c.categoryId) === String(selectedCategory)));
    
    const matchesSearch = product.name.toLowerCase().includes(searchTerm) || 
                          (product.description && product.description.toLowerCase().includes(searchTerm));
    return matchesCategory && matchesSearch;
  });

  renderProducts(filtered);
}

function renderProducts(products) {
  if (!productCount || !productsGrid) return;

  productCount.textContent = `${products.length} product${products.length === 1 ? '' : 's'} available`;

  if (products.length === 0) {
    productsGrid.innerHTML = `<p style="text-align: center; color: #94a3b8; padding: 3rem;">No products found in this category.</p>`;
    return;
  }

  productsGrid.innerHTML = products.map(product => {
    const isAvailable = product.totalAvailableQuantity > 0;
    const placeholderImg = "https://images.unsplash.com/photo-1542838132-92c53300491e?auto=format&fit=crop&w=400&q=80";
    const imgUrl = product.imageUrl || placeholderImg;
    const unitDisplay = product.unitValue ? `<span style="font-size: 0.85rem; color: #64748b; font-weight: 500;">(${product.unitValue})</span>` : '';

    // "Fresh Today" is driven by category data (admin-flagged "Perishable"), not the product itself.
    const isFreshToday = (product.categories || []).some(cat => cat.perishable === true);

    const currentQty = cartQuantities[product.productId] || 0;

    return `
      <div class="product-list-item" id="product-item-${product.productId}">
        <!-- Left Column: Title, Fresh Badge, Stock Tags, Description -->
        <div class="list-item-info">
          <div style="display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap;">
            <h3 class="product-title" style="margin: 0;">${product.name} ${unitDisplay}</h3>
            ${isFreshToday ? `
              <span style="padding: 0.2rem 0.6rem; background: #ecfdf5; color: #059669; border: 1px solid #a7f3d0; border-radius: 999px; font-size: 0.75rem; font-weight: 700; display: inline-flex; align-items: center; gap: 0.25rem;">
                🌿 Fresh Today
              </span>
            ` : ''}
          </div>

          ${!isAvailable || product.totalAvailableQuantity <= 5 ? `
            <div class="list-item-header" style="margin-top: 0.25rem;">
              ${!isAvailable ? `
                <span class="stock-tag stock-out">Out of Stock</span>
              ` : (product.totalAvailableQuantity <= 5 ? `
                <span class="stock-tag stock-low">Only ${product.totalAvailableQuantity} left!</span>
              ` : '')}
            </div>
          ` : ''}

          <p class="product-desc" id="desc-box-${product.productId}" style="margin-top: 0.5rem;">
            <span class="desc-text" id="desc-text-${product.productId}">${product.description || 'Fresh quality produce.'}</span>
            <button class="btn-more" id="btn-more-${product.productId}" onclick="toggleDescription(${product.productId})" style="display: none;">more</button>
          </p>
        </div>

        <!-- Right Column: Product Image, Discount Badge, Price & Add CTA -->
        <div class="list-item-actions">
          <div class="list-item-img-wrap">
            ${product.discountPercentage ? `
              <span class="discount-badge-corner">${product.discountPercentage}% OFF</span>
            ` : ''}
            <img src="${imgUrl}" alt="${product.name}" class="list-item-img" onerror="this.src='${placeholderImg}'">
          </div>

          <div class="price-container">
            ${product.discountedPrice ? `
              <span class="price-discounted">₹${product.discountedPrice}</span>
              <span class="price-original">₹${product.price}</span>
            ` : `
              <span class="price-normal">₹${product.price}</span>
            `}
          </div>
          
          <!-- Quantity Selector Button -->
          <div class="qty-control-wrap" id="qty-wrap-${product.productId}">
            ${currentQty === 0 ? `
              <button class="btn-add-qty" ${!isAvailable ? 'disabled' : ''} onclick="handleAddToCartClick(${product.productId}, 1, ${product.totalAvailableQuantity})">
                + Add
              </button>
            ` : `
              <div class="qty-stepper">
                <button class="stepper-btn" onclick="handleAddToCartClick(${product.productId}, -1, ${product.totalAvailableQuantity})">-</button>
                <span class="qty-val">${currentQty}</span>
                <button class="stepper-btn" onclick="handleAddToCartClick(${product.productId}, 1, ${product.totalAvailableQuantity})">+</button>
              </div>
            `}
          </div>
        </div>
      </div>
    `;
  }).join("");

  checkDescriptionOverflow();
}

async function handleAddToCartClick(productId, change, maxStock) {
  const token = localStorage.getItem("token");
  if (!token) {
    openLoginModal(() => {
      handleAddToCartClick(productId, change, maxStock);
    });
    return;
  }

  const current = cartQuantities[productId] || 0;
  const newQty = current + change;

  if (newQty < 0) return;
  if (newQty > maxStock) {
    alert(`Maximum available stock is ${maxStock} units.`);
    return;
  }

  try {
    const endpoint = CART_URL;
    const method = change > 0 ? "POST" : "DELETE";

    const res = await authFetch(endpoint, {
      method: method,
      headers: {
        "Content-Type": "application/json",
        "Authorization": "Bearer " + token
      },
      body: JSON.stringify({ productId: productId, quantity: Math.abs(change) })
    });

    const text = await res.text();
    const result = text ? JSON.parse(text) : {};
    if (!res.ok) throw new Error(result.message || "Failed to update cart");

    if (newQty === 0) {
      delete cartQuantities[productId];
    } else {
      cartQuantities[productId] = newQty;
    }

    // 1. Update only the targeted button DOM in place (No full grid re-render -> No vibration)
    const wrapEl = document.getElementById(`qty-wrap-${productId}`);
    if (wrapEl) {
      const isAvailable = maxStock > 0;
      wrapEl.innerHTML = newQty === 0 ? `
        <button class="btn-add-qty" ${!isAvailable ? 'disabled' : ''} onclick="handleAddToCartClick(${productId}, 1, ${maxStock})">
          + Add
        </button>
      ` : `
        <div class="qty-stepper">
          <button class="stepper-btn" onclick="handleAddToCartClick(${productId}, -1, ${maxStock})">-</button>
          <span class="qty-val">${newQty}</span>
          <button class="stepper-btn" onclick="handleAddToCartClick(${productId}, 1, ${maxStock})">+</button>
        </div>
      `;
    }

    // 2. Update navbar cart badge counter locally (no API call)
    updateCartBadgeCount();

    if (typeof renderCartDrawerItemsFromDB === 'function' && document.getElementById('cartDrawerOverlay')?.classList.contains('open')) {
      renderCartDrawerItemsFromDB();
    }
  } catch (err) {
    alert("Cart Error: " + describeError(err));
  }
}

// Lightweight local counter — no API calls
function updateCartBadgeCount() {
  const totalItems = Object.values(cartQuantities).reduce((sum, qty) => sum + qty, 0);
  const badge = document.getElementById("cartBadgeCount");
  if (badge) {
    badge.textContent = totalItems;
    badge.style.display = totalItems > 0 ? "inline-flex" : "none";
  }
}

function checkDescriptionOverflow() {
  requestAnimationFrame(() => {
    allProducts.forEach(product => {
      const textEl = document.getElementById(`desc-text-${product.productId}`);
      const btnEl = document.getElementById(`btn-more-${product.productId}`);
      if (textEl && btnEl) {
        if (textEl.scrollHeight > textEl.clientHeight + 2) {
          btnEl.style.display = "inline";
        } else {
          btnEl.style.display = "none";
        }
      }
    });
  });
}

function toggleDescription(productId) {
  const textEl = document.getElementById(`desc-text-${productId}`);
  const btnEl = document.getElementById(`btn-more-${productId}`);
  if (!textEl || !btnEl) return;

  if (textEl.classList.contains("expanded")) {
    textEl.classList.remove("expanded");
    btnEl.textContent = "more";
  } else {
    textEl.classList.add("expanded");
    btnEl.textContent = "less";
  }
}

if (searchInput) searchInput.addEventListener("input", filterAndRenderProducts);
window.addEventListener("resize", checkDescriptionOverflow);
