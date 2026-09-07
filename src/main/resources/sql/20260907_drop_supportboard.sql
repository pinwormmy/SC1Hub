-- 2026-09-07: retire the abandoned support board (문의게시판).
-- Last activity 2024-12-14, never linked from any menu. A full dump of the four tables and the
-- board_list row was taken before this ran (kept outside the repository). Idempotent.
DELETE FROM board_list WHERE board_title IN ('supportboard', 'supportBoard');
DROP TABLE IF EXISTS supportboard_views;
DROP TABLE IF EXISTS supportboard_recommend;
DROP TABLE IF EXISTS supportboard_comment;
DROP TABLE IF EXISTS supportboard;
