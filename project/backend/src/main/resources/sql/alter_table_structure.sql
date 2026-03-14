-- 修改student_archives表的file_url列长度，使其能够存储base64 data URL
ALTER TABLE student_archives MODIFY COLUMN file_url TEXT;
