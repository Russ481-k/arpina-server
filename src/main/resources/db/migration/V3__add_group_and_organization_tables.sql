-- 그룹 테이블 생성
CREATE TABLE IF NOT EXISTS groups (
    uuid VARCHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    created_by VARCHAR(36) DEFAULT NULL,
    created_ip VARCHAR(45) DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(36) DEFAULT NULL,
    updated_ip VARCHAR(45) DEFAULT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(uuid),
    FOREIGN KEY (updated_by) REFERENCES users(uuid)
);

-- 조직 테이블 생성
CREATE TABLE IF NOT EXISTS organizations (
    uuid VARCHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    created_by VARCHAR(36) DEFAULT NULL,
    created_ip VARCHAR(45) DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(36) DEFAULT NULL,
    updated_ip VARCHAR(45) DEFAULT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(uuid),
    FOREIGN KEY (updated_by) REFERENCES users(uuid)
);

-- 인덱스 생성
CREATE INDEX idx_groups_name ON groups(name);
CREATE INDEX idx_groups_created_at ON groups(created_at);
CREATE INDEX idx_groups_created_by ON groups(created_by);
CREATE INDEX idx_groups_updated_by ON groups(updated_by);

CREATE INDEX idx_organizations_name ON organizations(name);
CREATE INDEX idx_organizations_created_at ON organizations(created_at);
CREATE INDEX idx_organizations_created_by ON organizations(created_by);
CREATE INDEX idx_organizations_updated_by ON organizations(updated_by); 