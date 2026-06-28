package com.example.my_project1;

import android.animation.Animator;
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
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.airbnb.lottie.LottieAnimationView;
import com.example.my_project1.data.repository.account.AccountRepository;
import com.example.my_project1.ui.activity.LoginActivity;
import com.example.my_project1.ui.viewmodel.wish.WishViewModel;
import com.example.my_project1.utils.AppInitializer;
import com.example.my_project1.utils.AutoLoginManager;
import com.example.my_project1.utils.DataPreloader;
import com.example.my_project1.utils.SecureStorage;
import com.example.my_project1.work.CategoryDownloadWorker;

import org.jetbrains.annotations.Nullable;

import cn.bmob.v3.BmobUser;

/**
 * 启动页（SplashActivity）- 优化版
 * -------------------------------------------------------
 * 🚀 优化内容:
 * 1. 在播放动画的同时预加载数据
 * 2. 预加载当月账单数据到内存
 * 3. 预加载图片到Glide缓存
 * 4. 用户进入主页时数据已经准备就绪
 *
 * 功能：
 * 1. 播放Lottie启动动画
 * 2. 【新增】后台预加载账单数据和图片
 * 3. 动画播放完成后 -> 执行自动登录逻辑
 * 4. 若有有效Session -> 自动进入主页面
 * 5. 若无效或异常 -> 跳转登录页
 */
public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";

    private LottieAnimationView lottieView;
    private TextView tvWelcome;

    private AccountRepository accountRy;
    private DataPreloader dataPreloader;

    // 用于协调动画和预加载的完成时间
    private boolean isAnimationFinished = false;
    private boolean isPreloadFinished = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 设置沉浸式全屏
//        getWindow().setFlags(
//                WindowManager.LayoutParams.FLAG_FULLSCREEN,
//                WindowManager.LayoutParams.FLAG_FULLSCREEN
//        );

        makeStatusBarTransparent();
        setContentView(R.layout.activity_splash);

        accountRy = new AccountRepository(getApplicationContext());
        dataPreloader = DataPreloader.getInstance(this);

        lottieView = findViewById(R.id.lottieView);
        tvWelcome = findViewById(R.id.tvWelcome);

        // 🚀 关键优化：立即开始预加载数据
        startDataPreload();

        // 添加动画监听器
        lottieView.addAnimatorListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                // 动画开始时，延迟播放文字动画
                new Handler().postDelayed(() -> showTextAnimation(), 800);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                // 动画播放结束
                Log.d(TAG, "✅ 动画播放完成");
                isAnimationFinished = true;

                // 检查是否可以进入下一步
                checkAndProceed();
            }

            @Override public void onAnimationCancel(Animator animation) {}
            @Override public void onAnimationRepeat(Animator animation) {}
        });
    }

    private void makeStatusBarTransparent() {
        // 1. 设置内容延伸到状态栏
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        // 2. 核心：设置状态栏颜色为透明
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(Color.TRANSPARENT);
        }

        // 3. 这里的系统 UI 标记非常关键
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;


        window.getDecorView().setSystemUiVisibility(flags);
    }

    /**
     * 🚀 启动数据预加载
     */
    private void startDataPreload() {
        Log.d(TAG, "========== 🚀 开始数据预加载 ==========");

        // 观察预加载状态
        dataPreloader.getPreloadState().observe(this, state -> {
            switch (state) {
                case LOADING:
                    Log.d(TAG, "⏳ 数据加载中...");
                    break;

                case SUCCESS:
                    Log.d(TAG, "✅ 数据预加载成功，耗时: " +
                            dataPreloader.getPreloadDuration() + "ms");
                    isPreloadFinished = true;
                    checkAndProceed();
                    break;

                case FAILED:
                    Log.e(TAG, "❌ 数据预加载失败");
                    // 即使预加载失败也继续流程
                    isPreloadFinished = true;
                    checkAndProceed();
                    break;
            }
        });

        // 开始预加载
        dataPreloader.startPreload();
    }

    /**
     * 检查动画和预加载是否都完成，如果是则进入下一步
     */
    private void checkAndProceed() {
        if (isAnimationFinished && isPreloadFinished) {
            Log.d(TAG, "✅ 动画和预加载都已完成，准备进入主页");
            // 延迟800ms再执行自动登录，让用户看到欢迎文字
            new Handler().postDelayed(() -> performAutoLogin(), 800);
        } else {
            Log.d(TAG, "⏳ 等待完成 - 动画:" + isAnimationFinished + ", 预加载:" + isPreloadFinished);
        }
    }

    /**
     * 欢迎文字动画：淡入 + 上滑
     */
    private void showTextAnimation() {
        tvWelcome.setAlpha(0f);
        tvWelcome.setTranslationY(50f);

        PropertyValuesHolder alpha = PropertyValuesHolder.ofFloat("alpha", 0f, 1f);
        PropertyValuesHolder translateY = PropertyValuesHolder.ofFloat("translationY", 50f, 0f);

        ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(tvWelcome, alpha, translateY);
        animator.setDuration(1200);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.start();
    }

    /**
     * 自动登录逻辑：在动画和预加载都完成后调用
     */
    private void performAutoLogin() {
        AutoLoginManager.checkAutoLogin(this, new AutoLoginManager.AutoLoginCallback() {
            @Override
            public void onLoginSuccess(BmobUser user) {
                Log.d(TAG, "✅ 自动登录成功");
                Toast.makeText(SplashActivity.this, "欢迎回来", Toast.LENGTH_SHORT).show();

                String userId = user.getObjectId();
                accountRy.initDefaultAccountGroups(userId);


                WishViewModel viewModel =
                        new ViewModelProvider(SplashActivity.this).get(WishViewModel.class);
                viewModel.syncWishesFromCloud();
                viewModel.syncAllFromCloud();


                // 初始化系统分类
                AppInitializer.initSystemCategories(getApplicationContext(), userId, success -> {
                    if (success) {
                        // ✅ 下载云端已有分类
                        CategoryDownloadWorker.enqueue(getApplicationContext(), userId);
                    }
                });

                // 🎯 进入主页 - 此时数据已经预加载完成
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
                finish();
            }

            @Override
            public void onLoginFailed(String reason) {
                Log.e(TAG, "❌ 自动登录失败: " + reason);
                Toast.makeText(SplashActivity.this, reason, Toast.LENGTH_SHORT).show();
                SecureStorage.clearSession(SplashActivity.this);

                // 清除预加载的数据
                dataPreloader.clear();

                startActivity(new Intent(SplashActivity.this, LoginActivity.class));
                finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 如果启动页被销毁，清理预加载观察者
        dataPreloader.getPreloadState().removeObservers(this);
    }
}