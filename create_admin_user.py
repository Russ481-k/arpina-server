import bcrypt
import uuid

# 비밀번호 해시 함수
def hash_password(password: str) -> str:
    """주어진 평문 비밀번호를 bcrypt를 사용하여 해시합니다."""
    salt = bcrypt.gensalt(rounds=10) # TypeScript 예제와 동일하게 rounds=10 사용
    hashed_password_bytes = bcrypt.hashpw(password.encode('utf-8'), salt)
    return hashed_password_bytes.decode('utf-8')

# 초기 관리자 생성 SQL 생성 함수
def create_initial_admin_sql(plain_password: str = "arpina@1234") -> str:
    """초기 관리자 계정 생성을 위한 SQL INSERT 문을 생성합니다."""
    hashed_password = hash_password(plain_password)
    admin_uuid = str(uuid.uuid4())
    
    # SQL 문 작성 시, 값들은 SQL 인젝션 방지를 위해 적절히 이스케이프 처리되어야 합니다.
    # 여기서는 문자열 포맷팅을 사용하지만, 실제 DB에 직접 실행할 경우 PreparedStatement 사용을 권장합니다.
    # 테이블명과 컬럼명은 제공된 DDL을 참고하여 'user' 테이블 기준으로 작성합니다.
    # status 컬럼은 DDL에 따라 'ACTIVE'와 같은 문자열을 사용합니다.
    sql_statement = f"""
    INSERT INTO `user` (uuid, username, name, password, email, role, status) 
    VALUES ('{admin_uuid}', 'arpina', 'Administrator (Arpina)', '{hashed_password}', 'arpina@arpina.com', 'ADMIN', 'ACTIVE');
    """
    # MariaDB/MySQL의 경우 문자열 값에 작은따옴표를 사용합니다.
    # role과 status는 대문자로 통일 (예: 'ADMIN', 'ACTIVE') - 실제 시스템의 값에 맞춰주세요.
    # name 필드에 '(Arpina)'를 추가하여 생성된 계정 식별 용이하게 함 (선택 사항)
    return sql_statement

if __name__ == "__main__":
    admin_sql = create_initial_admin_sql()
    print("--- Generated SQL for Initial Admin User ---")
    print(admin_sql)

    # 다른 비밀번호로 테스트
    # custom_password_sql = create_initial_admin_sql(plain_password="mySecurePassword123")
    # print("\n--- SQL for Custom Password Admin User ---")
    # print(custom_password_sql) 