-- 게시판 마스터 더미 데이터
INSERT INTO BBS_MASTER (BBS_NAME, SKIN_TYPE, READ_AUTH, WRITE_AUTH, ADMIN_AUTH, DISPLAY_YN, SORT_ORDER, NOTICE_YN, PUBLISH_YN, ATTACHMENT_YN, ATTACHMENT_LIMIT, ATTACHMENT_SIZE) VALUES
('일반 게시판', 'BASIC', 'ROLE_USER', 'ROLE_USER', 'ROLE_ADMIN', 'Y', 'D', 'Y', 'Y', 'Y', 5, 10),
('FAQ 게시판', 'FAQ', 'ROLE_USER', 'ROLE_ADMIN', 'ROLE_ADMIN', 'Y', 'D', 'Y', 'Y', 'N', 0, 0),
('Q&A 게시판', 'QNA', 'ROLE_USER', 'ROLE_USER', 'ROLE_ADMIN', 'Y', 'D', 'Y', 'Y', 'Y', 3, 5),
('보도자료', 'PRESS', 'ROLE_USER', 'ROLE_ADMIN', 'ROLE_ADMIN', 'Y', 'D', 'Y', 'Y', 'Y', 5, 10),
('자료실', 'FORM', 'ROLE_USER', 'ROLE_ADMIN', 'ROLE_ADMIN', 'Y', 'D', 'Y', 'Y', 'Y', 10, 20);

-- 게시글 더미 데이터
INSERT INTO BBS_ARTICLE (BBS_ID, WRITER, TITLE, CONTENT, NOTICE_STATE, PUBLISH_STATE, HITS) VALUES
(1, 'user1', '첫 번째 일반 게시글', '일반 게시판의 첫 번째 게시글 내용입니다.', 'N', 'Y', 10),
(1, 'user2', '두 번째 일반 게시글', '일반 게시판의 두 번째 게시글 내용입니다.', 'N', 'Y', 5),
(1, 'user3', '일반 게시판 공지사항', '일반 게시판의 공지사항입니다.', 'Y', 'Y', 20),
(1, 'user1', '일반 게시글 4', '일반 게시판의 네 번째 게시글 내용입니다.', 'N', 'Y', 8),
(1, 'user2', '일반 게시글 5', '일반 게시판의 다섯 번째 게시글 내용입니다.', 'N', 'Y', 15);

INSERT INTO BBS_ARTICLE (BBS_ID, WRITER, TITLE, CONTENT, NOTICE_STATE, PUBLISH_STATE, HITS) VALUES
(2, 'admin', '자주 묻는 질문 1', 'FAQ 게시판의 첫 번째 질문과 답변입니다.', 'N', 'Y', 30),
(2, 'admin', '자주 묻는 질문 2', 'FAQ 게시판의 두 번째 질문과 답변입니다.', 'N', 'Y', 25),
(2, 'admin', 'FAQ 공지사항', 'FAQ 게시판의 공지사항입니다.', 'Y', 'Y', 40),
(2, 'admin', '자주 묻는 질문 3', 'FAQ 게시판의 세 번째 질문과 답변입니다.', 'N', 'Y', 20),
(2, 'admin', '자주 묻는 질문 4', 'FAQ 게시판의 네 번째 질문과 답변입니다.', 'N', 'Y', 15);

INSERT INTO BBS_ARTICLE (BBS_ID, WRITER, TITLE, CONTENT, NOTICE_STATE, PUBLISH_STATE, HITS) VALUES
(3, 'user1', 'Q&A 질문 1', 'Q&A 게시판의 첫 번째 질문입니다.', 'N', 'Y', 10),
(3, 'user2', 'Q&A 질문 2', 'Q&A 게시판의 두 번째 질문입니다.', 'N', 'Y', 8),
(3, 'admin', 'Q&A 공지사항', 'Q&A 게시판의 공지사항입니다.', 'Y', 'Y', 25),
(3, 'user3', 'Q&A 질문 3', 'Q&A 게시판의 세 번째 질문입니다.', 'N', 'Y', 12),
(3, 'user1', 'Q&A 질문 4', 'Q&A 게시판의 네 번째 질문입니다.', 'N', 'Y', 7);

INSERT INTO BBS_ARTICLE (BBS_ID, WRITER, TITLE, CONTENT, NOTICE_STATE, PUBLISH_STATE, HITS) VALUES
(4, 'admin', '보도자료 1', '첫 번째 보도자료 내용입니다.', 'N', 'Y', 50),
(4, 'admin', '보도자료 2', '두 번째 보도자료 내용입니다.', 'N', 'Y', 45),
(4, 'admin', '보도자료 공지', '보도자료 게시판의 공지사항입니다.', 'Y', 'Y', 60),
(4, 'admin', '보도자료 3', '세 번째 보도자료 내용입니다.', 'N', 'Y', 40),
(4, 'admin', '보도자료 4', '네 번째 보도자료 내용입니다.', 'N', 'Y', 35);

INSERT INTO BBS_ARTICLE (BBS_ID, WRITER, TITLE, CONTENT, NOTICE_STATE, PUBLISH_STATE, HITS) VALUES
(5, 'admin', '자료실 파일 1', '첫 번째 자료실 파일 설명입니다.', 'N', 'Y', 20),
(5, 'admin', '자료실 파일 2', '두 번째 자료실 파일 설명입니다.', 'N', 'Y', 18),
(5, 'admin', '자료실 공지', '자료실 게시판의 공지사항입니다.', 'Y', 'Y', 30),
(5, 'admin', '자료실 파일 3', '세 번째 자료실 파일 설명입니다.', 'N', 'Y', 15),
(5, 'admin', '자료실 파일 4', '네 번째 자료실 파일 설명입니다.', 'N', 'Y', 12); 