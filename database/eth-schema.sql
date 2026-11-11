-- MySQL 8.4: select the eth20 database before running this script.

CREATE TABLE eth_admin (
    id INTEGER AUTO_INCREMENT PRIMARY KEY,
    account VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    level INTEGER,
    create_date DATE,
    create_account VARCHAR(255),
    mail VARCHAR(255),
    phone VARCHAR(255),
    address VARCHAR(255),
    hash_key VARCHAR(255)
);

CREATE TABLE eth_client (
    id INTEGER AUTO_INCREMENT PRIMARY KEY,
    account VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    level INTEGER,
    create_date DATE,
    create_account VARCHAR(255),
    mail VARCHAR(255),
    phone VARCHAR(255),
    address VARCHAR(255),
    hash_key VARCHAR(255),
    main_company VARCHAR(255),
    main_account VARCHAR(255)
);

CREATE TABLE eth_log (
    id INTEGER AUTO_INCREMENT PRIMARY KEY,
    register_log TEXT,
    transfer_log TEXT,
    approve_log TEXT,
    lock_log TEXT,
    unlock_log TEXT,
    modify_log TEXT
);

CREATE TABLE eth_client_detail (
    hash_key VARCHAR(255) PRIMARY KEY,
    cash DOUBLE,
    create_date DATE,
    modify_date DATE,
    agreed_account VARCHAR(255)
);
