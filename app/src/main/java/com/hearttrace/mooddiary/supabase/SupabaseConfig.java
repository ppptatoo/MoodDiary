package com.hearttrace.mooddiary.supabase;

import com.hearttrace.mooddiary.BuildConfig;

/**
 * Supabase 配置常量
 * 
 * 密钥通过 Gradle BuildConfig 从 local.properties 注入（不提交 Git）。
 * 
 * 如需配置你自己的 Supabase 项目：
 *   1. 在项目根目录 local.properties 中添加：
 *        supabase.url=https://YOUR_PROJECT_ID.supabase.co
 *        supabase.anonkey=YOUR_ANON_KEY
 *   2. 或复制 local.properties.example 为 local.properties 后修改
 */
public class SupabaseConfig {
    /** Supabase 项目地址（来自 local.properties，不给 GitHub 看到） */
    public static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;

    /** Supabase 匿名公钥（来自 local.properties，不给 GitHub 看到） */
    public static final String SUPABASE_ANON_KEY = BuildConfig.SUPABASE_ANON_KEY;
}
