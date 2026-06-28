package com.example.my_project1.data.repository.user;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.example.my_project1.data.model.common.ApiResponse;
import com.example.my_project1.utils.AppExecutors;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import cn.bmob.v3.BmobUser;
import cn.bmob.v3.exception.BmobException;
import cn.bmob.v3.listener.UpdateListener;

/**
 * ChangePasswordRepository - 修改/重置密码仓库层
 * -------------------------------------------------------
 * 支持两种模式：
 *
 *   MODE_CHANGE（修改密码）：
 *     已登录用户在设置中主动修改密码
 *     调用 BmobUser.updateCurrentUserPassword(oldPwd, newPwd)
 *     需要旧密码验证，无需邮件验证
 *
 *   MODE_FORGOT（忘记密码）：
 *     未登录用户从登录页入口进入
 *     调用 BmobUser.resetPasswordByEmail(email) 发送重置链接
 *     用户点击邮件链接后密码重置完成，无需旧密码
 *
 * 性能优化：
 *   - networkIO 线程执行所有网络操作，不阻塞主线程
 *   - CountDownLatch 将 Bmob 异步回调同步化，超时 15s 自动中止
 *   - 网络检查提前拦截，无网络不发起任何 Bmob 请求
 *   - postMain 统一处理 callback null 判断，避免重复判断
 *   - 单例，全局复用
 */
public class ChangePasswordRepository {

    private static final String TAG = "ChangePasswordRepository";

    /** Bmob 接口超时时间（秒） */
    private static final int TIMEOUT_SECONDS = 15;

    private static volatile ChangePasswordRepository instance;

    private final Context context;
    private final AppExecutors executors;

    // ==================== 单例 ====================

    private ChangePasswordRepository(Context context) {
        this.context = context.getApplicationContext();
        this.executors = AppExecutors.get();
    }

    public static ChangePasswordRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (ChangePasswordRepository.class) {
                if (instance == null) {
                    instance = new ChangePasswordRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    // ==================== 查询操作 ====================

    /**
     * 获取掩码邮箱，用于界面提示（例如：abc***@gmail.com）
     * 从本地 Bmob Session 读取，无需网络
     */
    public String getMaskedEmail() {
        try {
            BmobUser currentUser = BmobUser.getCurrentUser();
            if (currentUser == null) return "";

            String email = currentUser.getEmail();
            if (email == null || !email.contains("@")) return "";

            int atIndex = email.indexOf('@');
            int keepChars = atIndex >= 3 ? 3 : 1;
            return email.substring(0, keepChars) + "***" + email.substring(atIndex);

        } catch (Exception e) {
            Log.e(TAG, "getMaskedEmail 异常: " + e.getMessage(), e);
            return "";
        }
    }

    // ==================== MODE_FORGOT：发送重置密码邮件 ====================

    /**
     * 发送重置密码邮件（忘记密码模式）
     *
     * 调用 BmobUser.resetPasswordByEmail，Bmob 向注册邮箱发送重置链接
     * 用户点击链接后在 Bmob 页面设置新密码，无需在 App 内输入旧密码
     *
     * @param callback 主线程回调
     */
    public void sendResetPasswordEmail(ApiResponse.Callback<Void> callback) {
        executors.networkIO().execute(() -> {
            // 1. 网络检查
            if (!isNetworkAvailable()) {
                Log.w(TAG, "sendResetPasswordEmail - 网络不可用");
                postMain(callback, ApiResponse.error("网络异常，请检查网络连接后重试"));
                return;
            }

            // 2. 获取当前用户邮箱
            String email;
            try {
                BmobUser currentUser = BmobUser.getCurrentUser();
                if (currentUser == null) {
                    Log.w(TAG, "sendResetPasswordEmail - 用户未登录");
                    postMain(callback, ApiResponse.error("用户未登录，请重新登录"));
                    return;
                }
                email = currentUser.getEmail();
                if (email == null || email.isEmpty()) {
                    Log.w(TAG, "sendResetPasswordEmail - 用户邮箱为空");
                    postMain(callback, ApiResponse.error("获取用户邮箱失败，请重新登录"));
                    return;
                }
            } catch (Exception e) {
                Log.e(TAG, "sendResetPasswordEmail - 获取用户信息异常: " + e.getMessage(), e);
                postMain(callback, ApiResponse.error("获取用户信息失败"));
                return;
            }

            Log.d(TAG, "sendResetPasswordEmail - 向 " + getMaskedEmail() + " 发送重置密码邮件");

            // 3. 调用 Bmob 发送重置密码邮件
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean success = new AtomicBoolean(false);
            AtomicReference<String> errorMsg = new AtomicReference<>("");

            BmobUser.resetPasswordByEmail(email, new UpdateListener() {
                @Override
                public void done(BmobException e) {
                    if (e == null) {
                        success.set(true);
                        Log.d(TAG, "sendResetPasswordEmail - 发送成功");
                    } else {
                        String msg = parseSendEmailError(e.getErrorCode(), e.getMessage());
                        errorMsg.set(msg);
                        Log.e(TAG, "sendResetPasswordEmail - 失败 code=" + e.getErrorCode()
                                + " msg=" + e.getMessage());
                    }
                    latch.countDown();
                }
            });

            // 4. 等待回调
            if (!awaitLatch(latch, callback)) return;

            // 5. 回调结果
            if (success.get()) {
                postMain(callback, ApiResponse.success(null, "重置密码邮件已发送"));
            } else {
                postMain(callback, ApiResponse.error(errorMsg.get()));
            }
        });
    }

    // ==================== MODE_CHANGE：修改密码 ====================

    /**
     * 修改密码（修改密码模式）
     *
     * 调用 BmobUser.updateCurrentUserPassword，需要旧密码验证
     * 修改成功后 Bmob Session 失效，App 需要强制跳转登录页
     *
     * @param oldPassword 旧密码
     * @param newPassword 新密码（ViewModel 已完成格式校验）
     * @param callback    主线程回调
     */
    public void changePassword(String oldPassword, String newPassword,
                               ApiResponse.Callback<Void> callback) {
        executors.networkIO().execute(() -> {
            // 1. 网络检查
            if (!isNetworkAvailable()) {
                Log.w(TAG, "changePassword - 网络不可用");
                postMain(callback, ApiResponse.error("网络异常，请检查网络连接后重试"));
                return;
            }

            // 2. 检查登录状态
            try {
                if (BmobUser.getCurrentUser() == null) {
                    Log.w(TAG, "changePassword - 用户未登录");
                    postMain(callback, ApiResponse.error("用户未登录，请重新登录"));
                    return;
                }
            } catch (Exception e) {
                Log.e(TAG, "changePassword - 检查登录状态异常: " + e.getMessage(), e);
                postMain(callback, ApiResponse.error("获取用户信息失败"));
                return;
            }

            Log.d(TAG, "changePassword - 调用 Bmob updateCurrentUserPassword");

            // 3. 调用 Bmob 修改密码
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean success = new AtomicBoolean(false);
            AtomicReference<String> errorMsg = new AtomicReference<>("");

            BmobUser.updateCurrentUserPassword(oldPassword, newPassword, new UpdateListener() {
                @Override
                public void done(BmobException e) {
                    if (e == null) {
                        success.set(true);
                        Log.d(TAG, "changePassword - Bmob 密码修改成功");
                    } else {
                        String msg = parseChangePasswordError(e.getErrorCode(), e.getMessage());
                        errorMsg.set(msg);
                        Log.e(TAG, "changePassword - 失败 code=" + e.getErrorCode()
                                + " msg=" + e.getMessage());
                    }
                    latch.countDown();
                }
            });

            // 4. 等待回调
            if (!awaitLatch(latch, callback)) return;

            // 5. 回调结果
            if (success.get()) {
                postMain(callback, ApiResponse.success(null, "密码修改成功"));
            } else {
                postMain(callback, ApiResponse.error(errorMsg.get()));
            }
        });
    }

    // ==================== 内部工具方法 ====================

    /**
     * 等待 CountDownLatch，超时或中断时向 callback 发送错误
     *
     * @return true = 正常完成，false = 超时或中断（已向 callback 发送错误，调用方直接 return）
     */
    private boolean awaitLatch(CountDownLatch latch, ApiResponse.Callback<?> callback) {
        try {
            boolean finished = latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                Log.e(TAG, "awaitLatch - 请求超时");
                postMain(callback, ApiResponse.error("请求超时，请检查网络后重试"));
                return false;
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "awaitLatch - 线程被中断: " + e.getMessage());
            postMain(callback, ApiResponse.error("操作被中断，请重试"));
            return false;
        }
    }

    /**
     * 检查网络是否可用
     */
    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        } catch (Exception e) {
            Log.e(TAG, "isNetworkAvailable 异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 将结果 post 回主线程，统一处理 callback null 判断
     */
    @SuppressWarnings("unchecked")
    private <T> void postMain(ApiResponse.Callback<T> callback, ApiResponse<T> response) {
        if (callback == null) return;
        executors.mainThread().execute(() -> callback.onComplete(response));
    }

    /**
     * 解析发送验证邮件的 Bmob 错误码
     */
    private String parseSendEmailError(int errorCode, String defaultMsg) {
        switch (errorCode) {
            case 125: return "邮箱格式不正确";
            case 205: return "该邮箱未注册";
            case 219: return "发送过于频繁，请稍后再试";
            case -1009:
            case -1004: return "网络异常，请检查网络连接后重试";
            default:   return defaultMsg != null ? defaultMsg : "发送失败，请稍后重试";
        }
    }

    /**
     * 解析修改密码的 Bmob 错误码
     */
    private String parseChangePasswordError(int errorCode, String defaultMsg) {
        switch (errorCode) {
            case 101: return "旧密码不正确，请重新输入";
            case 209: return "登录已过期，请重新登录后再修改";
            case -1009:
            case -1004: return "网络异常，请检查网络连接后重试";
            default:   return defaultMsg != null ? defaultMsg : "修改失败，请稍后重试";
        }
    }
}