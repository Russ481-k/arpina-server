-- 사용자 활동 로그 테이블 생성
CREATE TABLE IF NOT EXISTS user_activity_log (
    uuid VARCHAR(36) NOT NULL PRIMARY KEY,
    user_uuid VARCHAR(36) NOT NULL,
    group_id VARCHAR(36) NOT NULL,
    organization_id VARCHAR(36) NOT NULL,
    activity_type VARCHAR(50) NOT NULL,
    description VARCHAR(255) NOT NULL,
    user_agent VARCHAR(255),
    created_by VARCHAR(36) DEFAULT NULL,
    created_ip VARCHAR(45) DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(36) DEFAULT NULL,
    updated_ip VARCHAR(45) DEFAULT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_activity_log_user FOREIGN KEY (user_uuid) REFERENCES user(uuid) ON DELETE CASCADE,
    CONSTRAINT fk_user_activity_log_group FOREIGN KEY (group_id) REFERENCES groups(uuid) ON DELETE CASCADE,
    CONSTRAINT fk_user_activity_log_organization FOREIGN KEY (organization_id) REFERENCES organizations(uuid) ON DELETE CASCADE,
    CONSTRAINT fk_user_activity_log_created_by FOREIGN KEY (created_by) REFERENCES user(uuid) ON DELETE SET NULL,
    CONSTRAINT fk_user_activity_log_updated_by FOREIGN KEY (updated_by) REFERENCES user(uuid) ON DELETE SET NULL
);

-- 인덱스 생성
CREATE INDEX idx_user_activity_log_uuid ON user_activity_log(uuid);
CREATE INDEX idx_user_activity_log_user_uuid ON user_activity_log(user_uuid);
CREATE INDEX idx_user_activity_log_group_id ON user_activity_log(group_id);
CREATE INDEX idx_user_activity_log_organization_id ON user_activity_log(organization_id);
CREATE INDEX idx_user_activity_log_created_at ON user_activity_log(created_at);
CREATE INDEX idx_user_activity_log_created_by ON user_activity_log(created_by);
CREATE INDEX idx_user_activity_log_updated_by ON user_activity_log(updated_by); 