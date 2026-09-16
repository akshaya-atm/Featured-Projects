# 🛒 ShopSphere — Full-Stack E-Commerce Platform with Agentic AI

**ShopSphere** is a full-stack e-commerce application built with **Java Servlets (Jakarta EE)**, **PostgreSQL with Flyway migrations**, **HikariCP connection pooling**, **JJWT security**, and an **agentic AI shopping assistant powered by LangChain4j and Groq LLMs**.

The application orchestrates HTTP request lifecycles, database connection pools, pessimistic concurrency control, and stateless JWT security filters using raw Jakarta Servlets and JDBC.

---

## 🛠️ Key Features & Architecture Highlights

- **Framework-Free Servlet Architecture**: Implemented on Jakarta Servlets 6.1 and JDBC for explicit control over request dispatching, memory usage, and lifecycle filters.
- **FEFO Inventory Allocation & Concurrency Control**: First-Expired-First-Out (FEFO) batch selection algorithm paired with database pessimistic row locking (`SELECT ... FOR UPDATE`) to prevent overselling during concurrent checkout transactions.
- **Agentic AI Shopping Assistant**: Integrated chatbot powered by **LangChain4j** and **Groq LLM** with **11 custom Java function tools** (`ChatTools.java`) enabling natural language catalog search, cart management, checkout execution, and admin clearance analytics.
- **Database Versioning & Stock Audit Ledger**: Schema management via **Flyway SQL migrations**, backed by transactional audit logs (`stock_movements`) tracking all inventory deductions, restorations, and cancellations.
- **Dynamic Discount Engine**: Multi-tiered promo engine evaluating pincode eligibility, birthday bonuses, volume thresholds, and subtotal caps.
- **Role-Based Security & CORS**: Stateless authentication using **JJWT**, **BCrypt** password hashing, allowlisted localhost origin matching (`CorsFilter.java`), and role-based Servlet authorization filters (`AdminFilter`, `UserAuthFilter`).

---

## 🏗️ System Architecture

```
                             +-------------------------------+
                             |    Frontend (Vanilla Web)     |
                             |  HTML5 / CSS3 / ES6 Modular   |
                             +---------------+---------------+
                                             |
                                     HTTP / REST API
                                             |
                                             v
                             +---------------+---------------+
                             |    Jakarta Servlet Engine     |
                             | (UserAuthFilter / AdminFilter)|
                             |          (CorsFilter)         |
                             +---------------+---------------+
                                             |
                 +---------------------------+---------------------------+
                 |                                                       |
                 v                                                       v
+-------------------------------+                       +-------------------------------+
|     Business Logic Layer      |                       |    LangChain4j AI Assistant    |
| OrderService / ProductService |                       | (11 Custom Tool Functions)   |
+----------------+--------------+                       +----------------+--------------+
                 |                                                       |
                 v                                                       v
+-------------------------------+                       +-------------------------------+
|    HikariCP Connection Pool   |                       |         Groq LLM API          |
+----------------+--------------+                       +-------------------------------+
                 |
                 v
+-------------------------------+
|   PostgreSQL Database Engine  |
|   (Flyway SQL Migrations)     |
+-------------------------------+
```

---

## 🗂️ Project Structure

```text
ShopSphere/
├── LICENSE                    # MIT License file
├── ShopSphere-Backend/
│   ├── src/main/java/com/akshaya/shopsphere/
│   │   ├── address/          # Shipping address repository, service, & servlets
│   │   ├── auth/             # Login servlet, authentication service, & repo
│   │   ├── category/         # Category management & servlets
│   │   ├── chat/             # LangChain4j AI service, ChatTools.java, & ChatServlet
│   │   ├── common/           # JwtUtil, PasswordService, AdminFilter, UserAuthFilter, CorsFilter
│   │   ├── db/               # HikariCP DatabaseConnection & DatabaseInitializer
│   │   ├── discount/         # Order & product discount evaluators & servlets
│   │   ├── order/            # Order processing, FEFO checkout logic, & servlets
│   │   ├── product/          # Product batches, ProductBatchRepository, & stock ledger
│   │   └── registration/     # User registration servlets & service
│   └── src/main/resources/
│       └── db/migration/     # V1..V4 Flyway SQL database migration scripts
└── ShopSphere-Frontend/
    ├── admin.html / admin.js # Admin dashboard & batch inventory manager
    ├── index.html / index.js # Customer storefront
    ├── products.js / orders.js # Product catalog & order tracking modules
    ├── common.js             # Shared BASE_API_URL, fetch wrapper, & JWT header handling
    └── chatbot.css           # Floating AI assistant UI styles
```

---

## 🔐 AI Tool Authorization & Prompt Injection Isolation

The AI assistant operates via `ChatServlet.java` (`POST /ShopSphere-Backend/chat`), which is accessible to both guests and authenticated users. Security, CORS, and privilege boundaries are enforced **server-side**, independent of the LLM prompt:

- **Cross-Origin Resource Sharing (CORS)**: Servlets are wrapped by `CorsFilter.java` (`@WebFilter("/*")`), matching incoming `Origin` headers against allowlisted localhost patterns (`^http://(localhost|127\.0\.0\.1)(:\d+)?$`) to permit cross-origin requests from frontend static servers (e.g., `localhost:8000` or Live Server).
- **Prompt Injection Resilience**: The LLM prompt is never trusted to police user privileges. Prompt instructions claiming *"I am an admin"* or attempting to trigger admin tools (such as `get_clearance_candidates`) are neutralized because privileges are verified exclusively against the validated JWT session (`CurrentUser.isAdmin()`) in Java tool code.
- **Context Injection**: Each chat session injects a `CurrentUser` context object populated directly from the validated JWT request token.
- **Admin Isolation**: Admin-only capabilities verify `currentUser.isAdmin()`. If an unauthenticated or non-admin user prompts the LLM to run admin tools, the Java tool function immediately rejects execution with an unauthorized exception.

### 🎭 Role-Based Dynamic Tool Injection

To guarantee that non-admin users cannot hallucinate or force the AI to execute administrative actions, the platform implements **Role-Based Dynamic Tool Injection** using a multi-instance routing strategy in `ChatServlet.java`:

1. **`guestAiService`**: Instantiated exclusively with `GuestChatTools` (product search, categories).
2. **`userAiService`**: Instantiated with `GuestChatTools` + `UserChatTools` (cart, checkout, addresses).
3. **`adminAiService`**: Instantiated with `GuestChatTools` + `UserChatTools` + `AdminChatTools` (inventory analytics, discount creation).

During the HTTP `/chat` request lifecycle, the servlet resolves the user's JWT role and dynamically routes the prompt to the corresponding AI service instance. This physically airgaps the tool schemas—it is fundamentally impossible for a regular user to prompt-inject an admin tool because the LLM instance they interact with is completely unaware that admin tools exist.

---

## 📦 Inventory Management & Concurrency Control

To handle perishable inventory and prevent overselling during concurrent checkout transactions:

1. **FEFO Allocation Algorithm**: `ProductBatchRepository` selects active product batches matching the requested item, sorted by `expiration_date ASC`.
2. **Pessimistic Row Locking**: Candidate batch rows are queried using `SELECT ... FOR UPDATE`, locking specific batch rows in PostgreSQL until the checkout transaction commits or rolls back.
3. **Transactional Rollback & Atomicity**: If stock is insufficient or any checkout step fails, the transaction executes `connection.rollback()`, preserving transactional atomicity and database consistency.
4. **Audit Ledger**: All stock adjustments write entries into `stock_movements` with movement types (`SALE`, `RESTORE`, `CANCELLATION`).

---

## 🧪 Testing & Concurrency Verification

- **Pessimistic Locking Verification**: Validated concurrent checkout behavior under simulated parallel threads competing for limited batch inventory. Verified that PostgreSQL row locks (`FOR UPDATE`) serialize batch deductions, preventing negative stock levels and duplicate allocations.
- **FEFO Batch Allocation Tests**: Verified that checkout transactions deduct units from nearest-expiring batches first before pulling from later batches.
- **Transactional Compensation Tests**: Tested forced checkout failures mid-transaction to confirm automatic database rollback and stock movement audit ledger accuracy.

---

## 📡 REST API Specifications & Examples

*Note: Default WAR deployment to Apache Tomcat maps endpoints under the `/ShopSphere-Backend` context path.*

### 1. Authentication (`POST /ShopSphere-Backend/login`)

**Request:**
```http
POST /ShopSphere-Backend/login HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "username": "customer@example.com",
  "password": "Password123!"
}
```

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "user": {
    "id": 42,
    "email": "customer@example.com",
    "role": "CUSTOMER"
  }
}
```

### 2. Product Query (`GET /ShopSphere-Backend/products?category=Dairy%20%26%20Bakery`)

**Request:**
```http
GET /ShopSphere-Backend/products?category=Dairy%20%26%20Bakery HTTP/1.1
Host: localhost:8080
Accept: application/json
```

**Response (200 OK):**
```json
[
  {
    "id": 101,
    "name": "Organic Whole Milk",
    "category": "Dairy & Bakery",
    "isPerishable": true,
    "unit": "L",
    "price": 3.99,
    "totalAvailableStock": 45
  }
]
```

### 3. AI Chat Assistant (`POST /ShopSphere-Backend/chat`)

**Request:**
```http
POST /ShopSphere-Backend/chat HTTP/1.1
Host: localhost:8080
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Content-Type: application/json

{
  "message": "Add 2 liters of Organic Whole Milk to my cart"
}
```

**Response (200 OK):**
```json
{
  "response": "I've added 2 units of Organic Whole Milk to your cart. Your current subtotal is $7.98."
}
```

---

## ⚙️ Known Limitations & Future Enhancements

- **CORS Production Config**: The active `CorsFilter.java` uses regex pattern matching for local development (`localhost` / `127.0.0.1`). Production deployments should inject an environment-driven explicit origin allowlist.
- **LLM Endpoint Rate Limiting**: The public/user `/chat` endpoint relies on standard Groq API keys without server-side IP rate limiting. Adding a sliding-window rate limiter (e.g. Bucket4j or Redis rate-limiting filter) is a planned enhancement for production abuse prevention.
- **Stateless JWT Expiry**: Current JWT tokens are signed statelessly without a refresh token token-rotation strategy or token-revocation database blacklist.

---

## 🛠️ Tech Stack & Requirements

| Component | Technology | Version / Specification |
| :--- | :--- | :--- |
| **Language & Runtime** | Java | **Java 17+** |
| **Servlet Specification** | Jakarta EE Servlet API | **Jakarta Servlet 6.1** |
| **Application Server** | Apache Tomcat | **Apache Tomcat 11+** (implements Servlet 6.1) |
| **Database** | PostgreSQL | PostgreSQL 14+ with Flyway v10.10.0 |
| **Connection Pooling** | HikariCP | v5.1.0 |
| **Authentication** | JJWT + jBCrypt | `jjwt` v0.12.5, `jBCrypt` v0.4 |
| **AI Engine** | LangChain4j + Groq LLM | `langchain4j` v1.19.0 |

---

## 🚀 Getting Started

### Prerequisites
- **Java 17+** & **Maven 3.8+**
- **PostgreSQL 14+**
- **Apache Tomcat 11+**
- **Groq API Key**

### 1. Database Setup
Create a PostgreSQL database named `shopsphere`:
```sql
CREATE DATABASE shopsphere;
```

### 2. Configure Environment Variables
Set the required environment variables:
```bash
# Database Configuration
export SHOPSPHERE_DB_URL="jdbc:postgresql://localhost:5432/shopsphere"
export SHOPSPHERE_DB_USER="postgres"
export SHOPSPHERE_DB_PASSWORD="your_postgres_password"

# Security & Secrets
export JWT_SECRET="your_secure_random_jwt_signing_secret_key_32bytes"

# AI Integration
export GROQ_API_KEY="your_groq_api_key"
```

### 3. Build & Deploy Backend
Navigate to `ShopSphere-Backend` and package the WAR file:
```bash
cd ShopSphere-Backend
mvn clean package
```
Deploy `target/ShopSphere-Backend.war` to **Apache Tomcat 11+** (`webapps/` directory). The application context path will be `/ShopSphere-Backend`. Flyway will execute SQL schema migrations automatically on application startup.

*(Note: To deploy at root context `http://localhost:8080/`, rename `ShopSphere-Backend.war` to `ROOT.war` before copying to Tomcat's `webapps/` folder.)*

### 4. Run Frontend
Because the frontend uses modular ES6 JavaScript, serve the `ShopSphere-Frontend` directory using any local static HTTP server:

```bash
# Using Node npx serve:
cd ShopSphere-Frontend
npx serve .

# Or using Python 3:
python3 -m http.server 8000
```
Open `http://localhost:8000` in your browser. Cross-origin requests to `http://localhost:8080/ShopSphere-Backend` are automatically allowed by `CorsFilter.java`.

---

## 📝 License
This project is open source and available under the [MIT License](LICENSE).
