package com.example.my_project1.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.my_project1.R;

import java.util.Locale;
import java.util.Random;

/**
 * 存钱计划删除专用弹窗
 * 支持两步操作：
 * 1. 选择删除范围（仅计划或包含账单）
 * 2. 输入随机验证码二次确认
 */
public class SavingPlanDeleteDialog {

    private final Context context;
    private final OnDeleteConfirmListener listener;
    private final String planName;
    
    public interface OnDeleteConfirmListener {
        void onDelete(boolean deleteBills);
    }

    public SavingPlanDeleteDialog(@NonNull Context context, String planName, OnDeleteConfirmListener listener) {
        this.context = context;
        this.planName = planName;
        this.listener = listener;
    }

    public void show() {
        showChoiceDialog();
    }

    private void showChoiceDialog() {
        Dialog dialog = new Dialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_saving_delete_choice, null);
        dialog.setContentView(view);

        TextView tvMessage = view.findViewById(R.id.tv_message);
        TextView btnDeleteBoth = view.findViewById(R.id.btn_delete_both);
        TextView btnDeletePlanOnly = view.findViewById(R.id.btn_delete_plan_only);
        TextView btnCancel = view.findViewById(R.id.btn_cancel);

        tvMessage.setText(String.format("你正在执行计划「%s」的删除操作，请选择是否需要保留与此计划相关联的账单记录", planName));

        btnDeleteBoth.setOnClickListener(v -> {
            dialog.dismiss();
            showVerifyDialog(true);
        });

        btnDeletePlanOnly.setOnClickListener(v -> {
            dialog.dismiss();
            showVerifyDialog(false);
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        applyWindowStyle(dialog);
        dialog.show();
    }

    private void showVerifyDialog(boolean deleteBills) {
        Dialog dialog = new Dialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_saving_delete_verify, null);
        dialog.setContentView(view);

        TextView tvMessage = view.findViewById(R.id.tv_message);
        EditText etVerifyCode = view.findViewById(R.id.et_verify_code);
        TextView btnExecute = view.findViewById(R.id.btn_execute);
        TextView btnCancel = view.findViewById(R.id.btn_cancel);

        // 生成随机4位确认码
        String verifyCode = String.format(Locale.US, "%04d", new Random().nextInt(10000));
        tvMessage.setText(String.format("请输入 【%s】 来执行此次操作", verifyCode));

        btnExecute.setOnClickListener(v -> {
            String input = etVerifyCode.getText().toString().trim();
            if (TextUtils.equals(input, verifyCode)) {
                if (listener != null) listener.onDelete(deleteBills);
                dialog.dismiss();
            } else {
                Toast.makeText(context, "确认码输入错误", Toast.LENGTH_SHORT).show();
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        applyWindowStyle(dialog);
        dialog.show();
    }

    private void applyWindowStyle(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = window.getAttributes();
            // 确保宽度为屏幕宽度的 85%
            params.width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.85);
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.CENTER;
            window.setAttributes(params);
        }
    }
}
