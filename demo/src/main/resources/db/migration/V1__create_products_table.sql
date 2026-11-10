CREATE TABLE products (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    price NUMERIC(19,2) NOT NULL,
    stock_quantity INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
