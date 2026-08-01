package com.example.my_project1;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;
import com.example.my_project1.data.repository.account.AccountRepository;
import com.example.my_project1.ui.activity.LoginActivity;
import com.example.my_project1.utils.AutoLoginManager;
import com.example.my_project1.utils.DataPreloader;
import com.example.my_project1.utils.SecureStorage;

import org.jetbrains.annotations.Nullable;

import cn.bmob.v3.BmobUser;

/**
 * 启动页（SplashActivity）- 旗舰级秒开版
 * -------------------------------------------------------
 * 🚀 优化内容:
 * 1. 零等待机制：不再等待预加载完成，动画播完即进。
 * 2. 预加载转后台：DataPreloader 在后台默默工作。
 * 3. 动画与逻辑并行：在播放 Lottie 的 1 秒内完成自动登录检查。
 */
public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";

    private LottieAnimationView lottieView;
    private TextView tvWelcome;

    private AccountRepository accountRy;
    private DataPreloader dataPreloader;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        makeStatusBarTransparent();
        setContentView(R.layout.activity_splash);

        accountRy = new AccountRepository(getApplicationContext());
        dataPreloader = DataPreloader.getInstance(this);

        lottieView = findViewById(R.id.lottieView);
        tvWelcome = findViewById(R.id.tvWelcome);

        // 🚀 后台预加载，不阻塞进入主页
        dataPreloader.startPreload();

        // 1秒后尝试进入下一步（给用户看一眼动画的时间）
        new Handler().postDelayed(this::checkAndProceed, 1000);

        // 文字动画
        new Handler().postDelayed(this::showTextAnimation, 500);
    }

    private void makeStatusBarTransparent() {
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(Color.TRANSPARENT);
        }
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        window.getDecorView().setSystemUiVisibility(flags);
    }

    private void showTextAnimation() {
        tvWelcome.setAlpha(0f);
        tvWelcome.setTranslationY(50f);
        PropertyValuesHolder alpha = PropertyValuesHolder.ofFloat("alpha", 0f, 1f);
        PropertyValuesHolder translateY = PropertyValuesHolder.ofFloat("translationY", 50f, 0f);
        ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(tvWelcome, alpha, translateY);
        animator.setDuration(1000);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.start();
    }

    private void checkAndProceed() {
        Log.d(TAG, "🚀 动画预设时间到，开始自动登录检查");
        performAutoLogin();
    }

    private void performAutoLogin() {
        AutoLoginManager.checkAutoLogin(this, new AutoLoginManager.AutoLoginCallback() {
            @Override
            public void onLoginSuccess(BmobUser user) {
                Log.d(TAG, "✅ 自动登录成功");
                String userId = user.getObjectId();
                accountRy.initDefaultAccountGroups(userId);
                
                // 🎯 立即进入主页
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
                finish();
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }

            @Override
            public void onLoginFailed(String reason) {
                Log.e(TAG, "❌ 自动登录失败: " + reason);
                SecureStorage.clearSession(SplashActivity.this);
                startActivity(new Intent(SplashActivity.this, LoginActivity.class));
                finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
