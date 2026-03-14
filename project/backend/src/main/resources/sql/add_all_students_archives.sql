-- 为所有学生添加简历和证书测试数据
-- 为student_id = 1 (张伟)
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, remark, status) VALUES
(1, 'ZhangWei_Resume.pdf', 'Resume', 'data:application/pdf;base64,JVBERi0xLjQKJeLjz9MKMSAwIG9iago8PC9UeXBlL0NhdGFsb2cvUGFnZXMgMiAwIFI+ZW5kb2JqCjIgMCBvYmoKPDwvVHlwZS9QYWdlcy9NZWRpYUJveCBbMCAwIDU5NS4yOCA4NDEuODldL0NvdW50IDEvS2lkc1sgMyAwIFJdPj5lbmRvYmoKMyAwIG9iago8PC9UeXBlL1BhZ2UvUGFyZW50IDIgMCBSL1Jlc291cmNlcyAzIDAgUi9Db250ZW50cyA0IDAgUj4+ZW5kb2JqCjQgMCBvYmoKPDwvRmlsdGVyL0ZsYXRlRGVjb2RlL0xlbmd0aCA0ND4+ZW5kb2JqCnhyZWYKMCA1CjAwMDAwMDAwMDAgNjU1MzUgZiAKMDAwMDAwMDAxMCAwMDAwMCBuIAowMDAwMDAwMDYwIDAwMDAwIG4gCjAwMDAwMDAxNTcgMDAwMDAgbiAKMDAwMDAwMDI1NyAwMDAwMCBuIAp0cmFpbGVyCjw8L1NpemUgNS9Sb290IDEgMCBSPj4Kc3RhcnR4cmVmCjM0OQolJUVPRgo=', NOW(), 'Resume', 1),
(1, 'ZhangWei_Certificate.jpg', 'Certificate', 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=', NOW(), 'Certificate', 1);

-- 为student_id = 2 (李娜)
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, remark, status) VALUES
(2, 'LiNa_Resume.pdf', 'Resume', 'data:application/pdf;base64,JVBERi0xLjQKJeLjz9MKMSAwIG9iago8PC9UeXBlL0NhdGFsb2cvUGFnZXMgMiAwIFI+ZW5kb2JqCjIgMCBvYmoKPDwvVHlwZS9QYWdlcy9NZWRpYUJveCBbMCAwIDU5NS4yOCA4NDEuODldL0NvdW50IDEvS2lkc1sgMyAwIFJdPj5lbmRvYmoKMyAwIG9iago8PC9UeXBlL1BhZ2UvUGFyZW50IDIgMCBSL1Jlc291cmNlcyAzIDAgUi9Db250ZW50cyA0IDAgUj4+ZW5kb2JqCjQgMCBvYmoKPDwvRmlsdGVyL0ZsYXRlRGVjb2RlL0xlbmd0aCA0ND4+ZW5kb2JqCnhyZWYKMCA1CjAwMDAwMDAwMDAgNjU1MzUgZiAKMDAwMDAwMDAxMCAwMDAwMCBuIAowMDAwMDAwMDYwIDAwMDAwIG4gCjAwMDAwMDAxNTcgMDAwMDAgbiAKMDAwMDAwMDI1NyAwMDAwMCBuIAp0cmFpbGVyCjw8L1NpemUgNS9Sb290IDEgMCBSPj4Kc3RhcnR4cmVmCjM0OQolJUVPRgo=', NOW(), 'Resume', 1),
(2, 'LiNa_Certificate.jpg', 'Certificate', 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=', NOW(), 'Certificate', 1);

-- 为student_id = 3 (王芳)
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, remark, status) VALUES
(3, 'WangFang_Resume.pdf', 'Resume', 'data:application/pdf;base64,JVBERi0xLjQKJeLjz9MKMSAwIG9iago8PC9UeXBlL0NhdGFsb2cvUGFnZXMgMiAwIFI+ZW5kb2JqCjIgMCBvYmoKPDwvVHlwZS9QYWdlcy9NZWRpYUJveCBbMCAwIDU5NS4yOCA4NDEuODldL0NvdW50IDEvS2lkc1sgMyAwIFJdPj5lbmRvYmoKMyAwIG9iago8PC9UeXBlL1BhZ2UvUGFyZW50IDIgMCBSL1Jlc291cmNlcyAzIDAgUi9Db250ZW50cyA0IDAgUj4+ZW5kb2JqCjQgMCBvYmoKPDwvRmlsdGVyL0ZsYXRlRGVjb2RlL0xlbmd0aCA0ND4+ZW5kb2JqCnhyZWYKMCA1CjAwMDAwMDAwMDAgNjU1MzUgZiAKMDAwMDAwMDAxMCAwMDAwMCBuIAowMDAwMDAwMDYwIDAwMDAwIG4gCjAwMDAwMDAxNTcgMDAwMDAgbiAKMDAwMDAwMDI1NyAwMDAwMCBuIAp0cmFpbGVyCjw8L1NpemUgNS9Sb290IDEgMCBSPj4Kc3RhcnR4cmVmCjM0OQolJUVPRgo=', NOW(), 'Resume', 1),
(3, 'WangFang_Certificate.jpg', 'Certificate', 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=', NOW(), 'Certificate', 1);

-- 为student_id = 4 (刘洋)
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, remark, status) VALUES
(4, 'LiuYang_Resume.pdf', 'Resume', 'data:application/pdf;base64,JVBERi0xLjQKJeLjz9MKMSAwIG9iago8PC9UeXBlL0NhdGFsb2cvUGFnZXMgMiAwIFI+ZW5kb2JqCjIgMCBvYmoKPDwvVHlwZS9QYWdlcy9NZWRpYUJveCBbMCAwIDU5NS4yOCA4NDEuODldL0NvdW50IDEvS2lkc1sgMyAwIFJdPj5lbmRvYmoKMyAwIG9iago8PC9UeXBlL1BhZ2UvUGFyZW50IDIgMCBSL1Jlc291cmNlcyAzIDAgUi9Db250ZW50cyA0IDAgUj4+ZW5kb2JqCjQgMCBvYmoKPDwvRmlsdGVyL0ZsYXRlRGVjb2RlL0xlbmd0aCA0ND4+ZW5kb2JqCnhyZWYKMCA1CjAwMDAwMDAwMDAgNjU1MzUgZiAKMDAwMDAwMDAxMCAwMDAwMCBuIAowMDAwMDAwMDYwIDAwMDAwIG4gCjAwMDAwMDAxNTcgMDAwMDAgbiAKMDAwMDAwMDI1NyAwMDAwMCBuIAp0cmFpbGVyCjw8L1NpemUgNS9Sb290IDEgMCBSPj4Kc3RhcnR4cmVmCjM0OQolJUVPRgo=', NOW(), 'Resume', 1),
(4, 'LiuYang_Certificate.jpg', 'Certificate', 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=', NOW(), 'Certificate', 1);

-- 为student_id = 5 (陈静)
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, remark, status) VALUES
(5, 'ChenJing_Resume.pdf', 'Resume', 'data:application/pdf;base64,JVBERi0xLjQKJeLjz9MKMSAwIG9iago8PC9UeXBlL0NhdGFsb2cvUGFnZXMgMiAwIFI+ZW5kb2JqCjIgMCBvYmoKPDwvVHlwZS9QYWdlcy9NZWRpYUJveCBbMCAwIDU5NS4yOCA4NDEuODldL0NvdW50IDEvS2lkc1sgMyAwIFJdPj5lbmRvYmoKMyAwIG9iago8PC9UeXBlL1BhZ2UvUGFyZW50IDIgMCBSL1Jlc291cmNlcyAzIDAgUi9Db250ZW50cyA0IDAgUj4+ZW5kb2JqCjQgMCBvYmoKPDwvRmlsdGVyL0ZsYXRlRGVjb2RlL0xlbmd0aCA0ND4+ZW5kb2JqCnhyZWYKMCA1CjAwMDAwMDAwMDAgNjU1MzUgZiAKMDAwMDAwMDAxMCAwMDAwMCBuIAowMDAwMDAwMDYwIDAwMDAwIG4gCjAwMDAwMDAxNTcgMDAwMDAgbiAKMDAwMDAwMDI1NyAwMDAwMCBuIAp0cmFpbGVyCjw8L1NpemUgNS9Sb290IDEgMCBSPj4Kc3RhcnR4cmVmCjM0OQolJUVPRgo=', NOW(), 'Resume', 1),
(5, 'ChenJing_Certificate.jpg', 'Certificate', 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=', NOW(), 'Certificate', 1);

-- 为student_id = 6 (杨明)
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, remark, status) VALUES
(6, 'YangMing_Resume.pdf', 'Resume', 'data:application/pdf;base64,JVBERi0xLjQKJeLjz9MKMSAwIG9iago8PC9UeXBlL0NhdGFsb2cvUGFnZXMgMiAwIFI+ZW5kb2JqCjIgMCBvYmoKPDwvVHlwZS9QYWdlcy9NZWRpYUJveCBbMCAwIDU5NS4yOCA4NDEuODldL0NvdW50IDEvS2lkc1sgMyAwIFJdPj5lbmRvYmoKMyAwIG9iago8PC9UeXBlL1BhZ2UvUGFyZW50IDIgMCBSL1Jlc291cmNlcyAzIDAgUi9Db250ZW50cyA0IDAgUj4+ZW5kb2JqCjQgMCBvYmoKPDwvRmlsdGVyL0ZsYXRlRGVjb2RlL0xlbmd0aCA0ND4+ZW5kb2JqCnhyZWYKMCA1CjAwMDAwMDAwMDAgNjU1MzUgZiAKMDAwMDAwMDAxMCAwMDAwMCBuIAowMDAwMDAwMDYwIDAwMDAwIG4gCjAwMDAwMDAxNTcgMDAwMDAgbiAKMDAwMDAwMDI1NyAwMDAwMCBuIAp0cmFpbGVyCjw8L1NpemUgNS9Sb290IDEgMCBSPj4Kc3RhcnR4cmVmCjM0OQolJUVPRgo=', NOW(), 'Resume', 1),
(6, 'YangMing_Certificate.jpg', 'Certificate', 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=', NOW(), 'Certificate', 1);

-- 为student_id = 9 (周杰)
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, remark, status) VALUES
(9, 'ZhouJie_Resume.pdf', 'Resume', 'data:application/pdf;base64,JVBERi0xLjQKJeLjz9MKMSAwIG9iago8PC9UeXBlL0NhdGFsb2cvUGFnZXMgMiAwIFI+ZW5kb2JqCjIgMCBvYmoKPDwvVHlwZS9QYWdlcy9NZWRpYUJveCBbMCAwIDU5NS4yOCA4NDEuODldL0NvdW50IDEvS2lkc1sgMyAwIFJdPj5lbmRvYmoKMyAwIG9iago8PC9UeXBlL1BhZ2UvUGFyZW50IDIgMCBSL1Jlc291cmNlcyAzIDAgUi9Db250ZW50cyA0IDAgUj4+ZW5kb2JqCjQgMCBvYmoKPDwvRmlsdGVyL0ZsYXRlRGVjb2RlL0xlbmd0aCA0ND4+ZW5kb2JqCnhyZWYKMCA1CjAwMDAwMDAwMDAgNjU1MzUgZiAKMDAwMDAwMDAxMCAwMDAwMCBuIAowMDAwMDAwMDYwIDAwMDAwIG4gCjAwMDAwMDAxNTcgMDAwMDAgbiAKMDAwMDAwMDI1NyAwMDAwMCBuIAp0cmFpbGVyCjw8L1NpemUgNS9Sb290IDEgMCBSPj4Kc3RhcnR4cmVmCjM0OQolJUVPRgo=', NOW(), 'Resume', 1),
(9, 'ZhouJie_Certificate.jpg', 'Certificate', 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=', NOW(), 'Certificate', 1);

-- 为student_id = 10 (吴敏)
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, remark, status) VALUES
(10, 'WuMin_Resume.pdf', 'Resume', 'data:application/pdf;base64,JVBERi0xLjQKJeLjz9MKMSAwIG9iago8PC9UeXBlL0NhdGFsb2cvUGFnZXMgMiAwIFI+ZW5kb2JqCjIgMCBvYmoKPDwvVHlwZS9QYWdlcy9NZWRpYUJveCBbMCAwIDU5NS4yOCA4NDEuODldL0NvdW50IDEvS2lkc1sgMyAwIFJdPj5lbmRvYmoKMyAwIG9iago8PC9UeXBlL1BhZ2UvUGFyZW50IDIgMCBSL1Jlc291cmNlcyAzIDAgUi9Db250ZW50cyA0IDAgUj4+ZW5kb2JqCjQgMCBvYmoKPDwvRmlsdGVyL0ZsYXRlRGVjb2RlL0xlbmd0aCA0ND4+ZW5kb2JqCnhyZWYKMCA1CjAwMDAwMDAwMDAgNjU1MzUgZiAKMDAwMDAwMDAxMCAwMDAwMCBuIAowMDAwMDAwMDYwIDAwMDAwIG4gCjAwMDAwMDAxNTcgMDAwMDAgbiAKMDAwMDAwMDI1NyAwMDAwMCBuIAp0cmFpbGVyCjw8L1NpemUgNS9Sb290IDEgMCBSPj4Kc3RhcnR4cmVmCjM0OQolJUVPRgo=', NOW(), 'Resume', 1),
(10, 'WuMin_Certificate.jpg', 'Certificate', 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=', NOW(), 'Certificate', 1);

-- 为student_id = 11 (徐刚)
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, remark, status) VALUES
(11, 'XuGang_Resume.pdf', 'Resume', 'data:application/pdf;base64,JVBERi0xLjQKJeLjz9MKMSAwIG9iago8PC9UeXBlL0NhdGFsb2cvUGFnZXMgMiAwIFI+ZW5kb2JqCjIgMCBvYmoKPDwvVHlwZS9QYWdlcy9NZWRpYUJveCBbMCAwIDU5NS4yOCA4NDEuODldL0NvdW50IDEvS2lkc1sgMyAwIFJdPj5lbmRvYmoKMyAwIG9iago8PC9UeXBlL1BhZ2UvUGFyZW50IDIgMCBSL1Jlc291cmNlcyAzIDAgUi9Db250ZW50cyA0IDAgUj4+ZW5kb2JqCjQgMCBvYmoKPDwvRmlsdGVyL0ZsYXRlRGVjb2RlL0xlbmd0aCA0ND4+ZW5kb2JqCnhyZWYKMCA1CjAwMDAwMDAwMDAgNjU1MzUgZiAKMDAwMDAwMDAxMCAwMDAwMCBuIAowMDAwMDAwMDYwIDAwMDAwIG4gCjAwMDAwMDAxNTcgMDAwMDAgbiAKMDAwMDAwMDI1NyAwMDAwMCBuIAp0cmFpbGVyCjw8L1NpemUgNS9Sb290IDEgMCBSPj4Kc3RhcnR4cmVmCjM0OQolJUVPRgo=', NOW(), 'Resume', 1),
(11, 'XuGang_Certificate.jpg', 'Certificate', 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=', NOW(), 'Certificate', 1);

-- 为student_id = 12 (孙芳)
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, remark, status) VALUES
(12, 'SunFang_Resume.pdf', 'Resume', 'data:application/pdf;base64,JVBERi0xLjQKJeLjz9MKMSAwIG9iago8PC9UeXBlL0NhdGFsb2cvUGFnZXMgMiAwIFI+ZW5kb2JqCjIgMCBvYmoKPDwvVHlwZS9QYWdlcy9NZWRpYUJveCBbMCAwIDU5NS4yOCA4NDEuODldL0NvdW50IDEvS2lkc1sgMyAwIFJdPj5lbmRvYmoKMyAwIG9iago8PC9UeXBlL1BhZ2UvUGFyZW50IDIgMCBSL1Jlc291cmNlcyAzIDAgUi9Db250ZW50cyA0IDAgUj4+ZW5kb2JqCjQgMCBvYmoKPDwvRmlsdGVyL0ZsYXRlRGVjb2RlL0xlbmd0aCA0ND4+ZW5kb2JqCnhyZWYKMCA1CjAwMDAwMDAwMDAgNjU1MzUgZiAKMDAwMDAwMDAxMCAwMDAwMCBuIAowMDAwMDAwMDYwIDAwMDAwIG4gCjAwMDAwMDAxNTcgMDAwMDAgbiAKMDAwMDAwMDI1NyAwMDAwMCBuIAp0cmFpbGVyCjw8L1NpemUgNS9Sb290IDEgMCBSPj4Kc3RhcnR4cmVmCjM0OQolJUVPRgo=', NOW(), 'Resume', 1),
(12, 'SunFang_Certificate.jpg', 'Certificate', 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=', NOW(), 'Certificate', 1);

-- 为student_id = 13 (马伟)
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, remark, status) VALUES
(13, 'MaWei_Resume.pdf', 'Resume', 'data:application/pdf;base64,JVBERi0xLjQKJeLjz9MKMSAwIG9iago8PC9UeXBlL0NhdGFsb2cvUGFnZXMgMiAwIFI+ZW5kb2JqCjIgMCBvYmoKPDwvVHlwZS9QYWdlcy9NZWRpYUJveCBbMCAwIDU5NS4yOCA4NDEuODldL0NvdW50IDEvS2lkc1sgMyAwIFJdPj5lbmRvYmoKMyAwIG9iago8PC9UeXBlL1BhZ2UvUGFyZW50IDIgMCBSL1Jlc291cmNlcyAzIDAgUi9Db250ZW50cyA0IDAgUj4+ZW5kb2JqCjQgMCBvYmoKPDwvRmlsdGVyL0ZsYXRlRGVjb2RlL0xlbmd0aCA0ND4+ZW5kb2JqCnhyZWYKMCA1CjAwMDAwMDAwMDAgNjU1MzUgZiAKMDAwMDAwMDAxMCAwMDAwMCBuIAowMDAwMDAwMDYwIDAwMDAwIG4gCjAwMDAwMDAxNTcgMDAwMDAgbiAKMDAwMDAwMDI1NyAwMDAwMCBuIAp0cmFpbGVyCjw8L1NpemUgNS9Sb290IDEgMCBSPj4Kc3RhcnR4cmVmCjM0OQolJUVPRgo=', NOW(), 'Resume', 1),
(13, 'MaWei_Certificate.jpg', 'Certificate', 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=', NOW(), 'Certificate', 1);

-- 为student_id = 14 (朱丽)
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, remark, status) VALUES
(14, 'ZhuLi_Resume.pdf', 'Resume', 'data:application/pdf;base64,JVBERi0xLjQKJeLjz9MKMSAwIG9iago8PC9UeXBlL0NhdGFsb2cvUGFnZXMgMiAwIFI+ZW5kb2JqCjIgMCBvYmoKPDwvVHlwZS9QYWdlcy9NZWRpYUJveCBbMCAwIDU5NS4yOCA4NDEuODldL0NvdW50IDEvS2lkc1sgMyAwIFJdPj5lbmRvYmoKMyAwIG9iago8PC9UeXBlL1BhZ2UvUGFyZW50IDIgMCBSL1Jlc291cmNlcyAzIDAgUi9Db250ZW50cyA0IDAgUj4+ZW5kb2JqCjQgMCBvYmoKPDwvRmlsdGVyL0ZsYXRlRGVjb2RlL0xlbmd0aCA0ND4+ZW5kb2JqCnhyZWYKMCA1CjAwMDAwMDAwMDAgNjU1MzUgZiAKMDAwMDAwMDAxMCAwMDAwMCBuIAowMDAwMDAwMDYwIDAwMDAwIG4gCjAwMDAwMDAxNTcgMDAwMDAgbiAKMDAwMDAwMDI1NyAwMDAwMCBuIAp0cmFpbGVyCjw8L1NpemUgNS9Sb290IDEgMCBSPj4Kc3RhcnR4cmVmCjM0OQolJUVPRgo=', NOW(), 'Resume', 1),
(14, 'ZhuLi_Certificate.jpg', 'Certificate', 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=', NOW(), 'Certificate', 1);

-- 为student_id = 15 (胡强)
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, remark, status) VALUES
(15, 'HuQiang_Resume.pdf', 'Resume', 'data:application/pdf;base64,JVBERi0xLjQKJeLjz9MKMSAwIG9iago8PC9UeXBlL0NhdGFsb2cvUGFnZXMgMiAwIFI+ZW5kb2JqCjIgMCBvYmoKPDwvVHlwZS9QYWdlcy9NZWRpYUJveCBbMCAwIDU5NS4yOCA4NDEuODldL0NvdW50IDEvS2lkc1sgMyAwIFJdPj5lbmRvYmoKMyAwIG9iago8PC9UeXBlL1BhZ2UvUGFyZW50IDIgMCBSL1Jlc291cmNlcyAzIDAgUi9Db250ZW50cyA0IDAgUj4+ZW5kb2JqCjQgMCBvYmoKPDwvRmlsdGVyL0ZsYXRlRGVjb2RlL0xlbmd0aCA0ND4+ZW5kb2JqCnhyZWYKMCA1CjAwMDAwMDAwMDAgNjU1MzUgZiAKMDAwMDAwMDAxMCAwMDAwMCBuIAowMDAwMDAwMDYwIDAwMDAwIG4gCjAwMDAwMDAxNTcgMDAwMDAgbiAKMDAwMDAwMDI1NyAwMDAwMCBuIAp0cmFpbGVyCjw8L1NpemUgNS9Sb290IDEgMCBSPj4Kc3RhcnR4cmVmCjM0OQolJUVPRgo=', NOW(), 'Resume', 1),
(15, 'HuQiang_Certificate.jpg', 'Certificate', 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=', NOW(), 'Certificate', 1);

-- 为student_id = 16 (郭芳)
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, remark, status) VALUES
(16, 'GuoFang_Resume.pdf', 'Resume', 'data:application/pdf;base64,JVBERi0xLjQKJeLjz9MKMSAwIG9iago8PC9UeXBlL0NhdGFsb2cvUGFnZXMgMiAwIFI+ZW5kb2JqCjIgMCBvYmoKPDwvVHlwZS9QYWdlcy9NZWRpYUJveCBbMCAwIDU5NS4yOCA4NDEuODldL0NvdW50IDEvS2lkc1sgMyAwIFJdPj5lbmRvYmoKMyAwIG9iago8PC9UeXBlL1BhZ2UvUGFyZW50IDIgMCBSL1Jlc291cmNlcyAzIDAgUi9Db250ZW50cyA0IDAgUj4+ZW5kb2JqCjQgMCBvYmoKPDwvRmlsdGVyL0ZsYXRlRGVjb2RlL0xlbmd0aCA0ND4+ZW5kb2JqCnhyZWYKMCA1CjAwMDAwMDAwMDAgNjU1MzUgZiAKMDAwMDAwMDAxMCAwMDAwMCBuIAowMDAwMDAwMDYwIDAwMDAwIG4gCjAwMDAwMDAxNTcgMDAwMDAgbiAKMDAwMDAwMDI1NyAwMDAwMCBuIAp0cmFpbGVyCjw8L1NpemUgNS9Sb290IDEgMCBSPj4Kc3RhcnR4cmVmCjM0OQolJUVPRgo=', NOW(), 'Resume', 1),
(16, 'GuoFang_Certificate.jpg', 'Certificate', 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=', NOW(), 'Certificate', 1);

-- 为student_id = 17 (何伟)
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, remark, status) VALUES
(17, 'HeWei_Resume.pdf', 'Resume', 'data:application/pdf;base64,JVBERi0xLjQKJeLjz9MKMSAwIG9iago8PC9UeXBlL0NhdGFsb2cvUGFnZXMgMiAwIFI+ZW5kb2JqCjIgMCBvYmoKPDwvVHlwZS9QYWdlcy9NZWRpYUJveCBbMCAwIDU5NS4yOCA4NDEuODldL0NvdW50IDEvS2lkc1sgMyAwIFJdPj5lbmRvYmoKMyAwIG9iago8PC9UeXBlL1BhZ2UvUGFyZW50IDIgMCBSL1Jlc291cmNlcyAzIDAgUi9Db250ZW50cyA0IDAgUj4+ZW5kb2JqCjQgMCBvYmoKPDwvRmlsdGVyL0ZsYXRlRGVjb2RlL0xlbmd0aCA0ND4+ZW5kb2JqCnhyZWYKMCA1CjAwMDAwMDAwMDAgNjU1MzUgZiAKMDAwMDAwMDAxMCAwMDAwMCBuIAowMDAwMDAwMDYwIDAwMDAwIG4gCjAwMDAwMDAxNTcgMDAwMDAgbiAKMDAwMDAwMDI1NyAwMDAwMCBuIAp0cmFpbGVyCjw8L1NpemUgNS9Sb290IDEgMCBSPj4Kc3RhcnR4cmVmCjM0OQolJUVPRgo=', NOW(), 'Resume', 1),
(17, 'HeWei_Certificate.jpg', 'Certificate', 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=', NOW(), 'Certificate', 1);

-- 为student_id = 18 (高丽)
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, remark, status) VALUES
(18, 'GaoLi_Resume.pdf', 'Resume', 'data:application/pdf;base64,JVBERi0xLjQKJeLjz9MKMSAwIG9iago8PC9UeXBlL0NhdGFsb2cvUGFnZXMgMiAwIFI+ZW5kb2JqCjIgMCBvYmoKPDwvVHlwZS9QYWdlcy9NZWRpYUJveCBbMCAwIDU5NS4yOCA4NDEuODldL0NvdW50IDEvS2lkc1sgMyAwIFJdPj5lbmRvYmoKMyAwIG9iago8PC9UeXBlL1BhZ2UvUGFyZW50IDIgMCBSL1Jlc291cmNlcyAzIDAgUi9Db250ZW50cyA0IDAgUj4+ZW5kb2JqCjQgMCBvYmoKPDwvRmlsdGVyL0ZsYXRlRGVjb2RlL0xlbmd0aCA0ND4+ZW5kb2JqCnhyZWYKMCA1CjAwMDAwMDAwMDAgNjU1MzUgZiAKMDAwMDAwMDAxMCAwMDAwMCBuIAowMDAwMDAwMDYwIDAwMDAwIG4gCjAwMDAwMDAxNTcgMDAwMDAgbiAKMDAwMDAwMDI1NyAwMDAwMCBuIAp0cmFpbGVyCjw8L1NpemUgNS9Sb290IDEgMCBSPj4Kc3RhcnR4cmVmCjM0OQolJUVPRgo=', NOW(), 'Resume', 1),
(18, 'GaoLi_Certificate.jpg', 'Certificate', 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=', NOW(), 'Certificate', 1);

-- 为student_id = 19 (林强)
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, remark, status) VALUES
(19, 'LinQiang_Resume.pdf', 'Resume', 'data:application/pdf;base64,JVBERi0xLjQKJeLjz9MKMSAwIG9iago8PC9UeXBlL0NhdGFsb2cvUGFnZXMgMiAwIFI+ZW5kb2JqCjIgMCBvYmoKPDwvVHlwZS9QYWdlcy9NZWRpYUJveCBbMCAwIDU5NS4yOCA4NDEuODldL0NvdW50IDEvS2lkc1sgMyAwIFJdPj5lbmRvYmoKMyAwIG9iago8PC9UeXBlL1BhZ2UvUGFyZW50IDIgMCBSL1Jlc291cmNlcyAzIDAgUi9Db250ZW50cyA0IDAgUj4+ZW5kb2JqCjQgMCBvYmoKPDwvRmlsdGVyL0ZsYXRlRGVjb2RlL0xlbmd0aCA0ND4+ZW5kb2JqCnhyZWYKMCA1CjAwMDAwMDAwMDAgNjU1MzUgZiAKMDAwMDAwMDAxMCAwMDAwMCBuIAowMDAwMDAwMDYwIDAwMDAwIG4gCjAwMDAwMDAxNTcgMDAwMDAgbiAKMDAwMDAwMDI1NyAwMDAwMCBuIAp0cmFpbGVyCjw8L1NpemUgNS9Sb290IDEgMCBSPj4Kc3RhcnR4cmVmCjM0OQolJUVPRgo=', NOW(), 'Resume', 1),
(19, 'LinQiang_Certificate.jpg', 'Certificate', 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=', NOW(), 'Certificate', 1);

-- 为student_id = 20 (罗芳)
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, remark, status) VALUES
(20, 'LuoFang_Resume.pdf', 'Resume', 'data:application/pdf;base64,JVBERi0xLjQKJeLjz9MKMSAwIG9iago8PC9UeXBlL0NhdGFsb2cvUGFnZXMgMiAwIFI+ZW5kb2JqCjIgMCBvYmoKPDwvVHlwZS9QYWdlcy9NZWRpYUJveCBbMCAwIDU5NS4yOCA4NDEuODldL0NvdW50IDEvS2lkc1sgMyAwIFJdPj5lbmRvYmoKMyAwIG9iago8PC9UeXBlL1BhZ2UvUGFyZW50IDIgMCBSL1Jlc291cmNlcyAzIDAgUi9Db250ZW50cyA0IDAgUj4+ZW5kb2JqCjQgMCBvYmoKPDwvRmlsdGVyL0ZsYXRlRGVjb2RlL0xlbmd0aCA0ND4+ZW5kb2JqCnhyZWYKMCA1CjAwMDAwMDAwMDAgNjU1MzUgZiAKMDAwMDAwMDAxMCAwMDAwMCBuIAowMDAwMDAwMDYwIDAwMDAwIG4gCjAwMDAwMDAxNTcgMDAwMDAgbiAKMDAwMDAwMDI1NyAwMDAwMCBuIAp0cmFpbGVyCjw8L1NpemUgNS9Sb290IDEgMCBSPj4Kc3RhcnR4cmVmCjM0OQolJUVPRgo=', NOW(), 'Resume', 1),
(20, 'LuoFang_Certificate.jpg', 'Certificate', 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=', NOW(), 'Certificate', 1);

-- 验证插入结果
SELECT COUNT(*) AS total_archives FROM student_archives;
