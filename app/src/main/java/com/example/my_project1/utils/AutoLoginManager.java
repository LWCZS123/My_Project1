package com.example.my_project1.utils;

import android.content.Context;
import android.util.Log;

import cn.bmob.v3.BmobUser;

/**
 * 自动登录管理器（适配最新版 Bmob SDK）
 * 使用 BmobUser.getCurrentUser() 检查会话有效性
 * 不再使用 loginBySessionToken / setCurrentUser 等旧接口
 */
public class AutoLoginManager {

    public interface AutoLoginCallback {
        void onLoginSuccess(BmobUser user);
        void onLoginFailed(String reason);
    }

    /**
     * 检查自动登录状态
     */
    public static void checkAutoLogin(Context context, AutoLoginCallback callback) {
        String sessionToken = SecureStorage.getSessionToken(context);
        if (sessionToken == null) {
            callback.onLoginFailed("本地凭证不存在或已过期");
            return;
        }

        try {
            // 1:从 Bmob SDK 获取当前用户对象
            BmobUser currentUser = BmobUser.getCurrentUser(BmobUser.class);

            // 2:判断当前用户是否已登录且 Token 一致
            if (currentUser != null && sessionToken.equals(currentUser.getSessionToken())) {
                Log.d("AutoLogin", "本地Session有效，自动登录成功");
                callback.onLoginSuccess(currentUser);
            } else {
                Log.w("AutoLogin", "本地Session无效或用户状态丢失");
                SecureStorage.clearSession(context);
                callback.onLoginFailed("登录状态无效，请重新登录");
            }
        } catch (Exception e) {
            Log.e("AutoLogin", "自动登录异常：" + e.getMessage());
            SecureStorage.clearSession(context);
            callback.onLoginFailed("系统异常，请重新登录");
        }
    }
}
