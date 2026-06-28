package com.example.my_project1.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.my_project1.R;
import com.example.my_project1.data.repository.user.UserProfileRepository;
import com.example.my_project1.databinding.ActivityChangePasswordBinding;
import com.example.my_project1.ui.viewmodel.user.ChangePasswordViewModel;

import io.reactivex.annotations.NonNull;

/**
 * ChangePasswordActivity - 修改密码 / 忘记密码 双模式界面
 * -------------------------------------------------------
 * 通过 Intent Extra「EXTRA_MODE」区分两种入口：
 *
 *   MODE_CHANGE（修改密码，默认）：
 *     从「个人中心 → 修改密码」进入
 *     显示旧密码输入框，隐藏发送邮件区域
 *     输入旧密码 + 新密码 + 确认新密码 → 直接确认修改
 *     Toolbar 标题：修改密码
 *
 *   MODE_FORGOT（忘记密码）：
 *     从「登录页 → 忘记密码」进入
 *     隐藏旧密码输入框，显示发送邮件区域
 *     发送邮件 → 去邮箱点击链接 → 回来点确认
 *     Toolbar 标题：忘记密码
 *
 * 使用方式：
 *   // 修改密码（默认，可不传 EXTRA_MODE）
 *   Intent intent = new Intent(this, ChangePasswordActivity.class);
 *   startActivity(intent);
 *
 *   // 忘记密码
 *   Intent intent = new Intent(this, ChangePasswordActivity.class);
 *   intent.putExtra(ChangePasswordActivity.EXTRA_MODE, ChangePasswordViewModel.MODE_FORGOT);
 *   startActivity(intent);
 */
public class ChangePasswordActivity extends AppCompatActivity {

    private static final String TAG = "ChangePasswordActivity";

    /** Intent Extra Key，值为 ChangePasswordViewModel.MODE_CHANGE 或 MODE_FORGOT */
    public static final String EXTRA_MODE = "extra_mode";

    /** 重发验证邮件倒计时总时长（毫秒） */
    private static final long COUNT_DOWN_TOTAL_MS    = 60_000L;
    /** 倒计时刷新间隔（毫秒） */
    private static final long COUNT_DOWN_INTERVAL_MS = 1_000L;

    // ViewBinding - onDestroy 中置空，避免内存泄漏
    private ActivityChangePasswordBinding binding;

    private ChangePasswordViewModel viewModel;

    private CountDownTimer countDownTimer;

    /** 当前模式，initMode() 中从 Intent 读取后缓存，避免重复读取 */
    private int currentMode;

    // ==================== 生命周期 ====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {


        super.onCreate(savedInstanceState);
        binding = ActivityChangePasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {

            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;

            v.setPadding(0, top, 0, 0);

            return insets;
        });

        // 设置状态栏图标为深色（因为背景是浅色 #F0F4FF）
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(true);


        initMode();
        initToolbar();
        initViewModel();
        initModeUi();
        initClickListeners();
        observeStates();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelCountDown();
        binding = null;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ==================== 初始化 ====================

    /**
     * 从 Intent 读取模式，缓存到 currentMode
     * 默认为 MODE_CHANGE，LoginActivity 传 MODE_FORGOT
     */
    private void initMode() {
        currentMode = getIntent().getIntExtra(EXTRA_MODE, ChangePasswordViewModel.MODE_CHANGE);
        Log.d(TAG, "initMode - 当前模式: "
                + (currentMode == ChangePasswordViewModel.MODE_FORGOT ? "忘记密码" : "修改密码"));
    }

    private void initToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            // 根据模式设置 Toolbar 标题
            if (currentMode == ChangePasswordViewModel.MODE_FORGOT) {
                getSupportActionBar().setTitle("忘记密码");
            } else {
                getSupportActionBar().setTitle("修改密码");
            }
        }
    }

    private void initViewModel() {
        viewModel = new ViewModelProvider(this).get(ChangePasswordViewModel.class);
        viewModel.setMode(currentMode);

        // 显示掩码邮箱（两种模式都显示）
        String maskedEmail = viewModel.getMaskedEmail();
        if (maskedEmail != null && !maskedEmail.isEmpty()) {
            binding.tvEmailHint.setText("验证邮件将发送至：" + maskedEmail);
            binding.tvEmailHint.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 根据模式显示/隐藏对应 UI 区域，并初始化确认按钮状态
     *
     * MODE_CHANGE：
     *   - 显示旧密码输入框
     *   - 隐藏发送邮件区域（cardEmailVerify）
     *   - 确认按钮默认启用（主题色）
     *
     * MODE_FORGOT：
     *   - 隐藏旧密码输入框（tilOldPassword）
     *   - 显示发送邮件区域
     *   - 确认按钮默认禁用（灰色），发送邮件成功后才启用
     */
    private void initModeUi() {
        if (currentMode == ChangePasswordViewModel.MODE_FORGOT) {
            // 忘记密码：隐藏旧密码框，显示邮件验证区域，确认按钮禁用
            binding.tilOldPassword.setVisibility(View.GONE);
            binding.cardEmailVerify.setVisibility(View.VISIBLE);
            setConfirmButtonEnabled(false);
        } else {
            // 修改密码：显示旧密码框，隐藏邮件验证区域，确认按钮直接启用
            binding.tilOldPassword.setVisibility(View.VISIBLE);
            binding.cardEmailVerify.setVisibility(View.GONE);
            setConfirmButtonEnabled(true);
        }
    }

    private void initClickListeners() {
        binding.btnSendEmail.setOnClickListener(v -> onSendEmailClick());
        binding.btnConfirm.setOnClickListener(v -> onConfirmClick());
    }

    // ==================== 状态监听 ====================

    private void observeStates() {

        // 发送重置邮件状态（仅 MODE_FORGOT 触发）
        viewModel.sendResetEmailState.observe(this, response -> {
            if (response == null) return;

            switch (response.status) {
                case LOADING:
                    // LOADING 由点击事件同步触发，此处只保证进度条可见
                    showLoading(true);
                    break;

                case SUCCESS:
                    showLoading(false);
                    onSendEmailSuccess();
                    viewModel.resetSendResetEmailState();
                    break;

                case ERROR:
                    showLoading(false);
                    binding.btnSendEmail.setEnabled(true);
                    showToast("发送失败：" + response.message);
                    viewModel.resetSendResetEmailState();
                    break;

                case IDLE:
                default:
                    showLoading(false);
                    break;
            }
        });

        // 确认修改/重置密码状态（两种模式共用）
        viewModel.changePasswordState.observe(this, response -> {
            if (response == null) return;

            switch (response.status) {
                case LOADING:
                    showLoading(true);
                    setAllInputEnabled(false);
                    break;

                case SUCCESS:
                    showLoading(false);
                    Log.d(TAG, "observeStates - 操作成功，模式: " + currentMode);
                    showSuccessDialog();
                    break;

                case ERROR:
                    showLoading(false);
                    setAllInputEnabled(true);
                    handleConfirmError(response.message);
                    viewModel.resetChangePasswordState();
                    break;

                case IDLE:
                default:
                    showLoading(false);
                    break;
            }
        });
    }

    // ==================== 点击事件 ====================

    /**
     * 点击「发送验证邮件」（仅 MODE_FORGOT）
     * 点击后立即同步更新 UI，消除卡顿感
     */
    private void onSendEmailClick() {
        if (!checkNewPasswordNotEmpty()) return;

        // 立即同步更新 UI，不等 LiveData 回调，消除视觉卡顿
        binding.btnSendEmail.setEnabled(false);
        showLoading(true);

        viewModel.sendResetPasswordEmail();
    }

    /**
     * 点击「确认修改密码」
     * ViewModel.confirm 内部根据模式路由，并完整校验
     */
    private void onConfirmClick() {
        // MODE_CHANGE 需要旧密码，MODE_FORGOT 旧密码传空字符串（ViewModel 会忽略）
        String oldPwd     = currentMode == ChangePasswordViewModel.MODE_CHANGE
                ? getInputText(binding.etOldPassword.getText())
                : "";
        String newPwd     = getInputText(binding.etNewPassword.getText());
        String confirmPwd = getInputText(binding.etConfirmPassword.getText());

        viewModel.confirm(oldPwd, newPwd, confirmPwd);
    }

    // ==================== 输入框辅助 ====================

    /**
     * MODE_FORGOT 发送邮件前检查：新密码和确认新密码不能为空
     * 给用户即时的输入框提示，格式校验由 ViewModel 负责
     */
    private boolean checkNewPasswordNotEmpty() {
        boolean ready = true;

        if (getInputText(binding.etNewPassword.getText()).isEmpty()) {
            binding.tilNewPassword.setError("请先输入新密码");
            ready = false;
        } else {
            binding.tilNewPassword.setError(null);
        }

        if (getInputText(binding.etConfirmPassword.getText()).isEmpty()) {
            binding.tilConfirmPassword.setError("请先确认新密码");
            ready = false;
        } else {
            binding.tilConfirmPassword.setError(null);
        }

        return ready;
    }

    /**
     * 将 ViewModel 返回的错误映射到对应输入框或 Toast
     */
    private void handleConfirmError(String message) {
        if (message == null) return;

        if (message.contains("旧密码")) {
            binding.tilOldPassword.setError(message);
            binding.etOldPassword.requestFocus();
        } else if (message.contains("新密码") || message.contains("两次") || message.contains("确认")) {
            binding.tilNewPassword.setError(message);
            binding.etNewPassword.requestFocus();
        } else {
            showToast(message);
        }
    }

    /**
     * 安全获取 EditText 文本，避免 null
     */
    private String getInputText(android.text.Editable editable) {
        return editable != null ? editable.toString().trim() : "";
    }

    // ==================== 发送成功处理 ====================

    /**
     * 发送重置邮件成功后的 UI 更新（仅 MODE_FORGOT）
     */
    private void onSendEmailSuccess() {
        binding.layoutEmailSentTip.setVisibility(View.VISIBLE);
        setConfirmButtonEnabled(true);
        startCountDown();
        showToast("重置邮件已发送，请查收邮箱并点击验证链接");
        Log.d(TAG, "onSendEmailSuccess - 倒计时已启动，确认按钮已解锁");
    }

    // ==================== 确认按钮颜色控制 ====================

    /**
     * 控制「确认」按钮的启用状态及颜色
     *   - disabled：灰色（gray_disabled），视觉上明显不可用
     *   - enabled ：主题色（accent_color），与其他主操作按钮一致
     */
    private void setConfirmButtonEnabled(boolean enabled) {
        binding.btnConfirm.setEnabled(enabled);
        int colorRes = enabled ? R.color.accent_color : R.color.gray_disabled;
        binding.btnConfirm.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(this, colorRes)
                )
        );
    }

    // ==================== 倒计时 ====================

    private void startCountDown() {
        cancelCountDown();
        binding.btnSendEmail.setEnabled(false);

        countDownTimer = new CountDownTimer(COUNT_DOWN_TOTAL_MS, COUNT_DOWN_INTERVAL_MS) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (binding != null) {
                    binding.btnSendEmail.setText(millisUntilFinished / 1000 + "s 后重发");
                }
            }

            @Override
            public void onFinish() {
                countDownTimer = null;
                if (binding != null) {
                    binding.btnSendEmail.setEnabled(true);
                    binding.btnSendEmail.setText("重新发送");
                }
            }
        }.start();
    }

    private void cancelCountDown() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }

    // ==================== 成功弹窗 ====================

    /**
     * 操作成功后弹窗
     *
     * MODE_CHANGE：密码已修改，Session 失效，强制跳转登录页并清除 back stack
     * MODE_FORGOT：重置流程完成，提示用户用新密码登录，跳转登录页并清除 back stack
     */
    private void showSuccessDialog() {
        String title   = currentMode == ChangePasswordViewModel.MODE_FORGOT ? "重置成功" : "修改成功";
        String message = currentMode == ChangePasswordViewModel.MODE_FORGOT
                ? "密码重置流程已完成，请用新密码登录"
                : "密码已修改，为确保安全请重新登录";

        AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("去登录", (dialog, which) -> {
                    // 清除本地登录态
                    UserProfileRepository.getInstance(this).logout(response ->
                            Log.d(TAG, "showSuccessDialog - logout 回调: " + response.status)
                    );
                    // 清除 back stack，强制跳转登录页，按返回键不会回到之前的页面
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .create();

        if (alertDialog.getWindow() != null) {
            alertDialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_rounded1);
        }

        alertDialog.show();
    }

    // ==================== UI 工具 ====================

    private void showLoading(boolean loading) {
        if (binding == null) return;
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    /**
     * 批量控制输入框和按钮的可用状态
     * LOADING 期间禁用所有操作，防止重复提交
     */
    private void setAllInputEnabled(boolean enabled) {
        if (binding == null) return;

        // 旧密码框仅 MODE_CHANGE 时可见，统一设置不影响隐藏状态
        binding.etOldPassword.setEnabled(enabled);
        binding.etNewPassword.setEnabled(enabled);
        binding.etConfirmPassword.setEnabled(enabled);

        // 发送按钮仅 MODE_FORGOT 时可见，倒计时期间不恢复
        binding.btnSendEmail.setEnabled(enabled && countDownTimer == null);

        // 确认按钮：MODE_CHANGE 恢复启用，MODE_FORGOT 需保持发送成功后的状态
        if (currentMode == ChangePasswordViewModel.MODE_CHANGE) {
            setConfirmButtonEnabled(enabled);
        } else {
            // MODE_FORGOT：只有发送过邮件（layoutEmailSentTip 可见）才能恢复启用
            boolean emailSent = binding.layoutEmailSentTip.getVisibility() == View.VISIBLE;
            setConfirmButtonEnabled(enabled && emailSent);
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}