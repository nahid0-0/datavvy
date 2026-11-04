-- MySQL / TiDB schema. Timestamps are stored in UTC (hibernate.jdbc.time_zone=UTC).
CREATE TABLE vehicles
(
    id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    vehicle_number    VARCHAR(32)  NOT NULL,
    -- Binary collation: duplicate detection must be exact, not accent/case-folded by the DB.
    normalized_number VARCHAR(32)  CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    make              VARCHAR(50)  NOT NULL,
    model             VARCHAR(50)  NOT NULL,
    vehicle_type      VARCHAR(20)  NOT NULL,
    manufacture_year  INT          NOT NULL,
    color             VARCHAR(30),
    owner_name        VARCHAR(100),
    status            VARCHAR(20)  NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0,

    -- normalized_number strips spaces/hyphens and upper-cases, so "dha-1234" == "DHA 1234".
    CONSTRAINT uk_vehicles_normalized_number UNIQUE (normalized_number),
    CONSTRAINT ck_vehicles_type CHECK (vehicle_type IN ('CAR', 'MOTORCYCLE', 'BUS', 'TRUCK', 'VAN', 'PICKUP', 'SUV', 'OTHER')),
    CONSTRAINT ck_vehicles_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'MAINTENANCE')),
    CONSTRAINT ck_vehicles_manufacture_year CHECK (manufacture_year >= 1886)
) DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_vehicles_created_at ON vehicles (created_at);
CREATE INDEX idx_vehicles_status ON vehicles (status);
CREATE INDEX idx_vehicles_type ON vehicles (vehicle_type);
