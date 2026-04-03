-- Thêm cột role vào bảng users, mặc định là 'USER'
ALTER TABLE users
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';
