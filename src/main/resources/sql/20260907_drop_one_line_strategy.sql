-- 2026-09-07: retire the one-line strategy feature (한줄 공략).
-- 181 tips existed, 166 of them guest posts from the 2026-09 spam wave; the feature is removed
-- from the application in the same release. Full dumps of all five tables were taken before this
-- ran (kept outside the repository). The two *_ai_* tables belonged to an AI-draft feature that
-- was removed from the code earlier and only survived in the schema. Idempotent; child tables first.
DROP TABLE IF EXISTS one_line_strategy_ai_draft;
DROP TABLE IF EXISTS one_line_strategy_ai_daily_run;
DROP TABLE IF EXISTS one_line_strategy_recommendation;
DROP TABLE IF EXISTS one_line_strategy;
DROP TABLE IF EXISTS one_line_strategy_category;
