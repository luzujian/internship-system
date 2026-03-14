-- 检查student_archives表中的数据
SELECT * FROM student_archives WHERE student_id IN (7, 8);

-- 如果没有数据，重新插入测试数据
-- 清除测试数据
DELETE FROM student_archives WHERE student_id IN (7, 8);

-- 插入测试数据 - 使用简化的base64 data URL
-- 对于student_id = 7 (赵强)
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, remark, status) VALUES
(7, 'ZhaoQiang_Resume.pdf', 'Resume', 'data:application/pdf;base64,JVBERi0xLjQKJeLjz9MKMSAwIG9iago8PC9UeXBlL0NhdGFsb2cvUGFnZXMgMiAwIFI+ZW5kb2JqCjIgMCBvYmoKPDwvVHlwZS9QYWdlcy9NZWRpYUJveCBbMCAwIDU5NS4yOCA4NDEuODldL0NvdW50IDEvS2lkc1sgMyAwIFJdPj5lbmRvYmoKMyAwIG9iago8PC9UeXBlL1BhZ2UvUGFyZW50IDIgMCBSL1Jlc291cmNlcyAzIDAgUi9Db250ZW50cyA0IDAgUj4+ZW5kb2JqCjQgMCBvYmoKPDwvRmlsdGVyL0ZsYXRlRGVjb2RlL0xlbmd0aCA0ND4+ZW5kb2JqCnhyZWYKMCA1CjAwMDAwMDAwMDAgNjU1MzUgZiAKMDAwMDAwMDAxMCAwMDAwMCBuIAowMDAwMDAwMDYwIDAwMDAwIG4gCjAwMDAwMDAxNTcgMDAwMDAgbiAKMDAwMDAwMDI1NyAwMDAwMCBuIAp0cmFpbGVyCjw8L1NpemUgNS9Sb290IDEgMCBSPj4Kc3RhcnR4cmVmCjM0OQolJUVPRgo=', NOW(), 'Resume', 1),
(7, 'ZhaoQiang_Certificate.jpg', 'Certificate', 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=', NOW(), 'Certificate', 1);

-- 对于student_id = 8 (李华)
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, remark, status) VALUES
(8, 'LiHua_Resume.pdf', 'Resume', 'data:application/pdf;base64,JVBERi0xLjQKJeLjz9MKMSAwIG9iago8PC9UeXBlL0NhdGFsb2cvUGFnZXMgMiAwIFI+ZW5kb2JqCjIgMCBvYmoKPDwvVHlwZS9QYWdlcy9NZWRpYUJveCBbMCAwIDU5NS4yOCA4NDEuODldL0NvdW50IDEvS2lkc1sgMyAwIFJdPj5lbmRvYmoKMyAwIG9iago8PC9UeXBlL1BhZ2UvUGFyZW50IDIgMCBSL1Jlc291cmNlcyAzIDAgUi9Db250ZW50cyA0IDAgUj4+ZW5kb2JqCjQgMCBvYmoKPDwvRmlsdGVyL0ZsYXRlRGVjb2RlL0xlbmd0aCA0ND4+ZW5kb2JqCnhyZWYKMCA1CjAwMDAwMDAwMDAgNjU1MzUgZiAKMDAwMDAwMDAxMCAwMDAwMCBuIAowMDAwMDAwMDYwIDAwMDAwIG4gCjAwMDAwMDAxNTcgMDAwMDAgbiAKMDAwMDAwMDI1NyAwMDAwMCBuIAp0cmFpbGVyCjw8L1NpemUgNS9Sb290IDEgMCBSPj4Kc3RhcnR4cmVmCjM0OQolJUVPRgo=', NOW(), 'Resume', 1),
(8, 'LiHua_Certificate.jpg', 'Certificate', 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=', NOW(), 'Certificate', 1);

-- 验证插入结果
SELECT * FROM student_archives WHERE student_id IN (7, 8);
