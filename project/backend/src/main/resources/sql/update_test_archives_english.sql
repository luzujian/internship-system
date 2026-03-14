-- Clear test data
DELETE FROM student_archives WHERE student_id IN (7, 8);

-- Insert test data with English filenames
-- Zhao Qiang (student_id = 7)
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, remark, status) VALUES
(7, 'Zhao_Qiang_Resume.pdf', 'Resume', 'https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf', NOW(), 'Personal Resume', 1),
(7, 'Zhao_Qiang_CET6_Certificate.jpg', 'Certificate', 'https://via.placeholder.com/800x600/4CAF50/FFFFFF?text=CET6+Certificate', NOW(), 'CET6 Certificate', 1),
(7, 'Zhao_Qiang_NCRE2_Certificate.jpg', 'Certificate', 'https://via.placeholder.com/800x600/2196F3/FFFFFF?text=NCRE2+Certificate', NOW(), 'NCRE2 Certificate', 1);

-- Student (student_id = 8)
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, remark, status) VALUES
(8, 'Student_Resume.pdf', 'Resume', 'https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf', NOW(), 'Personal Resume', 1),
(8, 'Student_CET6_Certificate.jpg', 'Certificate', 'https://via.placeholder.com/800x600/FF9800/FFFFFF?text=CET6+Certificate', NOW(), 'CET6 Certificate', 1),
(8, 'Student_NCRE2_Certificate.jpg', 'Certificate', 'https://via.placeholder.com/800x600/9C27B0/FFFFFF?text=NCRE2+Certificate', NOW(), 'NCRE2 Certificate', 1);

-- View inserted data
SELECT id, student_id, file_name, file_type, file_url FROM student_archives WHERE student_id IN (7, 8) ORDER BY id;
