-- reset_token은 어떤 코드 경로에서도 읽거나 쓰지 않는 죽은 컬럼이다.
-- (비밀번호 재설정은 임시 비밀번호 이메일 발송 방식이라 토큰을 쓰지 않는다.)
ALTER TABLE member DROP COLUMN IF EXISTS reset_token;
