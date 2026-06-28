package com.example.my_project1.ui.viewmodel.user;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.my_project1.data.model.common.ApiResponse;
import com.example.my_project1.data.repository.user.ChangePasswordRepository;

import io.reactivex.annotations.NonNull;

/**
 * ChangePasswordViewModel - 修改/重置密码 ViewModel
 * -------------------------------------------------------
 * 支持两种模式，由 Activity 通过 setMode() 在初始化时设置：
 *
 *   MODE_CHANGE（修改密码）：
 *     - 需要旧密码 + 新密码 + 确认新密码
 *     - 点击确认直接调用 changePassword()，无需邮件验证
 *     - 适用于已登录用户在设置中主动修改密码
 *
 *   MODE_FORGOT（忘记密码）：
 *     - 只需新密码 + 确认新密码
 *     - 先发送重置邮件，用户点击链接后再点确认
 *     - 适用于未登录用户从登录页「忘记密码」入口进入
 *
 * 性能优化：
 *   - 本地校验提前拦截不合法输入，不发起网络请求
 *   - 两种模式共用同一套 LiveData，Activity 只需 observe 一次
 *   - ViewModel 跟随 Activity 生命周期，自动清理
 */
public class ChangePasswordViewModel extends AndroidViewModel {

    private static final String TAG = "ChangePasswordViewModel";

    /** 新密码最短长度 */
    private static final int MIN_PASSWORD_LENGTH = 6;

    // ==================== 模式常量 ====================

    /** 修改密码模式：已登录用户，需要旧密码，无需邮件验证 */
    public static final int MODE_CHANGE = 0;

    /** 忘记密码模式：未登录用户，无需旧密码，需要邮件验证 */
    public static final int MODE_FORGOT = 1;

    // ==================== 字段 ====================

    private final ChangePasswordRepository repository;

    /** 当前模式，由 Activity 初始化时通过 setMode() 设置 */
    private int mode = MODE_CHANGE;

    // ==================== LiveData ====================

    // 发送重置密码邮件状态（仅 MODE_FORGOT 使用）
    private final MutableLiveData<ApiResponse<Void>> _sendResetEmailState =
            new MutableLiveData<>(ApiResponse.idle());
    public final LiveData<ApiResponse<Void>> sendResetEmailState = _sendResetEmailState;

    // 修改/重置密码状态（两种模式共用）
    private final MutableLiveData<ApiResponse<Void>> _changePasswordState =
            new MutableLiveData<>(ApiResponse.idle());
    public final LiveData<ApiResponse<Void>> changePasswordState = _changePasswordState;

    // ==================== 构造函数 ====================

    public ChangePasswordViewModel(@NonNull Application application) {
        super(application);
        repository = ChangePasswordRepository.getInstance(application);
    }

    // ==================== 模式设置 ====================

    /**
     * 设置当前模式，必须在 Activity 初始化时调用
     *
     * @param mode MODE_CHANGE 或 MODE_FORGOT
     */
    public void setMode(int mode) {
        this.mode = mode;
        Log.d(TAG, "setMode - 当前模式: " + (mode == MODE_FORGOT ? "忘记密码" : "修改密码"));
    }

    public int getMode() {
        return mode;
    }

    // ==================== 查询操作 ====================

    /**
     * 获取掩码邮箱，用于界面提示
     * 读取本地 Bmob Session，无需网络
     */
    public String getMaskedEmail() {
        return repository.getMaskedEmail();
    }

    // ==================== MODE_FORGOT：发送重置邮件 ====================

    /**
     * 发送重置密码邮件
     * 仅在 MODE_FORGOT 模式下调用
     */
    public void sendResetPasswordEmail() {
        if (mode != MODE_FORGOT) {
            Log.w(TAG, "sendResetPasswordEmail - 非 MODE_FORGOT 模式，忽略调用");
            return;
        }

        _sendResetEmailState.setValue(ApiResponse.loading("正在发送重置邮件..."));
        Log.d(TAG, "sendResetPasswordEmail - 触发发送");

        repository.sendResetPasswordEmail(response -> {
            Log.d(TAG, "sendResetPasswordEmail - 结果: " + response.status);
            _sendResetEmailState.postValue(response);
        });
    }

    // ==================== 确认修改/重置密码 ====================

    /**
     * 确认操作入口，根据当前模式路由到不同处理逻辑
     *
     * MODE_CHANGE：需要 oldPassword + newPassword + confirmPassword
     * MODE_FORGOT：只需要 newPassword + confirmPassword（oldPassword 传 null 即可）
     *
     * @param oldPassword    旧密码（MODE_CHANGE 必填，MODE_FORGOT 传 null）
     * @param newPassword    新密码
     * @param confirmPassword 确认新密码
     */
    public void confirm(String oldPassword, String newPassword, String confirmPassword) {
        if (mode == MODE_FORGOT) {
            confirmForgot(newPassword, confirmPassword);
        } else {
            confirmChange(oldPassword, newPassword, confirmPassword);
        }
    }

    /**
     * MODE_CHANGE 确认修改密码
     * 本地校验旧密码 + 新密码格式 + 一致性，通过后发起网络请求
     */
    private void confirmChange(String oldPassword, String newPassword, String confirmPassword) {
        String error = validateChangePasswords(oldPassword, newPassword, confirmPassword);
        if (error != null) {
            Log.w(TAG, "confirmChange - 本地校验失败: " + error);
            _changePasswordState.setValue(ApiResponse.error(error));
            return;
        }

        _changePasswordState.setValue(ApiResponse.loading("正在修改密码..."));
        Log.d(TAG, "confirmChange - 本地校验通过，发起请求");

        repository.changePassword(oldPassword, newPassword, response -> {
            Log.d(TAG, "confirmChange - 结果: " + response.status);
            _changePasswordState.postValue(response);
        });
    }

    /**
     * MODE_FORGOT 确认重置密码
     * 本地只校验新密码格式 + 一致性（无需旧密码）
     * 注意：忘记密码模式下，Bmob 通过邮件链接完成重置，
     *       App 端点击「确认」只是告知用户流程结束，实际密码重置已在 Bmob 页面完成
     *       此处直接视为成功，无需再发网络请求
     */
    private void confirmForgot(String newPassword, String confirmPassword) {
        String error = validateForgotPasswords(newPassword, confirmPassword);
        if (error != null) {
            Log.w(TAG, "confirmForgot - 本地校验失败: " + error);
            _changePasswordState.setValue(ApiResponse.error(error));
            return;
        }

        // 忘记密码模式：用户已在邮箱完成重置，App 端直接成功
        Log.d(TAG, "confirmForgot - 校验通过，流程完成");
        _changePasswordState.setValue(ApiResponse.success(null, "密码重置流程已完成"));
    }

    // ==================== 本地校验 ====================

    /**
     * MODE_CHANGE 校验：旧密码 + 新密码格式 + 一致性
     *
     * @return null = 校验通过，否则返回错误信息
     */
    private String validateChangePasswords(String oldPassword, String newPassword, String confirmPassword) {
        if (oldPassword == null || oldPassword.trim().isEmpty()) {
            return "请输入旧密码";
        }
        return validateNewPasswords(newPassword, confirmPassword, oldPassword);
    }

    /**
     * MODE_FORGOT 校验：新密码格式 + 一致性（无旧密码）
     *
     * @return null = 校验通过，否则返回错误信息
     */
    private String validateForgotPasswords(String newPassword, String confirmPassword) {
        return validateNewPasswords(newPassword, confirmPassword, null);
    }

    /**
     * 通用新密码校验逻辑
     *
     * @param oldPassword 旧密码（MODE_FORGOT 传 null，跳过新旧相同检查）
     * @return null = 校验通过
     */
    private String validateNewPasswords(String newPassword, String confirmPassword, String oldPassword) {
        if (newPassword == null || newPassword.trim().isEmpty()) {
            return "请输入新密码";
        }
        if (newPassword.length() < MIN_PASSWORD_LENGTH) {
            return "新密码至少 " + MIN_PASSWORD_LENGTH + " 位";
        }
        if (oldPassword != null && newPassword.equals(oldPassword)) {
            return "新密码不能与旧密码相同";
        }
        if (confirmPassword == null || confirmPassword.trim().isEmpty()) {
            return "请确认新密码";
        }
        if (!confirmPassword.equals(newPassword)) {
            return "两次密码输入不一致";
        }
        return null;
    }

    // ==================== 状态重置 ====================

    /**
     * 重置发送邮件状态
     * Activity 消费 SUCCESS/ERROR 后调用，避免重复响应
     */
    public void resetSendResetEmailState() {
        _sendResetEmailState.setValue(ApiResponse.idle());
    }

    /**
     * 重置修改密码状态
     * Activity 消费 ERROR 后调用，避免重复弹 Toast
     */
    public void resetChangePasswordState() {
        _changePasswordState.setValue(ApiResponse.idle());
    }

    // ==================== 生命周期 ====================

    @Override
    protected void onCleared() {
        super.onCleared();
        Log.d(TAG, "onCleared - ViewModel 已销毁");
    }
}