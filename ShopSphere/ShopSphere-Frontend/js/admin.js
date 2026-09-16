// ShopSphere Premium Admin Dashboard & Full CRUD Management Logic


const ADMIN_ORDERS_URL = "http://localhost:8080/ShopSphere-Backend/admin/orders";
const PRODUCTS_URL = "http://localhost:8080/ShopSphere-Backend/products";
// Admin mutations go through the admin-gated endpoint; the public /products endpoint is
// read-only and unauthenticated.
const ADMIN_PRODUCTS_URL = "http://localhost:8080/ShopSphere-Backend/admin/products";
const CATEGORIES_URL = "http://localhost:8080/ShopSphere-Backend/categories";
// Same reasoning as ADMIN_PRODUCTS_URL -- /categories is unauthenticated and read-only.
const ADMIN_CATEGORIES_URL = "http://localhost:8080/ShopSphere-Backend/admin/categories";
const ADMIN_DISCOUNTS_URL = "http://localhost:8080/ShopSphere-Backend/admin/discounts";
const ADMIN_BATCHES_URL = "http://localhost:8080/ShopSphere-Backend/admin/batches";

let allProductsList = [];
let allCategoriesList = [];
let allDiscountsList = [];
let currentBatchList = [];
let batchModalViewMode = 'batches';

document.addEventListener("DOMContentLoaded", () => {
    const token = localStorage.getItem("token");
    const userType = localStorage.getItem("userType");

    if (!token || userType !== "ADMIN") {
        alert("Admin Access Required. Please log in with an Admin account.");
        window.location.href = "login.html";
        return;
    }

    fetchAdminOrders();
    fetchAdminCategories();
    // Await fetchAdminProducts first -- the discounts table looks up product names from
    // allProductsList, which fetchAdminProducts() populates.
    (async () => {
        await fetchAdminProducts();
        fetchAdminDiscounts();
    })();

    // Form Listeners
    const productForm = document.getElementById("productForm");
    if (productForm) productForm.addEventListener("submit", handleSaveProduct);

    const categoryForm = document.getElementById("categoryForm");
    if (categoryForm) categoryForm.addEventListener("submit", handleSaveCategory);

    const discountForm = document.getElementById("createDiscountForm");
    if (discountForm) discountForm.addEventListener("submit", handleSaveDiscount);

    const batchForm = document.getElementById("batchForm");
    if (batchForm) batchForm.addEventListener("submit", handleSaveBatch);
});

function switchAdminTab(tabId) {
    document.querySelectorAll(".tab-content").forEach(tab => tab.style.display = "none");
    document.querySelectorAll(".tab-btn").forEach(btn => btn.classList.remove("active"));

    const activeTab = document.getElementById(tabId);
    if (activeTab) activeTab.style.display = "block";
    
    const activeBtn = Array.from(document.querySelectorAll(".tab-btn")).find(btn => btn.getAttribute("onclick").includes(tabId));
    if (activeBtn) activeBtn.classList.add("active");
}

/* ==========================================================================
   1. PRODUCTS CRUD LOGIC
   ========================================================================== */

async function fetchAdminProducts() {
    const container = document.getElementById("adminProductsList");
    try {
        const res = await fetch(PRODUCTS_URL);
        const data = await res.json();
        if (!res.ok || !Array.isArray(data)) throw new Error((data && data.message) || "Failed to load products");
        allProductsList = data;

        document.getElementById("metricTotalProducts").textContent = allProductsList.length;

        if (allProductsList.length === 0) {
            container.innerHTML = `<p style="color: #94a3b8; padding: 2rem; text-align: center;">No catalog products found.</p>`;
            return;
        }

        container.innerHTML = `
            <table class="data-table">
                <thead>
                    <tr>
                        <th>ID</th>
                        <th>Product</th>
                        <th>Unit Value</th>
                        <th>Price</th>
                        <th>Stock</th>
                        <th>Actions</th>
                    </tr>
                </thead>
                <tbody>
                    ${allProductsList.map(p => `
                        <tr>
                            <td>#${p.productId}</td>
                            <td>
                                <div style="display: flex; align-items: center; gap: 0.75rem;">
                                    <img src="${p.imageUrl}" alt="${p.name}" style="width: 40px; height: 40px; border-radius: 6px; object-fit: contain; background: #f8fafc; border: 1px solid #e2e8f0;">
                                    <div>
                                        <strong style="color: #0f172a;">${p.name}</strong>
                                        <div style="font-size: 0.75rem; color: #64748b;">${(p.description || '').substring(0, 45)}...</div>
                                        <div style="font-size: 0.7rem; color: #f59e0b; font-weight: 700; margin-top: 0.15rem;">📁 ${(p.categories && p.categories.length > 0) ? p.categories[0].categoryName : 'Uncategorized'}</div>
                                    </div>
                                </div>
                            </td>
                            <td><span style="background: #f1f5f9; padding: 0.2rem 0.5rem; border-radius: 4px; font-weight: 600;">${p.unitValue || '-'}</span></td>
                            <td><strong>₹${p.price}</strong></td>
                            <td><strong style="color: ${p.totalAvailableQuantity === 0 ? '#ef4444' : p.totalAvailableQuantity <= 5 ? '#f59e0b' : '#10b981'}">${p.totalAvailableQuantity} units</strong></td>
                            <td>
                                <div style="display: flex; gap: 0.35rem;">
                                    <button onclick="openBatchManageModal(${p.productId})" style="padding: 0.3rem 0.6rem; background: #fef3c7; color: #92400e; border: none; border-radius: 6px; font-weight: 700; cursor: pointer;">📦 Stock</button>
                                    <button onclick="editProduct(${p.productId})" style="padding: 0.3rem 0.6rem; background: #e0f2fe; color: #0369a1; border: none; border-radius: 6px; font-weight: 700; cursor: pointer;">Edit</button>
                                    <button onclick="deleteProduct(${p.productId})" style="padding: 0.3rem 0.6rem; background: #fee2e2; color: #991b1b; border: none; border-radius: 6px; font-weight: 700; cursor: pointer;">Delete</button>
                                </div>
                            </td>
                        </tr>
                    `).join("")}
                </tbody>
            </table>
        `;
    } catch (err) {
        container.innerHTML = `<p style="color: #ef4444; padding: 2rem; text-align: center;">Error: ${describeError(err)}</p>`;
    }
}

function openProductModal(prod = null) {
    const modal = document.getElementById("productModal");
    const title = document.getElementById("productModalTitle");
    const form = document.getElementById("productForm");
    
    const categorySelect = document.getElementById("prodCategory");
    if (categorySelect) {
        categorySelect.innerHTML = `
            <option value="" disabled selected>Select Category</option>
            ${allCategoriesList.map(cat => `
                <option value="${cat.categoryId}">${cat.categoryName}</option>
            `).join("")}
        `;
    }

    const stockSection = document.getElementById("prodInitialStockSection");
    const initQty = document.getElementById("prodInitialQty");
    const initSource = document.getElementById("prodInitialSource");

    if (prod) {
        title.textContent = "✏️ Edit Product #" + prod.productId;
        document.getElementById("prodId").value = prod.productId;
        document.getElementById("prodName").value = prod.name;
        document.getElementById("prodPrice").value = prod.price;
        document.getElementById("prodUnit").value = prod.unitValue || "";
        document.getElementById("prodDesc").value = prod.description || "";
        document.getElementById("prodImg").value = prod.imageUrl || "";
        if (categorySelect && prod.categories && prod.categories.length > 0) {
            categorySelect.value = prod.categories[0].categoryId;
        }
        if (stockSection) stockSection.style.display = "none";
        if (initQty) initQty.removeAttribute("required");
        if (initSource) initSource.removeAttribute("required");
    } else {
        title.textContent = "+ Add New Product";
        form.reset();
        document.getElementById("prodId").value = "";
        if (categorySelect) categorySelect.value = "";
        if (stockSection) stockSection.style.display = "block";
        if (initQty) initQty.setAttribute("required", "required");
        if (initSource) initSource.setAttribute("required", "required");
    }
    modal.classList.add("open");
}

function closeProductModal() {
    document.getElementById("productModal").classList.remove("open");
}

function editProduct(productId) {
    const prod = allProductsList.find(p => p.productId === productId);
    if (prod) openProductModal(prod);
}

async function handleSaveProduct(event) {
    event.preventDefault();
    const token = localStorage.getItem("token");
    const id = document.getElementById("prodId").value;
    const isEdit = Boolean(id);

    const selectedCategoryId = document.getElementById("prodCategory").value;

    const productPayload = {
        name: document.getElementById("prodName").value,
        price: parseFloat(document.getElementById("prodPrice").value),
        unitValue: document.getElementById("prodUnit").value,
        description: document.getElementById("prodDesc").value,
        imageUrl: document.getElementById("prodImg").value,
        categories: selectedCategoryId ? [{ categoryId: parseInt(selectedCategoryId) }] : []
    };

    if (isEdit) {
        productPayload.productId = parseInt(id);
    }

    try {
        const res = await authFetch(ADMIN_PRODUCTS_URL, {
            method: isEdit ? "PUT" : "POST",
            headers: {
                "Content-Type": "application/json",
                "Authorization": "Bearer " + token
            },
            body: JSON.stringify(productPayload)
        });

        const result = await res.json();
        if (!res.ok) throw new Error(result.message || "Failed to save product");

        closeProductModal();

        if (!isEdit && result.productId) {
            const qty = parseInt(document.getElementById("prodInitialQty").value) || 0;
            const source = document.getElementById("prodInitialSource").value.trim() || "Initial Stock";
            const expiry = document.getElementById("prodInitialExpiry").value || null;

            if (qty > 0) {
                const batchPayload = {
                    productId: result.productId,
                    source: source,
                    batchDate: new Date().toISOString().split("T")[0],
                    expiryDate: expiry,
                    availableQuantity: qty
                };

                const batchRes = await authFetch(ADMIN_BATCHES_URL, {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json",
                        "Authorization": "Bearer " + token
                    },
                    body: JSON.stringify(batchPayload)
                });
                const batchResult = await batchRes.json();
                if (!batchRes.ok) {
                    // Alert the admin -- the modal already closed and the list is about to refresh
                    // showing 0 stock, so silent failure here would be easy to miss.
                    alert(`Product "${productPayload.name}" was created, but its initial stock batch ` +
                        `could not be added (${batchResult.message || "unknown error"}). ` +
                        `Add stock manually via the 📦 Stock button.`);
                }
            }
        }

        await fetchAdminProducts();
    } catch (err) {
        alert("Error saving product: " + describeError(err));
    }
}

async function deleteProduct(productId) {
    if (!confirm(`Are you sure you want to delete Product #${productId}?`)) return;
    const token = localStorage.getItem("token");
    try {
        const res = await authFetch(`${ADMIN_PRODUCTS_URL}?id=${productId}`, {
            method: "DELETE",
            headers: { "Authorization": "Bearer " + token }
        });
        const result = await res.json();
        if (!res.ok) throw new Error(result.message || "Failed to delete product");
        alert("🗑️ Product deleted successfully!");
        fetchAdminProducts();
    } catch (err) {
        alert("Delete Error: " + describeError(err));
    }
}

/* ==========================================================================
   2. CATEGORIES CRUD LOGIC
   ========================================================================== */

async function fetchAdminCategories() {
    const container = document.getElementById("adminCategoriesList");
    try {
        const res = await fetch(CATEGORIES_URL);
        const data = await res.json();
        if (!res.ok || !Array.isArray(data)) throw new Error((data && data.message) || "Failed to load categories");
        allCategoriesList = data;

        document.getElementById("metricTotalCategories").textContent = allCategoriesList.length;

        if (allCategoriesList.length === 0) {
            container.innerHTML = `<p style="color: #94a3b8; padding: 2rem; text-align: center;">No categories found.</p>`;
            return;
        }

        container.innerHTML = `
            <table class="data-table">
                <thead>
                    <tr>
                        <th>ID</th>
                        <th>Category</th>
                        <th>Description</th>
                        <th>Fresh Today?</th>
                        <th>Actions</th>
                    </tr>
                </thead>
                <tbody>
                    ${allCategoriesList.map(c => `
                        <tr>
                            <td>#${c.categoryId}</td>
                            <td>
                                <div style="display: flex; align-items: center; gap: 0.75rem;">
                                    <img src="${c.categoryImageUrl}" alt="${c.categoryName}" style="width: 36px; height: 36px; border-radius: 50%; object-fit: cover;">
                                    <strong style="color: #0f172a;">${c.categoryName}</strong>
                                </div>
                            </td>
                            <td>${c.categoryDescription || '-'}</td>
                            <td>${c.perishable ? '<span style="color: #059669; font-weight: 700;">🌿 Yes</span>' : '<span style="color: #94a3b8;">No</span>'}</td>
                            <td>
                                <div style="display: flex; gap: 0.35rem;">
                                    <button onclick="editCategory(${c.categoryId})" style="padding: 0.3rem 0.6rem; background: #e0f2fe; color: #0369a1; border: none; border-radius: 6px; font-weight: 700; cursor: pointer;">Edit</button>
                                    <button onclick="deleteCategory(${c.categoryId})" style="padding: 0.3rem 0.6rem; background: #fee2e2; color: #991b1b; border: none; border-radius: 6px; font-weight: 700; cursor: pointer;">Delete</button>
                                </div>
                            </td>
                        </tr>
                    `).join("")}
                </tbody>
            </table>
        `;
    } catch (err) {
        container.innerHTML = `<p style="color: #ef4444; padding: 2rem; text-align: center;">Error: ${describeError(err)}</p>`;
    }
}

function openCategoryModal(cat = null) {
    const modal = document.getElementById("categoryModal");
    const title = document.getElementById("categoryModalTitle");
    const form = document.getElementById("categoryForm");
    
    if (cat) {
        title.textContent = "✏️ Edit Category #" + cat.categoryId;
        document.getElementById("catId").value = cat.categoryId;
        document.getElementById("catName").value = cat.categoryName;
        document.getElementById("catDesc").value = cat.categoryDescription || "";
        document.getElementById("catImg").value = cat.categoryImageUrl || "";
        document.getElementById("catPerishable").checked = cat.perishable === true;
    } else {
        title.textContent = "+ Add New Category";
        form.reset();
        document.getElementById("catId").value = "";
        document.getElementById("catPerishable").checked = false;
    }
    modal.classList.add("open");
}

function closeCategoryModal() {
    document.getElementById("categoryModal").classList.remove("open");
}

function editCategory(categoryId) {
    const cat = allCategoriesList.find(c => c.categoryId === categoryId);
    if (cat) openCategoryModal(cat);
}

async function handleSaveCategory(event) {
    event.preventDefault();
    const token = localStorage.getItem("token");
    const id = document.getElementById("catId").value;
    const isEdit = Boolean(id);

    const categoryPayload = {
        categoryName: document.getElementById("catName").value,
        categoryDescription: document.getElementById("catDesc").value,
        categoryImageUrl: document.getElementById("catImg").value,
        perishable: document.getElementById("catPerishable").checked
    };

    if (isEdit) {
        categoryPayload.categoryId = parseInt(id);
    }

    try {
        const res = await authFetch(ADMIN_CATEGORIES_URL, {
            method: isEdit ? "PUT" : "POST",
            headers: {
                "Content-Type": "application/json",
                "Authorization": "Bearer " + token
            },
            body: JSON.stringify(categoryPayload)
        });

        const result = await res.json();
        if (!res.ok) throw new Error(result.message || "Failed to save category");

        alert(`🎉 Category ${isEdit ? 'updated' : 'added'} successfully!`);
        closeCategoryModal();
        fetchAdminCategories();
    } catch (err) {
        alert("Error saving category: " + describeError(err));
    }
}

async function deleteCategory(categoryId) {
    if (!confirm(`Are you sure you want to delete Category #${categoryId}?`)) return;
    const token = localStorage.getItem("token");
    try {
        const res = await authFetch(`${ADMIN_CATEGORIES_URL}?id=${categoryId}`, {
            method: "DELETE",
            headers: { "Authorization": "Bearer " + token }
        });
        const result = await res.json();
        if (!res.ok) throw new Error(result.message || "Failed to delete category");
        alert("🗑️ Category deleted successfully!");
        fetchAdminCategories();
    } catch (err) {
        alert("Delete Error: " + describeError(err));
    }
}

/* ==========================================================================
   3. CUSTOMER ORDERS & DISCOUNTS LOGIC
   ========================================================================== */

async function fetchAdminOrders() {
    const token = localStorage.getItem("token");
    const container = document.getElementById("adminOrdersList");

    try {
        const res = await authFetch(ADMIN_ORDERS_URL, {
            headers: { "Authorization": "Bearer " + token }
        });
        const orders = await res.json();
        if (!res.ok) throw new Error(orders.message || "Failed to load orders");

        // Cancelled orders never counted revenue in the first place (or had it refunded) —
        // excluded here so the metric stays accurate regardless of which side cancelled.
        const totalRev = orders
            .filter(o => o.status !== "CANCELLED")
            .reduce((sum, o) => sum + (o.finalAmount || o.totalAmount || 0), 0);
        document.getElementById("metricTotalOrders").textContent = orders.length;
        document.getElementById("metricTotalRevenue").textContent = "₹" + totalRev.toFixed(2);

        if (orders.length === 0) {
            container.innerHTML = `<p style="color: #94a3b8; padding: 2rem; text-align: center;">No customer orders placed yet.</p>`;
            return;
        }

        container.innerHTML = `
            <table class="data-table">
                <thead>
                    <tr>
                        <th>Order ID</th>
                        <th>User ID</th>
                        <th>Date</th>
                        <th>Items Purchased</th>
                        <th>Total Amount</th>
                        <th>Payment</th>
                        <th>Fulfillment Status</th>
                    </tr>
                </thead>
                <tbody>
                    ${orders.map(order => `
                        <tr>
                            <td><strong>#${order.orderId}</strong></td>
                            <td>User #${order.userId}</td>
                            <td>${order.createdAt ? new Date(order.createdAt).toLocaleDateString() : 'Recent'}</td>
                            <td>${(order.orderItems || []).map(i => `${i.productName} (x${i.quantity})`).join(", ")}</td>
                            <td><strong>₹${(order.finalAmount || order.totalAmount || 0).toFixed(2)}</strong></td>
                            <td><span style="color: ${order.paymentStatus === 'REFUNDED' ? '#f59e0b' : '#10b981'}; font-weight: 700;">${order.paymentStatus || 'PAID'}</span></td>
                            <td>
                                <select class="form-control" style="width: auto; padding: 0.35rem 0.6rem;" onchange="updateOrderStatus(${order.orderId}, this.value)">
                                    <option value="PLACED" ${order.status === 'PLACED' ? 'selected' : ''}>PLACED</option>
                                    <option value="PROCESSING" ${order.status === 'PROCESSING' ? 'selected' : ''}>PROCESSING</option>
                                    <option value="SHIPPED" ${order.status === 'SHIPPED' ? 'selected' : ''}>SHIPPED</option>
                                    <option value="DELIVERED" ${order.status === 'DELIVERED' ? 'selected' : ''}>DELIVERED</option>
                                    ${(order.status === 'SHIPPED' || order.status === 'DELIVERED')
                                        ? `<option value="CANCELLED" disabled title="Already shipped — cancel is disabled; it would restock inventory that isn't really back in the warehouse.">CANCELLED (locked — already shipped)</option>`
                                        : `<option value="CANCELLED" ${order.status === 'CANCELLED' ? 'selected' : ''}>CANCELLED</option>`
                                    }
                                </select>
                            </td>
                        </tr>
                    `).join("")}
                </tbody>
            </table>
        `;
    } catch (err) {
        container.innerHTML = `<p style="color: #ef4444; padding: 2rem; text-align: center;">Error: ${describeError(err)}</p>`;
    }
}

async function updateOrderStatus(orderId, newStatus) {
    if (newStatus === "CANCELLED") {
        const confirmed = confirm(
            `Cancel order #${orderId}? This restores its items to stock and, if it was paid, ` +
            `marks the payment as refunded. This cannot be undone.`
        );
        if (!confirmed) {
            fetchAdminOrders(); // revert the dropdown back to its actual status
            return;
        }
    }

    const token = localStorage.getItem("token");
    try {
        const res = await authFetch(ADMIN_ORDERS_URL, {
            method: "PUT",
            headers: {
                "Content-Type": "application/json",
                "Authorization": "Bearer " + token
            },
            body: JSON.stringify({ orderId: orderId, status: newStatus })
        });
        const result = await res.json();
        if (!res.ok) throw new Error(result.message || "Failed to update order status");
        alert(`Order #${orderId} status updated to ${newStatus}`);
    } catch (err) {
        alert("Update Error: " + describeError(err));
    } finally {
        // Refresh the table, metrics (including revenue), and payment-status column so
        // everything reflects the change regardless of success or failure.
        fetchAdminOrders();
    }
}

async function fetchAdminDiscounts() {
    const container = document.getElementById("adminDiscountsList");
    if (!container) return;
    const token = getToken();
    try {
        const res = await authFetch(ADMIN_DISCOUNTS_URL, {
            headers: { "Authorization": "Bearer " + token }
        });
        const discounts = await res.json();
        if (!res.ok) throw new Error(discounts.message || "Failed to fetch discounts");

        allDiscountsList = discounts;

        if (discounts.length === 0) {
            container.innerHTML = `<p style="color: #94a3b8; padding: 2rem; text-align: center;">No store discounts found.</p>`;
            return;
        }

        const now = new Date();

        container.innerHTML = `
            <table class="data-table">
                <thead>
                    <tr>
                        <th>ID</th>
                        <th>Offer Name</th>
                        <th>Scope & Type</th>
                        <th>Discount Value</th>
                        <th>Target Area / Product</th>
                        <th>Birthday Special</th>
                        <th>Status</th>
                        <th>Actions</th>
                    </tr>
                </thead>
                <tbody>
                    ${discounts.map(d => {
                        const from = new Date(d.validFrom);
                        const until = new Date(d.validUntil);
                        let statusLabel, statusColor;
                        if (now < from) {
                            statusLabel = "⏳ Upcoming";
                            statusColor = "#0369a1";
                        } else if (now > until) {
                            statusLabel = "⚠️ Expired";
                            statusColor = "#ef4444";
                        } else {
                            statusLabel = "✅ Active";
                            statusColor = "#10b981";
                        }
                        return `
                        <tr>
                            <td><strong>#${d.discountId}</strong></td>
                            <td><strong>${d.name}</strong></td>
                            <td><span style="background: #f1f5f9; padding: 0.2rem 0.5rem; border-radius: 4px; font-weight: 600;">${d.targetScope} (${d.discountType})</span></td>
                            <td><strong style="color: #10b981;">${d.discountType === 'PERCENTAGE' ? d.discountValue + '%' : '₹' + d.discountValue}</strong></td>
                            <td>${d.targetScope === 'PRODUCT'
                                ? `<span style="background: #fef3c7; color: #92400e; padding: 0.2rem 0.5rem; border-radius: 4px; font-weight: 700;">${(() => { const p = allProductsList.find(pr => pr.productId === d.productId); return p ? p.name : (d.productId ? `Product #${d.productId}` : 'No product selected ⚠️'); })()}</span>`
                                : `<span style="background: #e0f2fe; color: #0369a1; padding: 0.2rem 0.5rem; border-radius: 4px; font-weight: 700;">${d.targetPincode || 'ALL AREAS'}</span>`
                            }</td>
                            <td>${d.birthdayOnly ? '🎂 YES' : 'NO'}</td>
                            <td><span style="color: ${statusColor}; font-weight: 700;" title="${from.toLocaleString()} → ${until.toLocaleString()}">${statusLabel}</span></td>
                            <td>
                                <div style="display: flex; gap: 0.35rem;">
                                    <button onclick="editDiscount(${d.discountId})" style="padding: 0.3rem 0.6rem; background: #e0f2fe; color: #0369a1; border: none; border-radius: 6px; font-weight: 700; cursor: pointer;">Edit</button>
                                    <button onclick="deleteDiscount(${d.discountId})" style="padding: 0.3rem 0.6rem; background: #fee2e2; color: #991b1b; border: none; border-radius: 6px; font-weight: 700; cursor: pointer;">Delete</button>
                                </div>
                            </td>
                        </tr>
                    `;
                    }).join("")}
                </tbody>
            </table>
        `;
    } catch (err) {
        container.innerHTML = `<p style="color: #ef4444; padding: 2rem; text-align: center;">Error: ${describeError(err)}</p>`;
    }
}

// Formats a JS Date into the "YYYY-MM-DDTHH:mm" value a <input type="datetime-local"> expects,
// using local time components (matches what the input displays to the admin).
function toDatetimeLocalValue(date) {
    const pad = n => String(n).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function openDiscountModal(discount = null) {
    const modal = document.getElementById("discountModal");
    const title = document.getElementById("discountModalTitle");
    const form = document.getElementById("createDiscountForm");

    if (discount) {
        title.textContent = "✏️ Edit Discount #" + discount.discountId;
        document.getElementById("discId").value = discount.discountId;
        document.getElementById("discName").value = discount.name;
        document.getElementById("discType").value = discount.discountType;
        document.getElementById("discValue").value = discount.discountValue;
        document.getElementById("discScope").value = discount.targetScope;
        document.getElementById("discMinOrder").value = discount.minOrderAmount != null ? discount.minOrderAmount : "";
        document.getElementById("discPincode").value = discount.targetPincode || "";
        document.getElementById("discIsBirthday").checked = Boolean(discount.birthdayOnly);
        document.getElementById("discValidFrom").value = toDatetimeLocalValue(new Date(discount.validFrom));
        document.getElementById("discValidUntil").value = toDatetimeLocalValue(new Date(discount.validUntil));
        populateDiscountProductOptions(discount.productId || null);
    } else {
        title.textContent = "+ Configure New Discount Offer";
        form.reset();
        document.getElementById("discId").value = "";
        document.getElementById("discValidFrom").value = toDatetimeLocalValue(new Date());
        document.getElementById("discValidUntil").value = toDatetimeLocalValue(new Date(Date.now() + 30 * 24 * 60 * 60 * 1000));
        populateDiscountProductOptions(null);
    }
    // Must run after discScope's value is set above (edit) or reset (add) -- it reads the
    // current selection to decide whether to show the product picker or the pincode field.
    toggleDiscountScopeFields();
    modal.classList.add("open");
}

// Shows the product picker for PRODUCT-scope discounts, pincode field otherwise.
function toggleDiscountScopeFields() {
    const scope = document.getElementById("discScope").value;
    const isProduct = scope === "PRODUCT";
    const productGroup = document.getElementById("discProductGroup");
    const pincodeGroup = document.getElementById("discPincodeGroup");
    const productSelect = document.getElementById("discProductId");

    if (productGroup) productGroup.style.display = isProduct ? "block" : "none";
    if (pincodeGroup) pincodeGroup.style.display = isProduct ? "none" : "block";
    if (productSelect) {
        if (isProduct) {
            productSelect.setAttribute("required", "required");
        } else {
            productSelect.removeAttribute("required");
            productSelect.value = "";
        }
    }
}

// Rebuilds the product dropdown from allProductsList (populated by fetchAdminProducts).
function populateDiscountProductOptions(selectedProductId) {
    const select = document.getElementById("discProductId");
    if (!select) return;
    const placeholderSelected = !selectedProductId ? "selected" : "";
    select.innerHTML = `<option value="" disabled ${placeholderSelected}>Select a product</option>` +
        allProductsList.map(p => `<option value="${p.productId}" ${String(selectedProductId) === String(p.productId) ? "selected" : ""}>${p.name}</option>`).join("");
}

function closeDiscountModal() {
    document.getElementById("discountModal").classList.remove("open");
}

function editDiscount(discountId) {
    const discount = allDiscountsList.find(d => d.discountId === discountId);
    if (discount) openDiscountModal(discount);
}

async function deleteDiscount(discountId) {
    if (!confirm(`Are you sure you want to delete Discount #${discountId}?`)) return;
    const token = localStorage.getItem("token");
    try {
        const res = await authFetch(`${ADMIN_DISCOUNTS_URL}?id=${discountId}`, {
            method: "DELETE",
            headers: { "Authorization": "Bearer " + token }
        });
        const result = await res.json();
        if (!res.ok) throw new Error(result.message || "Failed to delete discount");
        alert("🗑️ Discount deleted successfully!");
        fetchAdminDiscounts();
    } catch (err) {
        alert("Delete Error: " + describeError(err));
    }
}

async function handleSaveDiscount(event) {
    event.preventDefault();
    const token = localStorage.getItem("token");
    const id = document.getElementById("discId").value;
    const isEdit = Boolean(id);
    const scope = document.getElementById("discScope").value;
    const productIdRaw = document.getElementById("discProductId").value;

    // Client-side check to avoid a round-trip; the backend also enforces this.
    if (scope === "PRODUCT" && !productIdRaw) {
        alert("Please select which product this discount applies to.");
        return;
    }

    const discountPayload = {
        name: document.getElementById("discName").value,
        discountType: document.getElementById("discType").value,
        discountValue: parseFloat(document.getElementById("discValue").value),
        targetScope: scope,
        minOrderAmount: parseFloat(document.getElementById("discMinOrder").value) || null,
        targetPincode: document.getElementById("discPincode").value || null,
        birthdayOnly: document.getElementById("discIsBirthday").checked,
        validFrom: document.getElementById("discValidFrom").value,
        validUntil: document.getElementById("discValidUntil").value,
        productId: scope === "PRODUCT" ? parseInt(productIdRaw) : null
    };

    if (isEdit) {
        discountPayload.discountId = parseInt(id);
    }

    try {
        const res = await authFetch(ADMIN_DISCOUNTS_URL, {
            method: isEdit ? "PUT" : "POST",
            headers: {
                "Content-Type": "application/json",
                "Authorization": "Bearer " + token
            },
            body: JSON.stringify(discountPayload)
        });

        const result = await res.json();
        if (!res.ok) throw new Error(result.message || "Failed to save discount");

        alert(`🎉 Discount Offer ${isEdit ? 'Updated' : 'Created'} & Live in Database!`);
        closeDiscountModal();
        document.getElementById("createDiscountForm").reset();
        fetchAdminDiscounts();

    } catch (err) {
        alert("Error Saving Discount: " + describeError(err));
    }
}

/* ==========================================================================
   4. STOCK (PRODUCT BATCHES) MANAGEMENT — FEFO inventory, per-product batches
   ========================================================================== */

function openBatchManageModal(productId) {
    const product = allProductsList.find(p => p.productId === productId);
    const title = document.getElementById("batchManageModalTitle");
    title.textContent = "📦 Manage Stock" + (product ? " — " + product.name : "");

    document.getElementById("batchProductId").value = productId;
    resetBatchForm();

    batchModalViewMode = 'batches';
    document.getElementById("batchViewToggleBtn").textContent = "📜 View Stock History";
    document.getElementById("batchForm").style.display = "block";

    document.getElementById("batchManageModal").classList.add("open");
    fetchProductBatches(productId);
}

// Toggles the modal between the editable batch list and the read-only stock movement
// history for the same product. History is view-only — no add/edit/delete there.
function toggleBatchView() {
    const productId = parseInt(document.getElementById("batchProductId").value);
    const btn = document.getElementById("batchViewToggleBtn");
    const form = document.getElementById("batchForm");

    if (batchModalViewMode === 'batches') {
        batchModalViewMode = 'history';
        btn.textContent = "📦 Back to Batches";
        form.style.display = "none";
        fetchStockMovements(productId);
    } else {
        batchModalViewMode = 'batches';
        btn.textContent = "📜 View Stock History";
        form.style.display = "block";
        fetchProductBatches(productId);
    }
}

async function fetchStockMovements(productId) {
    const container = document.getElementById("batchListContainer");
    const token = getToken();
    container.innerHTML = `<p style="color: #94a3b8; text-align: center; padding: 1rem;">Loading history...</p>`;

    try {
        const res = await authFetch(`${ADMIN_BATCHES_URL}/movements?productId=${productId}`, {
            headers: { "Authorization": "Bearer " + token }
        });
        const movements = await res.json();
        if (!res.ok) throw new Error(movements.message || "Failed to load stock history");

        if (movements.length === 0) {
            container.innerHTML = `<p style="color: #94a3b8; text-align: center; padding: 1rem;">No stock movements recorded yet — history fills in as orders are placed.</p>`;
            return;
        }

        container.innerHTML = `
            <table class="data-table">
                <thead>
                    <tr>
                        <th>Date</th>
                        <th>Type</th>
                        <th>Batch #</th>
                        <th>Change</th>
                    </tr>
                </thead>
                <tbody>
                    ${movements.map(m => {
                        const isSale = m.movementType === 'SALE';
                        return `
                        <tr>
                            <td style="font-size: 0.8rem; color: #64748b;">${new Date(m.createdAt).toLocaleString()}</td>
                            <td><span style="background: ${isSale ? '#fee2e2' : '#dcfce7'}; color: ${isSale ? '#991b1b' : '#166534'}; padding: 0.2rem 0.5rem; border-radius: 4px; font-weight: 600; font-size: 0.8rem;">${m.movementType}</span></td>
                            <td>#${m.batchId}</td>
                            <td><strong style="color: ${m.quantityChange < 0 ? '#ef4444' : '#10b981'};">${m.quantityChange > 0 ? '+' : ''}${m.quantityChange}</strong></td>
                        </tr>
                    `;
                    }).join("")}
                </tbody>
            </table>
        `;
    } catch (err) {
        container.innerHTML = `<p style="color: #ef4444; text-align: center; padding: 1rem;">Error: ${describeError(err)}</p>`;
    }
}

function closeBatchManageModal() {
    document.getElementById("batchManageModal").classList.remove("open");
}

async function fetchProductBatches(productId) {
    const container = document.getElementById("batchListContainer");
    const token = getToken();
    container.innerHTML = `<p style="color: #94a3b8; text-align: center; padding: 1rem;">Loading batches...</p>`;

    try {
        const res = await authFetch(`${ADMIN_BATCHES_URL}?productId=${productId}`, {
            headers: { "Authorization": "Bearer " + token }
        });
        const batches = await res.json();
        if (!res.ok) throw new Error(batches.message || "Failed to load batches");

        currentBatchList = batches;

        if (batches.length === 0) {
            container.innerHTML = `
                <table class="data-table">
                    <thead>
                        <tr>
                            <th>Source</th>
                            <th>Batch Date</th>
                            <th>Expiry</th>
                            <th>Qty</th>
                            <th>Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr>
                            <td colspan="5" style="text-align: center; color: #94a3b8; padding: 1.5rem;">
                                No active stock batches found. Add stock below.
                            </td>
                        </tr>
                    </tbody>
                </table>
            `;
            return;
        }

        container.innerHTML = `
            <table class="data-table">
                <thead>
                    <tr>
                        <th>Source</th>
                        <th>Batch Date</th>
                        <th>Expiry</th>
                        <th>Qty</th>
                        <th>Actions</th>
                    </tr>
                </thead>
                <tbody>
                    ${batches.map(b => `
                        <tr>
                            <td>${b.source || '-'}</td>
                            <td>${b.batchDate}</td>
                            <td>${b.expiryDate || '-'}</td>
                            <td><strong>${b.availableQuantity}</strong></td>
                            <td>
                                <div style="display: flex; gap: 0.35rem;">
                                    <button onclick="editBatch(${b.batchId})" style="padding: 0.3rem 0.6rem; background: #e0f2fe; color: #0369a1; border: none; border-radius: 6px; font-weight: 700; cursor: pointer;">Edit</button>
                                    <button onclick="deleteBatch(${b.batchId})" style="padding: 0.3rem 0.6rem; background: #fee2e2; color: #991b1b; border: none; border-radius: 6px; font-weight: 700; cursor: pointer;">Delete</button>
                                </div>
                            </td>
                        </tr>
                    `).join("")}
                </tbody>
            </table>
        `;
    } catch (err) {
        container.innerHTML = `<p style="color: #ef4444; text-align: center; padding: 1rem;">Error: ${describeError(err)}</p>`;
    }
}

function editBatch(batchId) {
    const batch = currentBatchList.find(b => b.batchId === batchId);
    if (!batch) return;

    document.getElementById("batchId").value = batch.batchId;
    document.getElementById("batchSource").value = batch.source || "";
    document.getElementById("batchDate").value = batch.batchDate;
    document.getElementById("batchExpiry").value = batch.expiryDate || "";
    document.getElementById("batchQuantity").value = batch.availableQuantity;
    document.getElementById("batchFormTitle").textContent = "✏️ Edit Batch #" + batch.batchId;
    document.getElementById("batchFormCancelBtn").style.display = "inline-block";
}

function resetBatchForm() {
    const form = document.getElementById("batchForm");
    const productId = document.getElementById("batchProductId").value;
    form.reset();
    document.getElementById("batchId").value = "";
    document.getElementById("batchProductId").value = productId;
    document.getElementById("batchDate").value = new Date().toISOString().split("T")[0];
    document.getElementById("batchFormTitle").textContent = "+ Add New Batch";
    document.getElementById("batchFormCancelBtn").style.display = "none";
}

async function handleSaveBatch(event) {
    event.preventDefault();
    const token = getToken();
    const id = document.getElementById("batchId").value;
    const isEdit = Boolean(id);
    const productId = parseInt(document.getElementById("batchProductId").value);

    const batchPayload = {
        productId: productId,
        source: document.getElementById("batchSource").value,
        batchDate: document.getElementById("batchDate").value,
        expiryDate: document.getElementById("batchExpiry").value || null,
        availableQuantity: parseInt(document.getElementById("batchQuantity").value)
    };

    if (isEdit) {
        batchPayload.batchId = parseInt(id);
    }

    try {
        const res = await authFetch(ADMIN_BATCHES_URL, {
            method: isEdit ? "PUT" : "POST",
            headers: {
                "Content-Type": "application/json",
                "Authorization": "Bearer " + token
            },
            body: JSON.stringify(batchPayload)
        });

        const result = await res.json();
        if (!res.ok) throw new Error(result.message || "Failed to save batch");

        resetBatchForm();
        fetchProductBatches(productId);
        fetchAdminProducts(); // refresh the Stock column total shown in the products table
    } catch (err) {
        alert("Error saving batch: " + describeError(err));
    }
}

async function deleteBatch(batchId) {
    if (!confirm("Are you sure you want to delete this stock batch?")) return;
    const token = getToken();
    const productId = parseInt(document.getElementById("batchProductId").value);

    try {
        const res = await authFetch(`${ADMIN_BATCHES_URL}?id=${batchId}`, {
            method: "DELETE",
            headers: { "Authorization": "Bearer " + token }
        });
        const result = await res.json();
        if (!res.ok) throw new Error(result.message || "Failed to delete batch");

        fetchProductBatches(productId);
        fetchAdminProducts();
    } catch (err) {
        alert("Delete Error: " + describeError(err));
    }
}
