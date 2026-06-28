package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;

import androidx.fragment.app.DialogFragment;

import com.example.my_project1.databinding.FragmentVerificationCodeDialogBinding;
import com.example.my_project1.utils.SnackbarUtils;

import java.util.Random;

import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;

/**
 * VerificationCodeDialog
 * ----------------------------------------------------------------
 * 功能：验证码输入对话框
 *   - 生成随机4位验证码
 *   - 4个独立的输入框，带底部横线
 *   - 自动跳转焦点
 *   - 用户输入正确后才能执行删除操作
 *
 * 🔑 优化：使用 ViewBinding 提升性能
 */
public class VerificationCodeDialog extends DialogFragment {

    private static final String TAG = "VerificationCodeDialog";

    // 🔑 ViewBinding
    private FragmentVerificationCodeDialogBinding binding;

    // 数据
    private String verificationCode;

    // 回调
    private OnVerificationListener listener;

    // 输入框数组
    private EditText[] editTexts;
    private View[] lines;

    // ==================== 静态工厂方法 ====================

    public static VerificationCodeDialog newInstance() {
        return new VerificationCodeDialog();
    }

    // ==================== 生命周期 ====================

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 生成随机4位验证码
        verificationCode = generateVerificationCode();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // 🔑 使用 ViewBinding
        binding = FragmentVerificationCodeDialogBinding.inflate(inflater, container, false);

        // 初始化输入框数组
        editTexts = new EditText[]{
                binding.etCode1,
                binding.etCode2,
                binding.etCode3,
                binding.etCode4
        };

        lines = new View[]{
                binding.line1,
                binding.line2,
                binding.line3,
                binding.line4
        };

        setupUI();
        setupListeners();

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 🔑 清理 ViewBinding
        binding = null;
        editTexts = null;
        lines = null;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);

        // 设置对话框样式
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        }

        // 点击外部不可取消
        dialog.setCanceledOnTouchOutside(false);

        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();

        // 设置对话框宽度
        if (getDialog() != null && getDialog().getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
            getDialog().getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        // 🔑 自动弹出键盘，聚焦第一个输入框
        if (editTexts != null && editTexts[0] != null) {
            editTexts[0].requestFocus();
            editTexts[0].postDelayed(() -> {
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager)
                                requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(editTexts[0], android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            }, 200);
        }
    }

    // ==================== 初始化 ====================

    private void setupUI() {
        binding.tvTitle.setText("提示");
        binding.tvMessage.setText("确定不迁入任何账户吗？如果不选择账户则这些账单会被设置为无账户哦～");
        binding.tvVerificationCode.setText("输入确认码：" + verificationCode);

        // 初始隐藏进度条
        binding.progressBar.setVisibility(View.GONE);
    }

    private void setupListeners() {
        // 取消按钮
        binding.btnCancel.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCancel();
            }
            dismiss();
        });

        // 🔑 为每个输入框设置监听器
        for (int i = 0; i < editTexts.length; i++) {
            final int index = i;
            EditText currentEditText = editTexts[i];
            final View currentLine = lines[i];

            // 文本变化监听
            currentEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (s.length() == 1) {
                        // 🔑 输入一位数字后，自动跳转到下一个输入框
                        if (index < editTexts.length - 1) {
                            editTexts[index + 1].requestFocus();
                            // 激活下一个输入框的横线
                            lines[index + 1].setBackgroundColor(Color.parseColor("#2196F3"));
                        } else {
                            // 🔑 最后一个输入框填完后，自动验证
                            currentEditText.postDelayed(() -> {
                                String code = getInputCode();
                                if (code.length() == 4) {
                                    verifyCode(code);
                                }
                            }, 300);
                        }
                        // 当前输入框的横线保持激活状态
                        currentLine.setBackgroundColor(Color.parseColor("#2196F3"));
                    } else if (s.length() == 0) {
                        // 清空时，横线变灰
                        currentLine.setBackgroundColor(Color.parseColor("#CCCCCC"));
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });

            // 🔑 监听删除键，自动跳转到前一个输入框
            currentEditText.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (currentEditText.getText().toString().isEmpty() && index > 0) {
                        // 当前输入框为空，按删除键时跳转到前一个输入框
                        editTexts[index - 1].requestFocus();
                        editTexts[index - 1].setSelection(editTexts[index - 1].getText().length());
                        // 激活前一个输入框的横线
                        lines[index - 1].setBackgroundColor(Color.parseColor("#2196F3"));
                        // 当前输入框的横线变灰
                        currentLine.setBackgroundColor(Color.parseColor("#CCCCCC"));
                        return true;
                    }
                }
                return false;
            });

            // 🔑 焦点变化监听
            currentEditText.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    // 获得焦点时，横线变蓝
                    currentLine.setBackgroundColor(Color.parseColor("#2196F3"));
                } else {
                    // 失去焦点时，如果有内容则保持蓝色，否则变灰
                    if (currentEditText.getText().toString().isEmpty()) {
                        currentLine.setBackgroundColor(Color.parseColor("#CCCCCC"));
                    }
                }
            });
        }
    }

    // ==================== 业务逻辑 ====================

    /**
     * 生成随机4位验证码
     */
    private String generateVerificationCode() {
        Random random = new Random();
        int code = 1000 + random.nextInt(9000); // 生成1000-9999之间的随机数
        return String.valueOf(code);
    }

    /**
     * 获取输入的验证码
     */
    private String getInputCode() {
        StringBuilder code = new StringBuilder();
        for (EditText editText : editTexts) {
            code.append(editText.getText().toString());
        }
        return code.toString();
    }

    /**
     * 清空所有输入框
     */
    private void clearInputs() {
        for (int i = 0; i < editTexts.length; i++) {
            editTexts[i].setText("");
            lines[i].setBackgroundColor(Color.parseColor("#CCCCCC"));
        }
        // 聚焦到第一个输入框
        editTexts[0].requestFocus();
    }

    /**
     * 验证输入的验证码
     */
    private void verifyCode(String inputCode) {
        if (binding == null) {
            return;
        }

        if (inputCode.equals(verificationCode)) {
            // 验证成功
            binding.progressBar.setVisibility(View.VISIBLE);

            // 所有横线变绿，表示成功
            for (View line : lines) {
                line.setBackgroundColor(Color.parseColor("#4CAF50"));
            }

            // 延迟一小段时间，让用户看到进度条和绿色横线
            binding.getRoot().postDelayed(() -> {
                if (listener != null) {
                    listener.onVerified();
                }
                dismiss();
            }, 500);
        } else {
            // 验证失败 - 所有横线变红
            for (View line : lines) {
                line.setBackgroundColor(Color.parseColor("#F44336"));
            }

            // 震动效果（可选）
            binding.getRoot().animate()
                    .translationX(-10f)
                    .setDuration(50)
                    .withEndAction(() -> binding.getRoot().animate()
                            .translationX(10f)
                            .setDuration(50)
                            .withEndAction(() -> binding.getRoot().animate()
                                    .translationX(0f)
                                    .setDuration(50)
                                    .start())
                            .start())
                    .start();

            // 显示错误提示
            SnackbarUtils.showError(binding.getRoot(), "验证码错误，请重新输入");

            // 延迟后清空输入框并重置横线颜色
            binding.getRoot().postDelayed(() -> {
                clearInputs();
            }, 1000);
        }
    }

    // ==================== 回调接口 ====================

    public void setOnVerificationListener(OnVerificationListener listener) {
        this.listener = listener;
    }

    public interface OnVerificationListener {
        /**
         * 验证通过
         */
        void onVerified();

        /**
         * 取消验证
         */
        void onCancel();
    }
}