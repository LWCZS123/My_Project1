package com.example.my_project1.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * 安全存储工具类
 * 使用 Jetpack Security 提供的 EncryptedSharedPreferences 进行加密存储
 * 自动保存登录凭证（邮箱、SessionToken、时间戳）
 */
public class SecureStorage {

    private static final String PREFS_NAME = "secure_prefs";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_TOKEN = "sessionToken";
    private static final String KEY_TIMESTAMP = "timestamp"; // 保存登录时间

    // 会话有效期（毫秒）= 30天
    private static final long SESSION_VALIDITY = 30L * 24 * 60 * 60 * 1000;

    private static SharedPreferences getPrefs(Context context)
            throws GeneralSecurityException, IOException {
        String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
        return EncryptedSharedPreferences.create(
                PREFS_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );
    }

    /**
     * 保存会话信息
     */
    public static void saveSession(Context context, String email, String sessionToken) {
        try {
            SharedPreferences prefs = getPrefs(context);
            prefs.edit()
                    .putString(KEY_EMAIL, email)
                    .putString(KEY_TOKEN, sessionToken)
                    .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                    .apply();
            Log.d("SecureStorage", "Session 已加密保存");
        } catch (Exception e) {
            Log.e("SecureStorage", "加密保存失败：" + e.getMessage());
        }
    }

    /**
     * 获取SessionToken
     */
    public static String getSessionToken(Context context) {
        try {
            SharedPreferences prefs = getPrefs(context);
            String token = prefs.getString(KEY_TOKEN, null);

            // 检查是否过期
            long savedTime = prefs.getLong(KEY_TIMESTAMP, 0);
            if (System.currentTimeMillis() - savedTime > SESSION_VALIDITY) {
                Log.w("SecureStorage", "Session 已过期");
                clearSession(context);
                return null;
            }
            return token;
        } catch (Exception e) {
            Log.e("SecureStorage", "读取加密数据失败：" + e.getMessage());
            return null;
        }
    }

    /**
     * 清空所有加密数据
     */
    public static void clearSession(Context context) {
        try {
            SharedPreferences prefs = getPrefs(context);
            prefs.edit().clear().apply();
            Log.d("SecureStorage", "Session 已清空");
        } catch (Exception e) {
            Log.e("SecureStorage", "清空加密数据失败：" + e.getMessage());
        }
    }

    /**
     * 获取保存的邮箱
     */
    public static String getEmail(Context context) {
        try {
            SharedPreferences prefs = getPrefs(context);
            return prefs.getString(KEY_EMAIL, null);
        } catch (Exception e) {
            return null;
        }
    }
}
