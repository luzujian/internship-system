-- Check file with ID 394
SELECT id, student_id, file_name, file_type, file_url FROM student_archives WHERE id = 394;

-- Check all files for student_id = 7
SELECT id, student_id, file_name, file_type, file_url FROM student_archives WHERE student_id = 7 ORDER BY id;
