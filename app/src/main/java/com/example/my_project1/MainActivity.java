package com.example.my_project1;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.my_project1.databinding.ActivityMainBinding;
import com.example.my_project1.ui.activity.AddBillActivity;
import com.example.my_project1.ui.fragment.AssetsFragment;
import com.example.my_project1.ui.fragment.CalendarFragment;
import com.example.my_project1.ui.fragment.HomeFragment;
import com.example.my_project1.ui.fragment.ProfileFragment;

/**
 * MainActivity - 手动管理优化版
 * ✅ 使用 show/hide 机制，避免 Fragment 重复创建
 * ✅ 正确处理配置变更，避免 Fragment 重叠
 * ✅ 性能优化：Fragment 只创建一次，切换时仅显示/隐藏
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private ActivityMainBinding binding;
    private FragmentManager fragmentManager;

    // 缓存所有 Fragment 实例
    private HomeFragment homeFragment;
    private CalendarFragment calendarFragment;
    private AssetsFragment assetsFragment;
    private ProfileFragment profileFragment;

    private Fragment currentFragment;

    // Fragment 标签常量
    private static final String TAG_HOME = "home";
    private static final String TAG_CALENDAR = "calendar";
    private static final String TAG_ASSETS = "assets";
    private static final String TAG_PROFILE = "profile";

    // 保存当前选中的标签
    private static final String KEY_CURRENT_TAG = "current_fragment_tag";
    private String currentFragmentTag = TAG_HOME;

    @Override
    protected void onCreate(Bundle savedInstanceState) {


        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());



//        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // 设置状态栏图标为深色（因为背景是浅色 #F0F4FF）
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(true);

        binding.getRoot().setOnApplyWindowInsetsListener((view, windowInsets) -> {
            // 获取系统底部手势栏高度
            int bottomSafeHeight = windowInsets.getSystemWindowInsetBottom();
            // 你想要整体向上偏移的固定距离
            int offsetUp = 32;

            // 导航整体上移：系统安全高度 + 自定义向上偏移量
            CoordinatorLayout.LayoutParams navParams = (CoordinatorLayout.LayoutParams) binding.bottomBarContainer.getLayoutParams();
            navParams.bottomMargin = bottomSafeHeight + offsetUp;
            binding.bottomBarContainer.setLayoutParams(navParams);

            // FAB同步相同偏移，保持对齐
            CoordinatorLayout.LayoutParams fabParams = (CoordinatorLayout.LayoutParams) binding.fab.getLayoutParams();
            fabParams.bottomMargin = bottomSafeHeight + offsetUp;
            binding.fab.setLayoutParams(fabParams);

            return windowInsets;
        });


        View content = findViewById(R.id.nav_host_fragment);




        fragmentManager = getSupportFragmentManager();
        // ========== 修复1：启用预加载 ==========
        preloadFragments();

        // 处理配置变更（如屏幕旋转）
        if (savedInstanceState != null) {
            // 恢复 Fragment 引用
            restoreFragments();
            // 恢复当前显示的标签
            currentFragmentTag = savedInstanceState.getString(KEY_CURRENT_TAG, TAG_HOME);
            currentFragment = fragmentManager.findFragmentByTag(currentFragmentTag);

            // 隐藏所有其他 Fragment，只显示当前的
            hideAllFragmentsExcept(currentFragmentTag);

            // 同步底部导航栏选中状态
            updateBottomNavigationSelection(currentFragmentTag);

            Log.d(TAG, "配置变更恢复: " + currentFragmentTag);
        } else {
            // 首次创建，显示首页
            Log.d(TAG, "首次创建，显示首页");
            showFragment(TAG_HOME);
        }

        // 设置底部导航监听
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            String tag = getFragmentTag(item.getItemId());
            showFragment(tag);
            return true;
        });

        // 设置 FAB 点击监听
        binding.fab.setOnClickListener(v ->{
            startActivity(new Intent(this, AddBillActivity.class));
            this.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });
    }

    private void preloadFragments() {
        FragmentTransaction transaction = fragmentManager.beginTransaction();

        // 预加载 HomeFragment
        if (homeFragment == null) {
            homeFragment = new HomeFragment();
            transaction.add(R.id.nav_host_fragment, homeFragment, TAG_HOME);
            Log.d(TAG, "预加载 HomeFragment");
        }

        // 预加载 CalendarFragment
        if (calendarFragment == null) {
            calendarFragment = new CalendarFragment();
            transaction.add(R.id.nav_host_fragment, calendarFragment, TAG_CALENDAR);
            transaction.hide(calendarFragment);
            Log.d(TAG, "预加载 CalendarFragment");
        }

        // 预加载 AssetsFragment
        if (assetsFragment == null) {
            assetsFragment = new AssetsFragment();
            transaction.add(R.id.nav_host_fragment, assetsFragment, TAG_ASSETS);
            transaction.hide(assetsFragment);
            Log.d(TAG, "预加载 AssetsFragment");
        }

        // 预加载 ProfileFragment
        if (profileFragment == null) {
            profileFragment = new ProfileFragment();
            transaction.add(R.id.nav_host_fragment, profileFragment, TAG_PROFILE);
            transaction.hide(profileFragment);
            Log.d(TAG, "预加载 ProfileFragment");
        }

        transaction.commitNowAllowingStateLoss();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // 保存当前显示的 Fragment 标签
        outState.putString(KEY_CURRENT_TAG, currentFragmentTag);
        Log.d(TAG, "保存状态: " + currentFragmentTag);
    }

    private void restoreFragments() {
        homeFragment = (HomeFragment) fragmentManager.findFragmentByTag(TAG_HOME);
        calendarFragment = (CalendarFragment) fragmentManager.findFragmentByTag(TAG_CALENDAR);
        assetsFragment = (AssetsFragment) fragmentManager.findFragmentByTag(TAG_ASSETS);
        profileFragment = (ProfileFragment) fragmentManager.findFragmentByTag(TAG_PROFILE);

        Log.d(TAG, String.format("恢复 Fragment - Home:%s, Calendar:%s, Assets:%s, Profile:%s",
                homeFragment != null, calendarFragment != null,
                assetsFragment != null, profileFragment != null));
    }

    /**
     * 获取 Fragment 对应的索引，用于判断动画方向
     */
    private int getFragmentIndex(String tag) {
        switch (tag) {
            case TAG_HOME: return 0;
            case TAG_CALENDAR: return 1;
            case TAG_ASSETS: return 2;
            case TAG_PROFILE: return 3;
            default: return 0;
        }
    }

    /**
     * 显示指定的 Fragment
     * 使用 show/hide 机制，避免重复创建
     */
    private void showFragment(String tag) {
        // 如果是当前 Fragment，不执行切换
        if (tag.equals(currentFragmentTag) && currentFragment != null) {
            Log.d(TAG, "已经是当前 Fragment: " + tag);
            return;
        }

        Log.d(TAG, "切换 Fragment: " + currentFragmentTag + " -> " + tag);

        Fragment targetFragment = getOrCreateFragment(tag);

        // 开始事务
        FragmentTransaction transaction = fragmentManager.beginTransaction();

        // 根据索引决定动画方向
        int currentIndex = getFragmentIndex(currentFragmentTag);
        int targetIndex = getFragmentIndex(tag);

        if (targetIndex > currentIndex) {
            // 目标在右边 -> 从右往左
            transaction.setCustomAnimations(
                    R.anim.slide_in_right1,  // enter
                    R.anim.slide_out_left1   // exit
            );
        } else {
            // 目标在左边 -> 从左往右
            transaction.setCustomAnimations(
                    R.anim.slide_in_left1,   // enter
                    R.anim.slide_out_right1  // exit
            );
        }

        // 隐藏当前 Fragment
        if (currentFragment != null && currentFragment.isAdded()) {
            transaction.hide(currentFragment);
            Log.d(TAG, "隐藏 Fragment: " + currentFragment.getClass().getSimpleName());
        }

        // 显示或添加目标 Fragment
        if (targetFragment.isAdded()) {
            transaction.show(targetFragment);
            Log.d(TAG, "显示已存在的 Fragment: " + targetFragment.getClass().getSimpleName());
        } else {
            transaction.add(R.id.nav_host_fragment, targetFragment, tag);
            Log.d(TAG, "添加新 Fragment: " + targetFragment.getClass().getSimpleName());
        }

        // 提交事务
        transaction.commitAllowingStateLoss();

        // 更新当前 Fragment
        currentFragment = targetFragment;
        currentFragmentTag = tag;
    }

    /**
     * 获取或创建 Fragment
     * 优先从缓存获取，避免重复创建
     */
    private Fragment getOrCreateFragment(String tag) {
        switch (tag) {
            case TAG_HOME:
                if (homeFragment == null) {
                    homeFragment = (HomeFragment) fragmentManager.findFragmentByTag(TAG_HOME);
                    if (homeFragment == null) {
                        homeFragment = new HomeFragment();
                        Log.d(TAG, "创建新的 HomeFragment");
                    }
                }
                return homeFragment;

            case TAG_CALENDAR:
                if (calendarFragment == null) {
                    calendarFragment = (CalendarFragment) fragmentManager.findFragmentByTag(TAG_CALENDAR);
                    if (calendarFragment == null) {
                        calendarFragment = new CalendarFragment();
                        Log.d(TAG, "创建新的 CalendarFragment");
                    }
                }
                return calendarFragment;

            case TAG_ASSETS:
                if (assetsFragment == null) {
                    assetsFragment = (AssetsFragment) fragmentManager.findFragmentByTag(TAG_ASSETS);
                    if (assetsFragment == null) {
                        assetsFragment = new AssetsFragment();
                        Log.d(TAG, "创建新的 AssetsFragment");
                    }
                }
                return assetsFragment;

            case TAG_PROFILE:
                if (profileFragment == null) {
                    profileFragment = (ProfileFragment) fragmentManager.findFragmentByTag(TAG_PROFILE);
                    if (profileFragment == null) {
                        profileFragment = new ProfileFragment();
                        Log.d(TAG, "创建新的 ProfileFragment");
                    }
                }
                return profileFragment;

            default:
                if (homeFragment == null) {
                    homeFragment = new HomeFragment();
                }
                return homeFragment;
        }
    }

    /**
     * 隐藏除指定标签外的所有 Fragment
     * 用于配置变更后恢复正确的显示状态
     */
    private void hideAllFragmentsExcept(String exceptTag) {
        FragmentTransaction transaction = fragmentManager.beginTransaction();

        String[] allTags = {TAG_HOME, TAG_CALENDAR, TAG_ASSETS, TAG_PROFILE};
        for (String tag : allTags) {
            if (!tag.equals(exceptTag)) {
                Fragment fragment = fragmentManager.findFragmentByTag(tag);
                if (fragment != null && fragment.isAdded() && !fragment.isHidden()) {
                    transaction.hide(fragment);
                    Log.d(TAG, "隐藏 Fragment: " + tag);
                }
            }
        }

        // 确保当前 Fragment 是显示状态
        Fragment currentFrag = fragmentManager.findFragmentByTag(exceptTag);
        if (currentFrag != null && currentFrag.isAdded() && currentFrag.isHidden()) {
            transaction.show(currentFrag);
            Log.d(TAG, "显示 Fragment: " + exceptTag);
        }

        transaction.commitAllowingStateLoss();
    }

    /**
     * 更新底部导航栏的选中状态
     */
    private void updateBottomNavigationSelection(String tag) {
        int itemId = getItemIdFromTag(tag);
        binding.bottomNavigation.setSelectedItemId(itemId);
    }

    /**
     * 根据菜单 itemId 获取 Fragment 标签
     */
    private String getFragmentTag(int itemId) {
        if (itemId == R.id.homeFragment) return TAG_HOME;
        if (itemId == R.id.calendarFragment) return TAG_CALENDAR;
        if (itemId == R.id.assetsFragment) return TAG_ASSETS;
        if (itemId == R.id.profileFragment) return TAG_PROFILE;
        return TAG_HOME;
    }

    /**
     * 根据标签获取对应的菜单 itemId
     */
    private int getItemIdFromTag(String tag) {
        switch (tag) {
            case TAG_HOME:
                return R.id.homeFragment;
            case TAG_CALENDAR:
                return R.id.calendarFragment;
            case TAG_ASSETS:
                return R.id.assetsFragment;
            case TAG_PROFILE:
                return R.id.profileFragment;
            default:
                return R.id.homeFragment;
        }
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
        // 清理 Fragment 引用
        homeFragment = null;
        calendarFragment = null;
        assetsFragment = null;
        profileFragment = null;
        currentFragment = null;
    }
}