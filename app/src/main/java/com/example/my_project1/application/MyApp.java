package com.example.my_project1.application;

import android.app.Application;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.util.Log;

import com.amap.api.location.AMapLocationClient;
import com.example.my_project1.receiver.NetworkReceiver;
import com.example.my_project1.scheduler.SyncScheduler;
import com.example.my_project1.utils.AppInitializer;
import com.example.my_project1.utils.HolidayUtil;

import cn.bmob.v3.Bmob;
import cn.bmob.v3.BmobUser;

/**
 * MyApp
 * 全局 Application：
 * - 初始化 Bmob
 * - 注册网络监听
 * - 启动定期同步任务
 */
public class MyApp extends Application {

    private static final String TAG = "MyApp";
    private NetworkReceiver networkReceiver;
    private String userId;

    @Override
    public void onCreate() {
        super.onCreate();
        
        // 初始化节假日数据 (从 Assets 加载 JSON)
        HolidayUtil.init(this);

        AMapLocationClient.updatePrivacyShow(this, true, true); // 告知SDK已经展示隐私政策弹窗
        AMapLocationClient.updatePrivacyAgree(this, true);


        // 初始化 Bmob
        Bmob.initialize(this, "06602b7805aefe310720f1a2ed13fcea");
        Log.d(TAG, "✅ Bmob 初始化成功");

        BmobUser currentUser = BmobUser.getCurrentUser();
        if(currentUser != null){
            userId = currentUser.getObjectId();

        }else {
            userId = null;
        }
        AppInitializer.initSystemCategories(getApplicationContext(), userId);
        // 注册网络监听
        networkReceiver = new NetworkReceiver();
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(networkReceiver, filter);
        Log.d(TAG, "已注册 NetworkReceiver（监听网络状态变化）");

        // 启动定期同步任务
        SyncScheduler.schedulePeriodicSync(this);
        Log.d(TAG, "⏰ 定期同步任务已启动");
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        if (networkReceiver != null) {
            unregisterReceiver(networkReceiver);
            Log.d(TAG, "🧹 NetworkReceiver 已注销");
        }
    }
}
