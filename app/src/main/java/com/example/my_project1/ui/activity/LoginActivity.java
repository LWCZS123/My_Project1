package com.example.my_project1.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.my_project1.MainActivity;
import com.example.my_project1.R;
import com.example.my_project1.ui.viewmodel.user.ChangePasswordViewModel;
import com.example.my_project1.utils.AppInitializer;
import com.example.my_project1.utils.SecureStorage;
import com.example.my_project1.work.CategoryDownloadWorker;

import cn.bmob.v3.BmobUser;
import cn.bmob.v3.exception.BmobException;
import cn.bmob.v3.listener.LogInListener;

/**
 * 登录界面
 * 功能：
 * 1. 输入邮箱+密码登录
 * 2. 验证邮箱格式与合法性
 * 3. 登录成功后安全加密保存凭证
 * 4. 异常安全处理
 */
public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tv_register,tv_forgot;
    private ProgressBar progressBar;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);

        // 设置状态栏图标为深色（因为背景是浅色 #F0F4FF）
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(true);



        setContentView(R.layout.activity_login);


        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        tv_register = findViewById(R.id.tv_register);
        tv_forgot = findViewById(R.id.tv_forgot);
        progressBar = findViewById(R.id.progress_bar);

        btnLogin.setOnClickListener(view -> loginUser());
        tv_register.setOnClickListener(v->registerActivity());
        tv_forgot.setOnClickListener(v->changePasswordActivity());
    }




    private void changePasswordActivity() {
        Intent intent = new Intent(this, ChangePasswordActivity.class);
        intent.putExtra(ChangePasswordActivity.EXTRA_MODE, ChangePasswordViewModel.MODE_FORGOT);
        startActivity(intent);
    }

    private void registerActivity() {
        startActivity(new Intent(this,RegisterActivity.class));
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        finish();
    }

    private void loginUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty()) {
            etEmail.setError("请输入邮箱");
            etEmail.requestFocus();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("邮箱格式错误");
            etEmail.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            etPassword.setError("请输入密码");
            etPassword.requestFocus();
            return;
        }
        //显示加载条，并隐藏登录按钮
        progressBar.setVisibility(View.VISIBLE);
        btnLogin.setEnabled(false);

        BmobUser.loginByAccount(email, password, new LogInListener<BmobUser>() {
            @Override
            public void done(BmobUser user, BmobException e) {
                //隐藏加载条，恢复登录按钮
                progressBar.setVisibility(View.GONE);
                btnLogin.setEnabled(true);
                if (e == null) {
                    if (user.getEmailVerified() != null && user.getEmailVerified()) {
                        Toast.makeText(LoginActivity.this, "登录成功！", Toast.LENGTH_SHORT).show();
                        //保存加密Session
                        SecureStorage.saveSession(LoginActivity.this, email, user.getSessionToken());


                        String userId = user.getObjectId();
                        // 初始化系统分类
                        AppInitializer.initSystemCategories(getApplicationContext(), userId, success -> {
                            if (success) {
                                // ✅ 下载云端已有分类
                                CategoryDownloadWorker.enqueue(getApplicationContext(), userId);
                            }
                        });
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();

                    } else {
                        Toast.makeText(LoginActivity.this, "请先到邮箱完成验证后再登录！", Toast.LENGTH_LONG).show();
                        //登出
                        BmobUser.logOut();
                    }
                } else {
                    Toast.makeText(LoginActivity.this, "登录失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}
