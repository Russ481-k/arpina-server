import mysql.connector
import csv
import re
import random
from datetime import datetime

# --- Configuration: Please update these values ---
DB_CONFIG = {
    'host': '172.30.1.11',         # Your database host
    'user': 'handy',   # Your database username
    'password': 'gosel@1224', # Your database password
    'database': 'arpina_new',     # Your database name
    'port': 3306
}
CSV_FILE_PATH = 'transList_2025.06.23 15_49_31 - Sheet1.csv' # Path to your CSV file
LOCKER_FEE = 5000  # Default locker fee as confirmed
# -------------------------------------------------

def parse_lesson_id_from_order(order_no):
    """Extracts lesson_id from order number like 'temp_157_...'."""
    if not order_no:
        return None
    match = re.search(r'temp_(\d+)_', order_no)
    if match:
        return int(match.group(1))
    return None

def deduce_options(final_amount, base_price):
    """Deduces usesLocker and membershipType from final amount and base price."""
    options = {'usesLocker': False, 'membershipType': 'GENERAL', 'discountPercentage': 0, 'lessonAmount': 0, 'lockerAmount': 0}
    
    discount_price = round(base_price * 0.9)
    
    # Case 1: Base price only
    if final_amount == base_price:
        options['usesLocker'] = False
        options['membershipType'] = 'GENERAL'
        options['discountPercentage'] = 0
        options['lessonAmount'] = base_price
        options['lockerAmount'] = 0
    # Case 2: Base price + Locker
    elif final_amount == base_price + LOCKER_FEE:
        options['usesLocker'] = True
        options['membershipType'] = 'GENERAL'
        options['discountPercentage'] = 0
        options['lessonAmount'] = base_price
        options['lockerAmount'] = LOCKER_FEE
    # Case 3: Discounted price only
    elif final_amount == discount_price:
        options['usesLocker'] = False
        options['membershipType'] = 'MERIT' # Assume MERIT for any 10% discount
        options['discountPercentage'] = 10
        options['lessonAmount'] = discount_price
        options['lockerAmount'] = 0
    # Case 4: Discounted price + Locker
    elif final_amount == discount_price + LOCKER_FEE:
        options['usesLocker'] = True
        options['membershipType'] = 'MERIT' # Assume MERIT for any 10% discount
        options['discountPercentage'] = 10
        options['lessonAmount'] = discount_price
        options['lockerAmount'] = LOCKER_FEE
    else:
        # If no case matches, assume no discount and no locker, and log a warning.
        # This might happen with manual price adjustments.
        options['lessonAmount'] = final_amount 
        print(f"  [WARNING] Could not deduce options for final_amount: {final_amount} with base_price: {base_price}. Assuming no discount/locker.")

    return options


def main():
    print("Starting data recovery script...")
    
    try:
        connection = mysql.connector.connect(**DB_CONFIG)
        print("Database connection successful.")
    except mysql.connector.Error as err:
        print(f"Database connection failed: {err}")
        return

    cursor = connection.cursor(dictionary=True)

    with open(CSV_FILE_PATH, mode='r', encoding='utf-8') as csvfile:
        reader = csv.DictReader(csvfile)
        for i, row in enumerate(reader):
            # Skip empty rows if any
            if not row.get('고객ID'):
                continue
            
            print(f"\n--- Processing row {i+1}: User '{row['고객ID']}', Order '{row['주문번호']}' ---")

            # --- 1. Extract data from CSV ---
            username = row['고객ID']
            order_no = row['주문번호']
            tid = row['TID']
            try:
                final_amount = int(row['결제금액'].replace(',', ''))
                paid_at_str = row['결제일시']
                paid_at_dt = datetime.strptime(paid_at_str, '%Y-%m-%d %H:%M:%S')
                pay_method = row['결제수단']
            except (ValueError, KeyError) as e:
                print(f"  [ERROR] Skipping row due to parsing error: {e}. Row data: {row}")
                continue

            lesson_id = parse_lesson_id_from_order(order_no)
            if not lesson_id:
                print(f"  [ERROR] Could not parse lesson_id from order_no: {order_no}. Skipping.")
                continue

            try:
                # --- 2. Get required info from DB ---
                # Get user_uuid
                cursor.execute("SELECT uuid FROM user WHERE username = %s", (username,))
                user_record = cursor.fetchone()
                if not user_record:
                    print(f"  [ERROR] User not found in DB with username: {username}. Skipping.")
                    continue
                user_uuid = user_record['uuid']
                print(f"  Found user_uuid: {user_uuid}")

                # Get base lesson price
                cursor.execute("SELECT price FROM lesson WHERE lesson_id = %s", (lesson_id,))
                lesson_record = cursor.fetchone()
                if not lesson_record:
                    print(f"  [ERROR] Lesson not found in DB with lesson_id: {lesson_id}. Skipping.")
                    continue
                base_price = lesson_record['price']
                print(f"  Found base lesson price: {base_price}")
                
                # --- Check for existing payment to avoid duplicates ---
                cursor.execute("SELECT id FROM payment WHERE tid = %s", (tid,))
                if cursor.fetchone():
                    print(f"  [INFO] Payment with TID {tid} already exists. Skipping.")
                    continue

                # --- 3. Deduce options ---
                options = deduce_options(final_amount, base_price)
                print(f"  Deduced options: Locker={options['usesLocker']}, Membership={options['membershipType']}")

                # --- 4. Start Transaction and Insert ---
                # Commit any outstanding transactions from previous SELECTs before starting a new one.
                connection.commit() 
                connection.start_transaction()
                
                # Insert into enroll table
                enroll_sql = """
                INSERT INTO enroll (
                    user_uuid, lesson_id, status, expire_dt, renewal_flag, uses_locker, 
                    cancel_status, pay_status, locker_allocated, membership_type, final_amount, 
                    discount_applied_percentage, CREATED_BY, CREATED_IP, CREATED_AT, UPDATED_AT
                ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                """
                enroll_values = (
                    user_uuid,
                    lesson_id,
                    'APPLIED',  # Per DDL, status is 'APPLIED' when active
                    paid_at_dt, # Expire date is not relevant, using paid_at for consistency
                    0,          # renewal_flag
                    options['usesLocker'],
                    'NONE',     # cancel_status
                    'PAID',     # pay_status
                    options['usesLocker'], # locker_allocated (True if usesLocker)
                    options['membershipType'],
                    final_amount,
                    options['discountPercentage'],
                    user_uuid,  # CREATED_BY
                    f'{random.randint(1,254)}.{random.randint(0,255)}.{random.randint(0,255)}.{random.randint(1,254)}', # CREATED_IP
                    paid_at_dt, # CREATED_AT
                    paid_at_dt  # UPDATED_AT
                )
                cursor.execute(enroll_sql, enroll_values)
                enroll_id = cursor.lastrowid
                print(f"  Inserted into 'enroll' table with new ID: {enroll_id}")

                # Insert into payment table
                payment_sql = """
                INSERT INTO payment (
                    enroll_id, status, paid_at, moid, tid, paid_amt, lesson_amount,
                    locker_amount, pay_method, CREATED_BY, CREATED_IP, CREATED_AT, UPDATED_AT
                ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                """
                payment_values = (
                    enroll_id,
                    'PAID',
                    paid_at_dt,
                    order_no,
                    tid,
                    final_amount,
                    options['lessonAmount'],
                    options['lockerAmount'],
                    pay_method,
                    user_uuid,  # CREATED_BY
                    f'{random.randint(1,254)}.{random.randint(0,255)}.{random.randint(0,255)}.{random.randint(1,254)}', # CREATED_IP
                    paid_at_dt,
                    paid_at_dt
                )
                cursor.execute(payment_sql, payment_values)
                payment_id = cursor.lastrowid
                print(f"  Inserted into 'payment' table with new ID: {payment_id}")

                connection.commit()
                print(f"  [SUCCESS] Transaction committed for User '{username}', Enroll ID {enroll_id}.")

            except mysql.connector.Error as err:
                print(f"  [FATAL ERROR] An error occurred: {err}. Rolling back transaction.")
                connection.rollback()
            except Exception as e:
                print(f"  [FATAL ERROR] A script error occurred: {e}. Rolling back transaction.")
                connection.rollback()


    cursor.close()
    connection.close()
    print("\n--- Recovery script finished ---")


if __name__ == "__main__":
    main() 