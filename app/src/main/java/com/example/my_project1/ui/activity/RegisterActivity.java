package com.example.my_project1.ui.activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.my_project1.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import cn.bmob.v3.BmobUser;
import cn.bmob.v3.exception.BmobException;
import cn.bmob.v3.listener.SaveListener;

public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";
    private EditText etEmail, etPassword, etConfirmPassword;
    private Button btnRegister;
    private ProgressBar progressBar;
    private TextView tv_login;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);

        // 设置状态栏图标为深色（因为背景是浅色 #F0F4FF）
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(true);

        setContentView(R.layout.activity_register);

        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);
        btnRegister = findViewById(R.id.btn_register);
        progressBar = findViewById(R.id.progress_bar);
        tv_login = findViewById(R.id.tv_login);

        // 初始化 Bmob SDK
//        Bmob.initialize(this, "384a584a91aede6c31ef844de372d0b4"); // 替换成你的 AppID
//        Log.d(TAG, "Bmob 初始化完成");

        btnRegister.setOnClickListener(v -> registerUser());
        tv_login.setOnClickListener(v -> loginActivity());
    }




    private void loginActivity() {
        startActivity(new Intent(this,LoginActivity.class));
        overridePendingTransition(R.anim.slide_in_left2, R.anim.slide_out_right);
        finish();
    }


    /**
     * 注册用户逻辑
     */
    private void registerUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        // 输入校验
        if (email.isEmpty()) {
            etEmail.setError("请输入邮箱地址");
            etEmail.requestFocus();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("邮箱格式不正确");
            etEmail.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            etPassword.setError("请输入密码");
            etPassword.requestFocus();
            return;
        }
        if (password.length() < 6) {
            etPassword.setError("密码长度至少6位");
            etPassword.requestFocus();
            return;
        }
        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("两次输入的密码不一致");
            etConfirmPassword.requestFocus();
            return;
        }
        //显示加载条，并隐藏注册按钮
        progressBar.setVisibility(View.VISIBLE);
        btnRegister.setEnabled(false);

        // 创建用户对象
        BmobUser user = new BmobUser();
        user.setUsername(email);
        user.setEmail(email);
        user.setPassword(password);

        Log.d(TAG, "尝试注册用户：" + email);

        // 注册
        user.signUp(new SaveListener<BmobUser>() {
            @Override
            public void done(BmobUser bmobUser, BmobException e) {
                if (e == null) {
                    //隐藏加载条，恢复注册按钮
                    progressBar.setVisibility(View.GONE);
                    btnRegister.setEnabled(true);
                    // 注册成功，只显示提示
                    Log.d(TAG, "注册成功，邮箱验证邮件已自动发送");
                    showRegisterSuccessDialog();
                    //Toast.makeText(RegisterActivity.this, "注册成功，请到邮箱点击验证链接后再登录！", Toast.LENGTH_LONG).show();
                    //finish();
                } else {
                    Log.e(TAG, "注册失败：" + e.getMessage() + " (错误码: " + e.getErrorCode() + ")");
                    if (e.getErrorCode() == 202) {
                        Toast.makeText(RegisterActivity.this, "该邮箱已被注册", Toast.LENGTH_SHORT).show();
                    } else if (e.getErrorCode() == 9015) {
                        Toast.makeText(RegisterActivity.this, "网络不可用，请检查网络连接", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(RegisterActivity.this, "注册失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });


    }
    private void showRegisterSuccessDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle("注册成功")
                .setMessage("注册成功，请到邮箱点击验证链接后再登录！")
                .setPositiveButton("确定", (dialog, which) -> {
                    dialog.dismiss();
                    //跳转到登录页面
                    startActivity(new Intent(this, LoginActivity.class));
                    // finish();
                });

        AlertDialog alertDialog = builder.create();

        // 设置圆角背景
        if (alertDialog.getWindow() != null) {
            alertDialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_rounded1);
        }

        alertDialog.show();
    }
}
