-- ============================================
-- MoodDiary Supabase 数据库建表脚本
-- ============================================
-- 使用说明：
--   1. 登录 Supabase Dashboard → SQL Editor
--   2. 复制本脚本全部内容，粘贴并点击 Run
--   3. 执行完成后，表即创建完毕
-- ============================================

-- ==================== 1. 创建日记表 ====================

CREATE TABLE IF NOT EXISTS public.mood_entries (
    id              BIGSERIAL PRIMARY KEY,
    user_id         TEXT NOT NULL,                  -- Supabase Auth 用户 UUID
    timestamp       BIGINT NOT NULL,               -- 日记记录的时间戳（毫秒）
    update_time     BIGINT NOT NULL DEFAULT 0,     -- 最后更新时间戳（毫秒）
    text            TEXT NOT NULL DEFAULT '',       -- 日记正文
    emotion_label   TEXT NOT NULL DEFAULT '',       -- 情绪标签
    image_path      TEXT NOT NULL DEFAULT '',       -- 图片路径
    is_favorite     BOOLEAN NOT NULL DEFAULT false, -- 是否收藏
    ai_image_path   TEXT DEFAULT NULL,              -- AI 生成图片路径
    ai_quote        TEXT DEFAULT NULL               -- AI 生成名言
);

-- 为常用查询建立索引
CREATE INDEX IF NOT EXISTS idx_mood_entries_user_id
    ON public.mood_entries (user_id);

CREATE INDEX IF NOT EXISTS idx_mood_entries_timestamp
    ON public.mood_entries (user_id, timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_mood_entries_update_time
    ON public.mood_entries (user_id, update_time DESC);


-- ==================== 2. 创建用户资料表 ====================

CREATE TABLE IF NOT EXISTS public.user_profiles (
    id          TEXT PRIMARY KEY,                    -- Supabase Auth 用户 UUID
    username    TEXT NOT NULL,                       -- 用户昵称
    signature   TEXT NOT NULL DEFAULT '记录生活的每一刻', -- 个性签名
    avatar_url  TEXT NOT NULL DEFAULT '',            -- 头像 URL
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()  -- 更新时间
);


-- ==================== 3. 启用 Row Level Security ====================

ALTER TABLE public.mood_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_profiles ENABLE ROW LEVEL SECURITY;


-- ==================== 4. RLS 策略 ====================

-- mood_entries：用户只能读写自己的日记
CREATE POLICY "用户只能查看自己的日记"
    ON public.mood_entries
    FOR SELECT
    USING (auth.uid()::text = user_id);

CREATE POLICY "用户只能插入自己的日记"
    ON public.mood_entries
    FOR INSERT
    WITH CHECK (auth.uid()::text = user_id);

CREATE POLICY "用户只能更新自己的日记"
    ON public.mood_entries
    FOR UPDATE
    USING (auth.uid()::text = user_id)
    WITH CHECK (auth.uid()::text = user_id);

CREATE POLICY "用户只能删除自己的日记"
    ON public.mood_entries
    FOR DELETE
    USING (auth.uid()::text = user_id);

-- user_profiles：用户只能读写自己的资料
CREATE POLICY "用户只能查看自己的资料"
    ON public.user_profiles
    FOR SELECT
    USING (auth.uid()::text = id);

CREATE POLICY "用户只能插入自己的资料"
    ON public.user_profiles
    FOR INSERT
    WITH CHECK (auth.uid()::text = id);

CREATE POLICY "用户只能更新自己的资料"
    ON public.user_profiles
    FOR UPDATE
    USING (auth.uid()::text = id)
    WITH CHECK (auth.uid()::text = id);


-- ==================== 5. 自动更新 updated_at ====================

CREATE OR REPLACE FUNCTION public.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON public.user_profiles
    FOR EACH ROW
    EXECUTE FUNCTION public.update_updated_at_column();
