```
UPDATE enroll e
JOIN lesson l ON e.lesson_id = l.lesson_id
SET e.renewal_flag = 1
WHERE EXISTS (
    SELECT 1
    FROM (SELECT * FROM enroll) AS prev_e
    JOIN lesson prev_l ON prev_e.lesson_id = prev_l.lesson_id
    WHERE
        prev_e.user_uuid = e.user_uuid
        AND prev_e.pay_status = 'PAID'
        AND DATE_FORMAT(prev_l.start_date, '%Y-%m') = DATE_FORMAT(l.start_date - INTERVAL 1 MONTH, '%Y-%m')
        AND prev_l.title = l.title
        AND prev_l.instructor_name = l.instructor_name
        AND prev_l.lesson_time = l.lesson_time
        AND prev_l.location_name = l.location_name
);
```
