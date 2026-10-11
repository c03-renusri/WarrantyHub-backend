CREATE TABLE accounts (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 name VARCHAR(100) NOT NULL,
 email VARCHAR(254) NOT NULL UNIQUE,
 password_hash VARCHAR(255) NOT NULL
);
CREATE TABLE products (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 owner_id BIGINT NOT NULL,
 product_name VARCHAR(120) NOT NULL,
 brand VARCHAR(100) NOT NULL,
 category VARCHAR(80) NOT NULL,
 store VARCHAR(120) NOT NULL,
 price DECIMAL(14,2) NOT NULL,
 purchase_date DATE NOT NULL,
 warranty_months INT NOT NULL,
 CONSTRAINT fk_product_owner FOREIGN KEY(owner_id) REFERENCES accounts(id),
 CONSTRAINT chk_price CHECK(price >= 0),
 CONSTRAINT chk_months CHECK(warranty_months BETWEEN 0 AND 600)
);
CREATE INDEX idx_product_owner ON products(owner_id);
CREATE TABLE receipts (
 product_id BIGINT PRIMARY KEY,
 filename VARCHAR(255) NOT NULL,
 content_type VARCHAR(40) NOT NULL,
 content LONGBLOB NOT NULL,
 CONSTRAINT fk_receipt_product FOREIGN KEY(product_id) REFERENCES products(id) ON DELETE CASCADE
);
CREATE TABLE service_requests (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 product_id BIGINT NOT NULL,
 issue_description VARCHAR(2000) NOT NULL,
 status VARCHAR(20) NOT NULL,
 request_date DATE NOT NULL,
 CONSTRAINT fk_request_product FOREIGN KEY(product_id) REFERENCES products(id) ON DELETE CASCADE
);
CREATE INDEX idx_request_product ON service_requests(product_id);
