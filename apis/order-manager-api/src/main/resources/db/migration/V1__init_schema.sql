-- 1. Enums for data integrity
CREATE TYPE status_type AS ENUM ('PENDING', 'SHIPPED', 'DELIVERED', 'CANCELLED');

-- 2. User accounts
CREATE TABLE users (
                       id SERIAL PRIMARY KEY,
                       username VARCHAR(50) NOT NULL UNIQUE,
                       email VARCHAR(100) NOT NULL UNIQUE,
                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. Orders linked to users
CREATE TABLE orders (
                        id SERIAL PRIMARY KEY,
                        customer_id INT NOT NULL,
                        order_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        status status_type NOT NULL DEFAULT 'PENDING',
                        FOREIGN KEY (customer_id) REFERENCES users(id) ON DELETE RESTRICT
);

-- 4. Product catalog
CREATE TABLE items (
                       id SERIAL PRIMARY KEY,
                       product_name VARCHAR(100) NOT NULL,
                       price NUMERIC(10, 2) NOT NULL
);

-- 5. Join table for Order/Items (Many-to-Many)
CREATE TABLE order_items (
                             order_id INT NOT NULL,
                             item_id INT NOT NULL,
                             quantity INT NOT NULL DEFAULT 1,
                             PRIMARY KEY (order_id, item_id),
                             FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
                             FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE
);
