// ShopSphere My Orders Page Logic

const ORDERS_API = "http://localhost:8080/ShopSphere-Backend/orders";

document.addEventListener("DOMContentLoaded", () => {
    fetchOrders();
});

async function fetchOrders() {
    const token = localStorage.getItem("token");
    const ordersList = document.getElementById("ordersList");

    if (!token) {
        ordersList.innerHTML = `
            <div style="text-align: center; padding: 3rem;">
                <p style="color: #64748b; margin-bottom: 1rem;">Please log in to view your order history.</p>
                <a href="login.html" style="padding: 0.6rem 1.2rem; background: #10b981; color: white; border-radius: 6px; text-decoration: none; font-weight: 600;">Go to Login</a>
            </div>
        `;
        return;
    }

    try {
        const response = await authFetch(ORDERS_API, {
            headers: {
                "Authorization": "Bearer " + token
            }
        });

        const orders = await response.json();

        if (!response.ok) {
            throw new Error(orders.message || "Failed to fetch orders");
        }

        if (orders.length === 0) {
            ordersList.innerHTML = `
                <div style="text-align: center; padding: 3rem; background: white; border-radius: 12px; border: 1px solid #e2e8f0;">
                    <p style="color: #94a3b8; font-size: 1.1rem; margin-bottom: 1rem;">You have not placed any orders yet.</p>
                    <a href="products.html" style="padding: 0.6rem 1.2rem; background: #10b981; color: white; border-radius: 6px; text-decoration: none; font-weight: 600;">Start Shopping</a>
                </div>
            `;
            return;
        }

        ordersList.innerHTML = orders.map(order => {
            const statusClass = `status-${(order.status || 'PLACED').toLowerCase()}`;
            const formattedDate = order.createdAt ? new Date(order.createdAt).toLocaleString() : 'Recent';

            const itemsHtml = (order.orderItems || []).map(item => `
                <div class="order-item-row">
                    <span>${item.productName || ('Product #' + item.productId)} × ${item.quantity}</span>
                    <strong>₹${(item.pricePerUnit * item.quantity).toFixed(2)}</strong>
                </div>
            `).join("");

            return `
                <div class="order-card">
                    <div class="order-header">
                        <div>
                            <span class="order-id">Order #${order.orderId}</span>
                            <div class="order-date">Placed on ${formattedDate}</div>
                        </div>
                        <div style="text-align: right;">
                            <span class="status-badge ${statusClass}">${order.status}</span>
                            ${order.status === 'PLACED' ? `
                                <div style="margin-top: 0.5rem;">
                                    <button type="button" class="btn-cancel-order" onclick="cancelMyOrder(${order.orderId})"
                                        style="padding: 0.35rem 0.8rem; background: #fee2e2; color: #b91c1c; border: 1px solid #fecaca; border-radius: 6px; font-weight: 600; font-size: 0.8rem; cursor: pointer;">
                                        Cancel Order
                                    </button>
                                </div>
                            ` : ''}
                        </div>
                    </div>

                    <div class="order-items-list">
                        ${itemsHtml}
                    </div>

                    <div class="order-footer">
                        <div>
                            <span style="font-size: 0.85rem; color: #64748b;">Payment Method:</span>
                            <strong style="font-size: 0.85rem; color: #334155;">${order.paymentMethod || 'CASH_ON_DELIVERY'}</strong>
                            <span style="margin: 0 0.5rem;">•</span>
                            <span style="font-size: 0.85rem; color: ${order.paymentStatus === 'REFUNDED' ? '#f59e0b' : '#10b981'}; font-weight: 600;">${order.paymentStatus || 'PAID'}</span>
                        </div>
                        <div>
                            <span style="font-size: 0.9rem; color: #64748b;">Total Amount:</span>
                            <span class="total-price">₹${(order.finalAmount || order.totalAmount || 0).toFixed(2)}</span>
                        </div>
                    </div>
                </div>
            `;
        }).join("");

    } catch (err) {
        ordersList.innerHTML = `<p style="color: #ef4444; text-align: center; padding: 2rem;">Error: ${describeError(err)}</p>`;
    }
}

// Customer-initiated cancellation. Only ever called from a button rendered while the order is
// still PLACED (see fetchOrders above) — the backend re-validates that same rule server-side.
async function cancelMyOrder(orderId) {
    const confirmed = confirm(
        `Cancel order #${orderId}? This cannot be undone.`
    );
    if (!confirmed) return;

    const token = localStorage.getItem("token");
    try {
        const response = await authFetch(`${ORDERS_API}/cancel`, {
            method: "PUT",
            headers: {
                "Content-Type": "application/json",
                "Authorization": "Bearer " + token
            },
            body: JSON.stringify({ orderId: orderId })
        });

        const text = await response.text();
        const result = text ? JSON.parse(text) : {};
        if (!response.ok) {
            throw new Error(result.message || "Failed to cancel order");
        }

        alert(`Order #${orderId} has been cancelled.`);
        fetchOrders();
    } catch (err) {
        alert("Cancel Error: " + describeError(err));
    }
}
