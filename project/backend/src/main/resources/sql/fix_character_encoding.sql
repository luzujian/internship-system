-- Fix character encoding issues in the database
-- Database: internship
-- Purpose: Convert all tables and columns to use utf8mb4 character set

-- ========================================
-- Part 1: Show current character sets
-- ========================================

SELECT '========== Part 1: Current character sets ==========' as status;

SELECT 
    TABLE_SCHEMA,
    TABLE_NAME,
    TABLE_COLLATION,
    CCSA.CHARACTER_SET_NAME
FROM information_schema.TABLES T
JOIN information_schema.COLLATION_CHARACTER_SET_APPLICABILITY CCSA 
    ON T.TABLE_COLLATION = CCSA.COLLATION_NAME
WHERE TABLE_SCHEMA = 'internship'
ORDER BY TABLE_NAME;

-- ========================================
-- Part 2: Convert database to utf8mb4
-- ========================================

SELECT '========== Part 2: Converting database ==========' as status;

ALTER DATABASE internship CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ========================================
-- Part 3: Convert all tables to utf8mb4
-- ========================================

SELECT '========== Part 3: Converting tables ==========' as status;

-- Convert admin_users table
ALTER TABLE admin_users CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert ai_audit_records table
ALTER TABLE ai_audit_records CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert ai_model table
ALTER TABLE ai_model CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert announcement_read_records table
ALTER TABLE announcement_read_records CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert announcements table
ALTER TABLE announcements CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert assignment table
ALTER TABLE assignment CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert backup_audit_log table
ALTER TABLE backup_audit_log CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert backup_record table
ALTER TABLE backup_record CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert backup_schedule table
ALTER TABLE backup_schedule CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert category_weight table
ALTER TABLE category_weight CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert chat table
ALTER TABLE chat CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert chat_member table
ALTER TABLE chat_member CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert chat_message table
ALTER TABLE chat_message CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert class table
ALTER TABLE class CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert company_notification table
ALTER TABLE company_notification CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert company_users table
ALTER TABLE company_users CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert contribution table
ALTER TABLE contribution CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert contribution_data table
ALTER TABLE contribution_data CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert course table
ALTER TABLE course CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert course_major table
ALTER TABLE course_major CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert course_student table
ALTER TABLE course_student CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert department table
ALTER TABLE department CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert file_upload table
ALTER TABLE file_upload CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert final_score table
ALTER TABLE final_score CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert group_table table
ALTER TABLE group_table CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert group_member table
ALTER TABLE group_member CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert individual_score table
ALTER TABLE individual_score CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert internship_application table
ALTER TABLE internship_application CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert internship_reflection table
ALTER TABLE internship_reflection CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert keyword_library table
ALTER TABLE keyword_library CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert learning_resource table
ALTER TABLE learning_resource CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert login_log table
ALTER TABLE login_log CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert major table
ALTER TABLE major CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert operate_log table
ALTER TABLE operate_log CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert permission table
ALTER TABLE permission CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert position table
ALTER TABLE position CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert position_category table
ALTER TABLE position_category CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert problem_feedback table
ALTER TABLE problem_feedback CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert resource_category table
ALTER TABLE resource_category CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert resource_document table
ALTER TABLE resource_document CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert role table
ALTER TABLE role CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert role_permission table
ALTER TABLE role_permission CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert score_adjustment table
ALTER TABLE score_adjustment CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert scoring_rule table
ALTER TABLE scoring_rule CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert student_archive table
ALTER TABLE student_archive CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert student_internship_status table
ALTER TABLE student_internship_status CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert student_users table (this is the main table with name column)
ALTER TABLE student_users CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert submission table
ALTER TABLE submission CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert system_config table
ALTER TABLE system_config CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert system_settings table
ALTER TABLE system_settings CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert teacher_users table
ALTER TABLE teacher_users CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert team_relation table
ALTER TABLE team_relation CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Convert template_file table
ALTER TABLE template_file CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- ========================================
-- Part 4: Verify the changes
-- ========================================

SELECT '========== Part 4: Verifying changes ==========' as status;

SELECT 
    TABLE_SCHEMA,
    TABLE_NAME,
    TABLE_COLLATION,
    CCSA.CHARACTER_SET_NAME
FROM information_schema.TABLES T
JOIN information_schema.COLLATION_CHARACTER_SET_APPLICABILITY CCSA 
    ON T.TABLE_COLLATION = CCSA.COLLATION_NAME
WHERE TABLE_SCHEMA = 'internship'
ORDER BY TABLE_NAME;

-- ========================================
-- Part 5: Summary
-- ========================================

SELECT '========== Summary ==========' as status;
SELECT 
    'All tables have been converted to utf8mb4 character set' as message,
    'Please restart the backend application for changes to take effect' as next_step;
