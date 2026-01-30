CREATE TABLE IF NOT EXISTS assignment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    status VARCHAR(255),
    request_id INT,
    room_id INT,
    position INT
);