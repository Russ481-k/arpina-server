import uuid
import random
import string
import datetime
import base64
import os
import bcrypt

NUM_USERS = 1000
PLAIN_TEXT_PASSWORD = "testpassword"

SURNAMES_KR = ["김", "이", "박", "최", "정", "강", "조", "윤", "장", "임", "한", "오", "서", "신", "권", "황", "안", "송", "류", "전", "홍", "고", "문", "양", "손", "배", "백", "허", "유", "남", "심", "노", "하", "곽", "성", "차", "주", "우", "구", "나", "민", "채", "공", "천", "방", "변", "지"]

def get_random_hangul_syllable():
    return chr(random.randint(0xAC00, 0xD7A3))

def generate_korean_name():
    surname = random.choice(SURNAMES_KR)
    given_name = get_random_hangul_syllable() + get_random_hangul_syllable()
    return surname + given_name

def generate_random_ip():
    return f"{random.randint(1,254)}.{random.randint(0,255)}.{random.randint(0,255)}.{random.randint(1,254)}"

def sql_escape(value):
    if value is None:
        return "NULL"
    if isinstance(value, (int, float)):
        return str(value)
    # Basic escaping for strings, replace single quote with two single quotes
    return "'" + str(value).replace("'", "''") + "'"

def generate_users():
    users_sql = []
    generated_usernames = set()
    generated_emails = set()
    generated_dis = set()

    # 평문 비밀번호를 bcrypt로 해시
    hashed_password_bytes = bcrypt.hashpw(PLAIN_TEXT_PASSWORD.encode('utf-8'), bcrypt.gensalt())
    hashed_password_str = sql_escape(hashed_password_bytes.decode('utf-8')) # SQL에 넣을 형태로 준비

    for i in range(NUM_USERS):
        user_uuid = str(uuid.uuid4())
        
        while True:
            username_suffix = ''.join(random.choices(string.ascii_lowercase + string.digits, k=6))
            username = f"testuser_{username_suffix}"
            if username not in generated_usernames:
                generated_usernames.add(username)
                break
        
        name = generate_korean_name()
        
        while True:
            email = f"{username}@example.com"
            if email not in generated_emails:
                generated_emails.add(email)
                break

        password = hashed_password_str # BCrypt로 해시된 비밀번호 사용
        role = "'USER'"
        avatar_url = None 
        status = "'ACTIVE'"
        organization_id = None
        group_id = None
        car_no = None
        temp_pw_flag = 0
        
        # Generate valid birth date (YYYYMMDD)
        birth_year = random.randint(1970, 2005)
        birth_month = random.randint(1, 12)
        # Ensure day is valid for the month
        if birth_month == 2:
            max_day = 28 # Simplification for February
        elif birth_month in [4, 6, 9, 11]:
            max_day = 30
        else:
            max_day = 31
        birth_day = random.randint(1, max_day)
        birth_date_obj = datetime.date(birth_year, birth_month, birth_day)
        birth_date_str = f"'{birth_date_obj.strftime('%Y%m%d')}'"

        while True:
            di = f"'{base64.b64encode(os.urandom(48)).decode('utf-8')}'" # 64 char base64 string
            if di not in generated_dis:
                generated_dis.add(di)
                break
        
        provider = "'LOCAL'"
        phone = f"'010-{random.randint(1000,9999)}-{random.randint(1000,9999)}'"
        address = None
        gender = random.choice(["'MALE'", "'FEMALE'", "NULL"])
        
        reset_token_expiry = None
        reset_token = None
        is_temporary = 0
        
        created_by = None # Assuming new users are not created by existing test users in this batch
        created_ip = f"'{generate_random_ip()}'"
        
        now = datetime.datetime.now()
        created_at = f"'{now.strftime('%Y-%m-%d %H:%M:%S')}'"
        
        updated_by = None
        # Ensure updated_at is same or slightly after created_at for new records
        updated_at = f"'{ (now + datetime.timedelta(seconds=random.randint(1,60))).strftime('%Y-%m-%d %H:%M:%S')}'"
        updated_ip = f"'{generate_random_ip()}'"
        
        memo = None
        memo_updated_at = None
        memo_updated_by = None

        user_data = [
            f"'{user_uuid}'", username, name, email, password, role, avatar_url, status,
            organization_id, group_id, car_no, temp_pw_flag, birth_date_str, di, provider,
            phone, address, gender, reset_token_expiry, reset_token, is_temporary,
            created_by, created_ip, created_at, updated_by, updated_at, updated_ip,
            memo, memo_updated_at, memo_updated_by
        ]
        
        # Convert python None to SQL NULL, and other types to string representations for SQL
        user_values_sql = [sql_escape(val) if not isinstance(val, str) and val is not None and val not in ["'MALE'", "'FEMALE'", "NULL", username, name, email] else # special cases already quoted or are numbers
                           (val if isinstance(val, str) and (val.startswith("'") or val == "NULL") else sql_escape(val)) # if already quoted string or NULL string
                           for val in user_data]


        # Correctly format username, name, email as they are generated without quotes initially by helper functions
        user_values_sql[1] = sql_escape(username.replace("'", "''")) # username from generated_usernames
        user_values_sql[2] = sql_escape(name.replace("'", "''"))     # name from generate_korean_name
        user_values_sql[3] = sql_escape(email.replace("'", "''")) # email derived from username
        

        fields = [
            "`uuid`", "`username`", "`name`", "`email`", "`password`", "`role`", 
            "`avatar_url`", "`status`", "`organization_id`", "`group_id`", "`car_no`", 
            "`temp_pw_flag`", "`birth_date`", "`di`", "`provider`", "`phone`", "`address`", 
            "`gender`", "`reset_token_expiry`", "`reset_token`", "`is_temporary`", 
            "`created_by`", "`created_ip`", "`created_at`", "`updated_by`", 
            "`updated_at`", "`updated_ip`", "`memo`", "`memo_updated_at`", "`memo_updated_by`"
        ]

        sql = f"INSERT INTO `user` ({', '.join(fields)}) VALUES ({', '.join(user_values_sql)});"
        users_sql.append(sql)
        
    return users_sql

if __name__ == "__main__":
    generated_sql_statements = generate_users()
    for statement in generated_sql_statements:
        print(statement)
    print(f"\n-- Generated {len(generated_sql_statements)} user INSERT statements.")
    print("-- Note: 'password' field uses a placeholder. These passwords need to be properly hashed if your system requires it.")
    print("-- Ensure 'created_by', 'updated_by', 'memo_updated_by' are handled if they need to reference existing users.") 