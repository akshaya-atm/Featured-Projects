-- ShopSphere Consolidated Database Schema & Seed Data (Single Migration V1)

-- 1. DROP EXISTING TABLES FOR CLEAN DATABASE INITIALIZATION
DROP TABLE IF EXISTS order_items CASCADE;
DROP TABLE IF EXISTS orders CASCADE;
DROP TABLE IF EXISTS cart_items CASCADE;
DROP TABLE IF EXISTS carts CASCADE;
DROP TABLE IF EXISTS product_discounts CASCADE;
DROP TABLE IF EXISTS discounts CASCADE;
DROP TABLE IF EXISTS product_batches CASCADE;
DROP TABLE IF EXISTS product_categories CASCADE;
DROP TABLE IF EXISTS products CASCADE;
DROP TABLE IF EXISTS categories CASCADE;
DROP TABLE IF EXISTS addresses CASCADE;
DROP TABLE IF EXISTS auth_info CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- 2. CREATE DDL SCHEMAS
CREATE TABLE users (
    user_id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    contact VARCHAR(20) NOT NULL,
    birthday DATE NOT NULL,
    pincode VARCHAR(20) DEFAULT '600001',
    user_type VARCHAR(50) DEFAULT 'CUSTOMER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE auth_info (
    auth_id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE addresses (
    address_id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL,
    house_no VARCHAR(100),
    street VARCHAR(255),
    landmark VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(100),
    pincode VARCHAR(20),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE categories (
    category_id SERIAL PRIMARY KEY,
    category_name VARCHAR(255) UNIQUE NOT NULL,
    category_description VARCHAR(255),
    category_image_url VARCHAR(500)
);

CREATE TABLE products (
    product_id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price DECIMAL(10, 2) NOT NULL,
    unit_value VARCHAR(50),
    image_url VARCHAR(500)
);

CREATE TABLE product_categories (
    product_id INTEGER NOT NULL,
    category_id INTEGER NOT NULL,
    PRIMARY KEY (product_id, category_id),
    FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE,
    FOREIGN KEY (category_id) REFERENCES categories(category_id) ON DELETE CASCADE
);

CREATE TABLE product_batches (
    batch_id SERIAL PRIMARY KEY,
    product_id INTEGER NOT NULL,
    source VARCHAR(255),
    batch_date DATE NOT NULL,
    expiry_date DATE,
    available_quantity INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE
);

CREATE TABLE discounts (
    discount_id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    discount_type VARCHAR(50) NOT NULL,
    discount_value DECIMAL(10, 2) NOT NULL,
    target_scope VARCHAR(50) NOT NULL,
    min_order_amount DECIMAL(10, 2),
    target_pincode VARCHAR(20),
    target_user_id INTEGER,
    is_birthday_only BOOLEAN DEFAULT FALSE,
    valid_from TIMESTAMP NOT NULL,
    valid_until TIMESTAMP NOT NULL
);

CREATE TABLE product_discounts (
    product_id INTEGER NOT NULL,
    discount_id INTEGER NOT NULL,
    PRIMARY KEY (product_id, discount_id),
    FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE,
    FOREIGN KEY (discount_id) REFERENCES discounts(discount_id) ON DELETE CASCADE
);

CREATE TABLE carts (
    cart_id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL UNIQUE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE cart_items (
    cart_item_id SERIAL PRIMARY KEY,
    cart_id INTEGER NOT NULL,
    product_id INTEGER NOT NULL,
    quantity DECIMAL(10, 2) NOT NULL DEFAULT 1,
    FOREIGN KEY (cart_id) REFERENCES carts(cart_id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE
);

CREATE TABLE orders (
    order_id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL,
    address_id INTEGER,
    total_amount DECIMAL(10, 2) NOT NULL,
    discount_amount DECIMAL(10, 2) DEFAULT 0.00,
    final_amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(50) DEFAULT 'PLACED',
    payment_status VARCHAR(50) DEFAULT 'PAID',
    payment_method VARCHAR(50) DEFAULT 'CASH_ON_DELIVERY',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE order_items (
    order_item_id SERIAL PRIMARY KEY,
    order_id INTEGER NOT NULL,
    product_id INTEGER NOT NULL,
    quantity DECIMAL(10, 2) NOT NULL,
    price_per_unit DECIMAL(10, 2) NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(product_id)
);

-- 3. SEED INITIAL DML DATA

-- Seed Admin & Customer Users
-- Priya Sharma's birthday is set to today's month/day so the seeded Birthday Special Discount is demoable immediately.
INSERT INTO users (name, email, contact, birthday, pincode, user_type) VALUES
('ShopSphere Store Admin', 'admin@shopsphere.com', '9876543210', '1990-01-01', '600001', 'ADMIN'),
('Demo Customer User', 'customer@shopsphere.com', '9876543211', '1995-05-20', '600001', 'CUSTOMER'),
('Priya Sharma', 'priya@shopsphere.com', '9876543212', '1998-08-17', '600001', 'CUSTOMER');

-- Seed Auth Info with BCrypt Hashes
-- User 3 (priya@shopsphere.com)'s password is: Passw0rd!
INSERT INTO auth_info (user_id, password_hash) VALUES
(1, '$2a$10$xZ8DtREylSyYzJVpEx5rcO50HnAkOWch2SIlNpDERcntEB.CLNbuy'),
(2, '$2a$10$dn4ugoCQod5lH2E5M2W/DOUfRNeLJdmi5GT7Og/72uOQ0lxk1pmIi'),
(3, '$2a$10$xC42yPBDcyyNDP5gTZQ5UOkYFB3LwIPS9p2yUwuFg4yXlC.32S5ia');

-- Seed a delivery address for the demo customer -- pincode matches the seeded "Chennai Pincode Special" discount.
INSERT INTO addresses (user_id, house_no, street, landmark, city, state, pincode) VALUES
(3, '12', 'Anna Salai', 'Near Spencer Plaza', 'Chennai', 'Tamil Nadu', '600001');

-- Seed Categories
INSERT INTO categories (category_name, category_description, category_image_url) VALUES
('Fresh Produce', 'Farm-fresh fruits, vegetables, and greens', 'https://images.unsplash.com/photo-1610832958506-aa56368176cf?auto=format&fit=crop&w=600&q=80'),
('Organic & Bio', 'Certificated 100% organic farm items', 'https://images.unsplash.com/photo-1542838132-92c53300491e?auto=format&fit=crop&w=600&q=80'),
('Dairy & Bakery', 'Fresh milk, artisanal cheeses, farm eggs, and breads', 'https://images.unsplash.com/photo-1528751014936-863e6e7a319c?auto=format&fit=crop&w=600&q=80'),
('Beverages & Snacks', 'Refreshing juices, gourmet teas, coffees, and nuts', 'https://images.unsplash.com/photo-1534353473418-4cfa6c56fd38?auto=format&fit=crop&w=600&q=80'),
('Meat & Seafood', 'Fresh, responsibly-sourced meat, poultry, and seafood', 'https://images.unsplash.com/photo-1682991136736-a2b44623eeba?auto=format&fit=crop&w=600&q=80'),
('Household Essentials', 'Cleaning supplies and everyday home essentials', 'https://images.unsplash.com/photo-1576503276236-71dd8ad5b0b9?auto=format&fit=crop&w=600&q=80'),
('Personal Care', 'Shampoos, soaps, and everyday personal care items', 'https://images.unsplash.com/photo-1748543668676-ea8241cb3886?auto=format&fit=crop&w=600&q=80');

-- Seed Products with Unit Values
INSERT INTO products (name, description, price, unit_value, image_url) VALUES
('Organic Vine Tomatoes', 'Juicy, vine-ripened organic tomatoes packed with rich flavor.', 60.00, '500 g', 'https://images.unsplash.com/photo-1592924357228-91a4daadcfea?auto=format&fit=crop&w=600&q=80'),
('Crisp Hydroponic Lettuce', 'Freshly harvested crisp lettuce heads, rich in nutrients.', 45.00, '1 head', 'https://images.unsplash.com/photo-1622206151226-18ca2c9ab4a1?auto=format&fit=crop&w=600&q=80'),
('Sweet Alphonso Mangoes', 'Handpicked premium Alphonso mangoes with rich aroma.', 250.00, '1 kg', 'https://images.unsplash.com/photo-1553279768-865429fa0078?auto=format&fit=crop&w=600&q=80'),
('Organic Hass Avocados', 'Creamy, rich Hass avocados perfect for salads & guacamole.', 120.00, '2 pcs', 'https://images.unsplash.com/photo-1523049673857-eb18f1d7b578?auto=format&fit=crop&w=600&q=80'),
('Farm Fresh Whole Milk 1L', 'Pasteurized, pure whole cow milk sourced directly from farms.', 65.00, '1 L', 'https://images.unsplash.com/photo-1550583724-b2692b85b150?auto=format&fit=crop&w=600&q=80'),
('Artisanal Sourdough Bread', 'Traditional slow-fermented sourdough bread with crispy crust.', 90.00, '400 g', 'https://images.unsplash.com/photo-1585478259715-876a6a81fc08?auto=format&fit=crop&w=600&q=80'),
('Aged Cheddar Cheese 200g', 'Rich, sharp aged cheddar cheese blocks crafted naturally.', 210.00, '200 g', 'https://images.unsplash.com/photo-1618160702438-9b02ab6515c9?auto=format&fit=crop&w=600&q=80'),
('Cold Pressed Orange Juice 500ml', '100% natural, freshly squeezed orange juice with no added sugar.', 110.00, '500 ml', 'https://images.unsplash.com/photo-1613478223719-2ab802602423?auto=format&fit=crop&w=600&q=80'),
('Organic Green Tea 100g', 'Antioxidant-rich whole leaf green tea from Darjeeling estates.', 180.00, '100 g', 'https://images.unsplash.com/photo-1576092768241-dec231879fc3?auto=format&fit=crop&w=600&q=80'),
('Roasted Salted Almonds 250g', 'Crunchy, jumbo Californian almonds slow-roasted with sea salt.', 320.00, '250 g', 'https://images.unsplash.com/photo-1508061252966-177bf0e41306?auto=format&fit=crop&w=600&q=80'),
('Fresh Chicken Breast (Boneless)', 'Tender, skinless boneless chicken breast, trimmed and ready to cook.', 240.00, '500 g', 'https://images.unsplash.com/photo-1682991136736-a2b44623eeba?auto=format&fit=crop&w=600&q=80'),
('Farm Raised Prawns', 'Deveined, farm-raised prawns, cleaned and ready for cooking.', 380.00, '500 g', 'https://images.unsplash.com/photo-1566575167524-1b8b9c66c30d?auto=format&fit=crop&w=600&q=80'),
('Multi-Surface Cleaner Spray', 'All-purpose cleaning spray, safe for kitchen and bathroom surfaces.', 150.00, '500 ml', 'https://images.unsplash.com/photo-1576503276236-71dd8ad5b0b9?auto=format&fit=crop&w=600&q=80'),
('Soft Recycled Paper Towels', 'Absorbent, tree-free paper towels made from 100% recycled fiber.', 95.00, 'Pack of 2', 'https://images.unsplash.com/photo-1625480860583-b007013905b9?auto=format&fit=crop&w=600&q=80'),
('Heavy Duty Trash Bags', 'Leak-proof, tear-resistant trash bags for everyday household use.', 180.00, '30 count', 'https://images.unsplash.com/photo-1701992678972-d5a053ad0fb0?auto=format&fit=crop&w=600&q=80'),
('Herbal Nourishing Shampoo', 'Sulfate-free shampoo with natural herbal extracts for daily use.', 220.00, '200 ml', 'https://images.unsplash.com/photo-1686831889330-b059693080dd?auto=format&fit=crop&w=600&q=80'),
('Natural Glycerin Bar Soap', 'Gentle, moisturizing glycerin soap bars for everyday skincare.', 60.00, 'Pack of 3', 'https://images.unsplash.com/photo-1726235812628-23558521686f?auto=format&fit=crop&w=600&q=80'),
('Classic Salted Potato Chips', 'Crispy, kettle-cooked potato chips with a light sea salt seasoning.', 50.00, '150 g', 'https://images.unsplash.com/photo-1741520149938-4f08654780ef?auto=format&fit=crop&w=600&q=80');

-- Seed Product Categories
INSERT INTO product_categories (product_id, category_id) VALUES
(1, 1), (1, 2),
(2, 1), (2, 2),
(3, 1),
(4, 1), (4, 2),
(5, 3),
(6, 3),
(7, 3),
(8, 4),
(9, 2), (9, 4),
(10, 4),
(11, 5),
(12, 5),
(13, 6),
(14, 6),
(15, 6),
(16, 7),
(17, 7),
(18, 4);

-- Seed Batches
INSERT INTO product_batches (product_id, source, batch_date, expiry_date, available_quantity) VALUES
(1, 'Local Organic Farm - Coonoor', CURRENT_DATE - INTERVAL '1 DAY', CURRENT_DATE + INTERVAL '5 DAYS', 30),
(1, 'Greenhouse Hydroponics - Hosur', CURRENT_DATE, CURRENT_DATE + INTERVAL '7 DAYS', 25),
(2, 'Ooty Valley Produce', CURRENT_DATE - INTERVAL '2 DAYS', CURRENT_DATE + INTERVAL '4 DAYS', 20),
(3, 'Ratnagiri Farms', CURRENT_DATE - INTERVAL '1 DAY', CURRENT_DATE + INTERVAL '8 DAYS', 50),
(4, 'Kodaikanal Estates', CURRENT_DATE, CURRENT_DATE + INTERVAL '6 DAYS', 15),
(5, 'Aavin Dairy Farm', CURRENT_DATE, CURRENT_DATE + INTERVAL '3 DAYS', 40),
(6, 'Artisan Bakers Chennai', CURRENT_DATE, CURRENT_DATE + INTERVAL '2 DAYS', 12),
(7, 'Nilgiris Dairy Co.', CURRENT_DATE - INTERVAL '5 DAYS', CURRENT_DATE + INTERVAL '25 DAYS', 18),
(8, 'SunPure Juices', CURRENT_DATE, CURRENT_DATE + INTERVAL '4 DAYS', 22),
(9, 'Darjeeling Tea Estate', CURRENT_DATE - INTERVAL '10 DAYS', CURRENT_DATE + INTERVAL '180 DAYS', 50),
(10, 'California Almond Import', CURRENT_DATE - INTERVAL '15 DAYS', CURRENT_DATE + INTERVAL '90 DAYS', 35),
(11, 'Coimbatore Poultry Farm', CURRENT_DATE, CURRENT_DATE + INTERVAL '2 DAYS', 20),
(12, 'Chennai Coastal Seafood', CURRENT_DATE, CURRENT_DATE + INTERVAL '2 DAYS', 15),
(13, 'CleanHome Industries', CURRENT_DATE - INTERVAL '10 DAYS', CURRENT_DATE + INTERVAL '365 DAYS', 40),
(14, 'EcoPaper Co.', CURRENT_DATE - INTERVAL '10 DAYS', NULL, 60),
(15, 'EcoPaper Co.', CURRENT_DATE - INTERVAL '10 DAYS', NULL, 50),
(16, 'Herbal Essentials Co.', CURRENT_DATE - INTERVAL '20 DAYS', CURRENT_DATE + INTERVAL '540 DAYS', 35),
(17, 'Herbal Essentials Co.', CURRENT_DATE - INTERVAL '20 DAYS', CURRENT_DATE + INTERVAL '540 DAYS', 45),
(18, 'SnackTime Foods', CURRENT_DATE - INTERVAL '5 DAYS', CURRENT_DATE + INTERVAL '120 DAYS', 55);

-- Seed Discounts
INSERT INTO discounts (name, discount_type, discount_value, target_scope, min_order_amount, target_pincode, target_user_id, is_birthday_only, valid_from, valid_until) VALUES
('Flash Produce Sale', 'PERCENTAGE', 10.00, 'PRODUCT', NULL, NULL, NULL, FALSE, CURRENT_TIMESTAMP - INTERVAL '1 DAY', CURRENT_TIMESTAMP + INTERVAL '30 DAYS'),
('Chennai Pincode Special', 'PERCENTAGE', 5.00, 'ORDER', 300.00, '600001', NULL, FALSE, CURRENT_TIMESTAMP - INTERVAL '1 DAY', CURRENT_TIMESTAMP + INTERVAL '30 DAYS'),
('Birthday Special Discount', 'PERCENTAGE', 20.00, 'ORDER', NULL, NULL, NULL, TRUE, CURRENT_TIMESTAMP - INTERVAL '1 DAY', CURRENT_TIMESTAMP + INTERVAL '30 DAYS');

-- Map Discounts
INSERT INTO product_discounts (product_id, discount_id) VALUES
(1, 1),
(3, 1);
