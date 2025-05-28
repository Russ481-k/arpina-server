import urllib.request
import json
import random
import time
import re
import concurrent.futures

# --- Configuration: Please update these values --- 
API_BASE_URL = "http://172.30.1.21:8080/api/v1"  # Replace with your actual API base URL
USER_SQL_FILE_PATH = "/home/handy/arpina/server/user_inserts.sql"      # Path to your generated SQL file
MAX_WORKERS_FOR_ENROLLMENT = 10             # Number of parallel threads for enrollment

# Lesson IDs based on your provided list
lesson_ids = [12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24]

# Optional: Delay between starting batches of API calls (if needed, less critical with ThreadPoolExecutor)
DELAY_BETWEEN_CALLS = 0.05 # Small delay when submitting tasks
# -------------------------------------------------

ENROLL_ENDPOINT = f"{API_BASE_URL}/swimming/enroll"
LOGIN_ENDPOINT = f"{API_BASE_URL}/auth/login" # !!! ADJUST YOUR LOGIN ENDPOINT URL !!!

# --- Added for enroll_user_to_lesson payload ---
POSSIBLE_MEMBERSHIP_TYPES = ["general", "merit", "multi-child", "multicultural"]
# -------------------------------------------------

def parse_user_inserts_sql(file_path):
    """Parses the user_inserts.sql file to extract user_uuid, username, and password placeholder."""
    users = []
    # Regex to capture UUID, username, and password from the INSERT statement
    insert_pattern = re.compile(
        r"INSERT\s+INTO\s+`user`"  # "INSERT INTO `user`" 부분 (공백 유연하게)
        r".*?"                     # 컬럼명 리스트 부분 (최소 매칭)
        r"VALUES\s*\("             # "VALUES (" 부분 (공백 유연하게, ( 이스케이프)
        # 각 값들에 대한 매칭
        r"\s*'([^']*)',"           # Group 1: uuid (첫 번째 따옴표 값)
        r"\s*'([^']*)',"           # Group 2: username (두 번째 따옴표 값)
        r"\s*'[^']*',"             # name (세 번째 따옴표 값, 캡처 안 함)
        r"\s*'[^']*',"             # email (네 번째 따옴표 값, 캡처 안 함)
        r"\s*'([^']*)',"           # Group 3: password_placeholder (다섯 번째 따옴표 값)
        r".*?"                     # 나머지 값들 (최소 매칭)
        r"\);"                     # ");" 로 끝나는 부분 ( ) 이스케이프)
        , re.VERBOSE | re.IGNORECASE) # IGNORECASE 추가, VERBOSE 유지
    print(f"[DEBUG] Attempting to parse SQL file: {file_path}") # DEBUG PRINT
    lines_processed = 0 # DEBUG PRINT
    matches_found = 0 # DEBUG PRINT
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            for i, line in enumerate(f):
                lines_processed += 1 # DEBUG PRINT
                # print(f"[DEBUG] Line {i+1}: {line.strip()[:100]}...") # DEBUG PRINT (너무 길면 주석 처리)
                match = insert_pattern.search(line)
                if match:
                    matches_found += 1 # DEBUG PRINT
                    user_uuid = match.group(1)
                    username = match.group(2)
                    password_placeholder = match.group(3)
                    users.append({"uuid": user_uuid, "username": username, "password": password_placeholder})
                    # print(f"[DEBUG] Found user: {username}") # DEBUG PRINT
                # else: # DEBUG PRINT
                    # if "INSERT INTO `user`" in line: # DEBUG PRINT
                        # print(f"[DEBUG] Line {i+1} contains INSERT but did not match regex.") # DEBUG PRINT
    except FileNotFoundError:
        print(f"Error: SQL file not found at {file_path}")
    except Exception as e: # DEBUG PRINT
        print(f"[DEBUG] An error occurred during parsing: {e}") # DEBUG PRINT
    
    print(f"[DEBUG] Total lines processed: {lines_processed}") # DEBUG PRINT
    print(f"[DEBUG] Total matches found: {matches_found}") # DEBUG PRINT
    if not users: # DEBUG PRINT
        print("[DEBUG] No users were appended to the list.") # DEBUG PRINT
    return users

def get_auth_token(username, password_placeholder):
    """
    Authenticates the user with the backend and retrieves a JWT token.
    Uses the username from the SQL file and a fixed password ("testpassword").
    The password_placeholder from the SQL file is IGNORED.
    """
    
    # --- Use a fixed password for all test users ---
    FIXED_TEST_PASSWORD = "testpassword" 
    # ------------------------------------------------

    print(f"Attempting to get auth token for {username} using fixed password: {FIXED_TEST_PASSWORD[:5]}...") 

    login_payload = {"username": username, "password": FIXED_TEST_PASSWORD} # Use fixed password
    headers = {'Content-Type': 'application/json'}
    data = json.dumps(login_payload).encode('utf-8')
    
    req = urllib.request.Request(LOGIN_ENDPOINT, data=data, headers=headers, method='POST')
    
    try:
        with urllib.request.urlopen(req) as response:
            response_body = response.read().decode('utf-8')
            if response.status == 200:
                response_data = json.loads(response_body)
                if response_data.get("success") and response_data.get("data") and response_data["data"].get("accessToken"):
                    token = response_data["data"]["accessToken"]
                    print(f"  Successfully obtained token for {username}")
                    return token
                else:
                    print(f"  Login successful for {username} but token not found in expected place in response: {response_body[:200]}...")
                    return None
            else:
                print(f"  Login failed for {username}. Status: {response.status}, Body: {response_body[:200]}...")
                return None
    except urllib.error.HTTPError as e:
        error_body = "Could not read error body."
        try: error_body = e.read().decode('utf-8')
        except: pass 
        print(f"  Login HTTP Error for {username}: {e.code} {e.reason} - Body: {error_body[:200]}...")
        return None
    except json.JSONDecodeError as e:
        print(f"  Login JSON Decode Error for {username}: {e}. Response was not valid JSON. Body: {response_body[:200]}...")
        return None
    except Exception as e:
        print(f"  Login general error for {username}: {e}")
        return None

def enroll_user_to_lesson(user_uuid, auth_token, lesson_id, uses_locker, membership_type):
    """Attempts to enroll a user into a lesson."""
    payload = {
        "lessonId": lesson_id,
        "usesLocker": uses_locker,
        "membershipType": membership_type
    }
    print(f"  Enrolling User {user_uuid} | Lesson {lesson_id} | Uses Locker: {uses_locker} | Membership: {membership_type}") # DEBUG
    data = json.dumps(payload).encode('utf-8')
    headers = {
        'Content-Type': 'application/json',
        'Authorization': f'Bearer {auth_token}',
        'X-Forwarded-For': f'{random.randint(1,254)}.{random.randint(0,255)}.{random.randint(0,255)}.{random.randint(1,254)}'
    }
    req = urllib.request.Request(ENROLL_ENDPOINT, data=data, headers=headers, method='POST')

    try:
        with urllib.request.urlopen(req) as response:
            response_body = response.read().decode('utf-8')
            # Shorten response for printing
            print(f"  SUCCESS: User {user_uuid} | Lesson {lesson_id} | Status: {response.status} | Resp: {response_body[:100].strip()}...")
            return {"uuid": user_uuid, "lesson_id": lesson_id, "status": response.status, "success": True, "response": response_body}
    except urllib.error.HTTPError as e:
        error_body = "Could not read error body."
        try: error_body = e.read().decode('utf-8')
        except: pass
        print(f"  FAILURE: User {user_uuid} | Lesson {lesson_id} | HTTP Error: {e.code} {e.reason} | Resp: {error_body[:100].strip()}...")
        return {"uuid": user_uuid, "lesson_id": lesson_id, "status": e.code, "success": False, "response": error_body}
    except Exception as e:
        print(f"  FAILURE: User {user_uuid} | Lesson {lesson_id} | General Error: {e}")
        return {"uuid": user_uuid, "lesson_id": lesson_id, "status": None, "success": False, "response": str(e)}

def main():
    print(f"Using API Base URL: {API_BASE_URL}")
    print(f"Enrollment endpoint: {ENROLL_ENDPOINT}")
    print(f"Login endpoint (for token generation - needs implementation): {LOGIN_ENDPOINT}")
    print(f"Parsing users from: {USER_SQL_FILE_PATH}")
    
    users_from_sql = parse_user_inserts_sql(USER_SQL_FILE_PATH)

    if not users_from_sql:
        print("No user data parsed from SQL file. Exiting.")
        return
    if not lesson_ids:
        print("Error: `lesson_ids` list is empty. Please populate it.")
        return

    print(f"Found {len(users_from_sql)} users in SQL file. Attempting to get tokens and enroll...")
    print(f"Available lesson IDs: {lesson_ids}")

    successful_enrollments = 0
    failed_enrollments = 0
    users_processed_for_enrollment = 0

    with concurrent.futures.ThreadPoolExecutor(max_workers=MAX_WORKERS_FOR_ENROLLMENT) as executor:
        future_to_user = {}
        for user_info in users_from_sql:
            user_uuid = user_info["uuid"]
            username = user_info["username"]
            password_placeholder = user_info["password"]

            print(f"Processing user: {username} ({user_uuid})")
            auth_token = get_auth_token(username, password_placeholder) # You MUST implement this properly

            if auth_token and "placeholder_jwt_token" not in auth_token:
                selected_lesson_id = random.choice(lesson_ids)
                # --- Randomly select enrollment options ---
                selected_uses_locker = random.choice([True, False])
                selected_membership_type = random.choice(POSSIBLE_MEMBERSHIP_TYPES)
                # -----------------------------------------
                print(f"  User {username} obtained token. Submitting enrollment for lesson {selected_lesson_id}, locker: {selected_uses_locker}, membership: {selected_membership_type}.")
                future = executor.submit(enroll_user_to_lesson, user_uuid, auth_token, selected_lesson_id, selected_uses_locker, selected_membership_type)
                future_to_user[future] = user_uuid
                users_processed_for_enrollment += 1
                if DELAY_BETWEEN_CALLS > 0:
                    time.sleep(DELAY_BETWEEN_CALLS) # Small delay before submitting next task
            elif auth_token and "placeholder_jwt_token" in auth_token:
                print(f"  Skipping user {username} due to placeholder auth token. Implement get_auth_token().")
                failed_enrollments +=1 # Count as failed if we can't get a real token
            else:
                print(f"  Skipping user {username} as no auth token could be obtained.")
                failed_enrollments +=1 # Count as failed if no token

        for future in concurrent.futures.as_completed(future_to_user):
            user_uuid_done = future_to_user[future]
            try:
                result = future.result()
                if result["success"]:
                    successful_enrollments += 1
                # else: # Already counted if token was missing, or will be implicitly a failure if API call itself failed
                #    failed_enrollments +=1
            except Exception as exc:
                print(f"User {user_uuid_done} enrollment generated an exception: {exc}")
                # failed_enrollments += 1 # Already counted if token was missing
    
    # Recalculate failed enrollments based on users we actually attempted to enroll
    # if all users were skipped due to token issues, users_processed_for_enrollment would be 0.
    if users_processed_for_enrollment > 0 : 
        failed_enrollments = users_processed_for_enrollment - successful_enrollments
    else: # if no users were processed (all skipped due to token issues), failed_enrollments should reflect total users
        failed_enrollments = len(users_from_sql)

    print("\n--- Enrollment Summary ---")
    print(f"Total users from SQL: {len(users_from_sql)}")
    print(f"Users for whom token was attempted: {len(users_from_sql)}")
    print(f"Users for whom enrollment API call was made: {users_processed_for_enrollment}")
    print(f"Successful API enrollments: {successful_enrollments}")
    print(f"Failed/Skipped API enrollments (due to token or API error): {failed_enrollments}")

if __name__ == "__main__":
    main()
    print("\nScript finished.")
    print("IMPORTANT: You MUST implement the 'get_auth_token' function with your actual login API logic.")
    print(f"Ensure the API_BASE_URL ('{API_BASE_URL}') and LOGIN_ENDPOINT ('{LOGIN_ENDPOINT}') are correct.")
    print(f"Ensure '{USER_SQL_FILE_PATH}' exists and is readable.") 