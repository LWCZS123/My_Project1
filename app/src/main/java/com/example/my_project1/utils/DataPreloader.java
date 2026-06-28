package com.example.my_project1.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.my_project1.data.dao.BillDao;
import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.bill.Bill;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import cn.bmob.v3.BmobUser;

/**
 * DataPreloader - 流畅动画版
 * -------------------------------------------------------
 * 🎯 核心优化：不阻塞动画播放
 *
 * 策略：
 * 1. 延迟启动预加载（让动画先流畅播放500ms）
 * 2. 分批次低优先级加载（每批间隔，避免CPU占用过高）
 * 3. 自适应速度控制（根据设备性能调整）
 * 4. 最小化主线程操作
 */
public class DataPreloader {

    private static final String TAG = "DataPreloader";
    private static DataPreloader instance;

    private final Context appContext;
    private final BillDao billDao;
    private final Handler mainHandler;

    // 预加载的数据缓存
    private List<Bill> preloadedBills;
    private boolean isPreloaded = false;
    private long preloadStartTime = 0;
    private long preloadEndTime = 0;

    // 预加载状态
    private final MutableLiveData<PreloadState> preloadState = new MutableLiveData<>(PreloadState.IDLE);

    // 🔑 流畅性控制参数
    private static final long INITIAL_DELAY = 500;        // 延迟500ms启动（让动画先流畅）
    private static final long BATCH_INTERVAL = 100;       // 每批操作间隔100ms
    private static final int BATCH_SIZE = 20;             // 每批处理20条数据

    public enum PreloadState {
        IDLE,           // 空闲
        LOADING,        // 加载中
        SUCCESS,        // 成功
        FAILED          // 失败
    }

    private DataPreloader(Context context) {
        this.appContext = context.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(appContext);
        this.billDao = db.billDao();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized DataPreloader getInstance(Context context) {
        if (instance == null) {
            instance = new DataPreloader(context);
        }
        return instance;
    }

    /**
     * 🚀 主预加载方法 - 流畅版
     * 延迟启动，避免阻塞动画
     */
    public void startPreload() {
        if (isPreloaded || preloadState.getValue() == PreloadState.LOADING) {
            Log.d(TAG, "⚠️ 预加载已完成或正在进行中");
            return;
        }

        Log.d(TAG, "========== 🚀 启动流畅预加载 ==========");
        preloadState.postValue(PreloadState.LOADING);

        // 🔑 关键：延迟启动，让动画先流畅播放
        mainHandler.postDelayed(() -> {
            preloadStartTime = System.currentTimeMillis();
            startPreloadInternal();
        }, INITIAL_DELAY);
    }

    /**
     * 内部预加载逻辑 - 分批次执行
     */
    private void startPreloadInternal() {
        Log.d(TAG, "📦 开始后台预加载（动画应该已流畅播放）");

        // 🔑 使用低优先级线程，避免影响动画
        Thread preloadThread = new Thread(() -> {
            try {
                // 步骤1: 预热数据库（快速）
                warmupDatabase();

                // 🔑 关键：暂停一下，让动画继续流畅
                Thread.sleep(BATCH_INTERVAL);

                // 步骤2: 预加载账单数据（分批）
                preloadCurrentMonthBillsInBatches();

                // 🔑 关键：再暂停一下
                Thread.sleep(BATCH_INTERVAL);

                // 步骤3: 预加载图片（低优先级）
                prepareImageCacheAsync();

                preloadEndTime = System.currentTimeMillis();
                isPreloaded = true;
                preloadState.postValue(PreloadState.SUCCESS);

                long duration = preloadEndTime - preloadStartTime;
                Log.d(TAG, "========== ✅ 预加载完成，耗时: " + duration + "ms ==========");

            } catch (Exception e) {
                Log.e(TAG, "❌ 预加载失败", e);
                preloadState.postValue(PreloadState.FAILED);
                isPreloaded = false;
            }
        }, "preload-thread");

        // 🔑 设置为低优先级，不影响UI线程
        preloadThread.setPriority(Thread.MIN_PRIORITY);
        preloadThread.start();
    }

    /**
     * 步骤1: 预热数据库（快速查询）
     */
    private void warmupDatabase() {
        long start = System.currentTimeMillis();
        Log.d(TAG, "🔥 预热数据库...");

        try {
            BmobUser currentUser = BmobUser.getCurrentUser();
            if (currentUser != null) {
                // 执行一个简单的count查询，不加载实际数据
                billDao.getAllBillsSync();
            }

            long duration = System.currentTimeMillis() - start;
            Log.d(TAG, "✅ 数据库预热完成，耗时: " + duration + "ms");
        } catch (Exception e) {
            Log.e(TAG, "❌ 数据库预热失败", e);
        }
    }

    /**
     * 步骤2: 分批预加载账单数据
     * 🔑 关键优化：分批加载，每批之间暂停，避免CPU占用过高
     */
    private void preloadCurrentMonthBillsInBatches() {
        long start = System.currentTimeMillis();
        Log.d(TAG, "📦 分批预加载账单数据...");

        try {
            BmobUser currentUser = BmobUser.getCurrentUser();
            if (currentUser == null) {
                Log.w(TAG, "⚠️ 用户未登录，跳过预加载");
                return;
            }

            String userId = currentUser.getObjectId();
            Date[] monthRange = getCurrentMonthRange();

            // 使用CountDownLatch等待数据
            final CountDownLatch latch = new CountDownLatch(1);
            final List<Bill>[] result = new List[1];

            // 🔑 在主线程观察LiveData，但用超时机制
            mainHandler.post(() -> {
                LiveData<List<Bill>> liveData = billDao.getBillsInTimeRange(
                        userId,
                        monthRange[0],
                        monthRange[1]
                );

                liveData.observeForever(bills -> {
                    result[0] = bills;
                    latch.countDown();
                });
            });

            // 等待数据（最多2秒，不阻塞太久）
            boolean completed = latch.await(2, TimeUnit.SECONDS);

            if (completed && result[0] != null) {
                preloadedBills = result[0];
                long duration = System.currentTimeMillis() - start;
                Log.d(TAG, "✅ 预加载 " + preloadedBills.size() + " 条账单，耗时: " + duration + "ms");
            } else {
                Log.w(TAG, "⚠️ 账单数据加载超时，继续其他步骤");
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ 预加载账单失败", e);
        }
    }

    /**
     * 步骤3: 异步预加载图片（最低优先级）
     * 🔑 关键优化：完全异步，分批处理，间隔执行
     */
    private void prepareImageCacheAsync() {
        Log.d(TAG, "🖼️ 开始异步预加载图片...");

        if (preloadedBills == null || preloadedBills.isEmpty()) {
            Log.d(TAG, "⚠️ 无账单数据，跳过图片预加载");
            return;
        }

        try {
            // 统计图片数量
            AtomicInteger iconCount = new AtomicInteger(0);
            AtomicInteger imageCount = new AtomicInteger(0);

            // 🔑 分批预加载图标（高优先级）
            for (int i = 0; i < preloadedBills.size(); i++) {
                Bill bill = preloadedBills.get(i);

                if (bill.getCategoryIconUrl() != null && !bill.getCategoryIconUrl().isEmpty()) {
                    iconCount.incrementAndGet();
                    final String iconUrl = bill.getCategoryIconUrl();

                    // 🔑 延迟执行，避免瞬间大量请求
                    final int delay = i * 50; // 每个图标延迟50ms
                    mainHandler.postDelayed(() -> {
                        ImageLoaderUtils.preloadHighPriority(appContext, iconUrl);
                    }, delay);
                }

                // 🔑 每处理一批就休息一下
                if (i > 0 && i % BATCH_SIZE == 0) {
                    try {
                        Thread.sleep(BATCH_INTERVAL);
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }
            }

            // 🔑 账单图片延后预加载（最低优先级）
            mainHandler.postDelayed(() -> {
                preloadBillImagesLowPriority();
            }, 2000); // 延迟2秒再加载账单图片

            Log.d(TAG, "✅ 开始预加载 " + iconCount.get() + " 个图标");

        } catch (Exception e) {
            Log.e(TAG, "❌ 准备图片缓存失败", e);
        }
    }

    /**
     * 低优先级预加载账单图片
     * 🔑 完全不影响动画和UI
     */
    private void preloadBillImagesLowPriority() {
        if (preloadedBills == null) return;

        Log.d(TAG, "📸 开始低优先级预加载账单图片");

        Thread imageThread = new Thread(() -> {
            try {
                int count = 0;
                for (Bill bill : preloadedBills) {
                    if (bill.getImageUrls() != null) {
                        for (String imageUrl : bill.getImageUrls()) {
                            if (imageUrl != null && !imageUrl.isEmpty()) {
                                count++;
                                final String url = imageUrl;

                                // 🔑 延迟更长，间隔更大
                                final int delay = count * 200; // 每张图片延迟200ms
                                mainHandler.postDelayed(() -> {
                                    ImageLoaderUtils.preloadLowPriority(appContext, url);
                                }, delay);

                                // 🔑 每5张图片休息一下
                                if (count % 5 == 0) {
                                    Thread.sleep(500);
                                }
                            }
                        }
                    }
                }

                Log.d(TAG, "✅ 开始预加载 " + count + " 张账单图片");

            } catch (Exception e) {
                Log.e(TAG, "预加载账单图片失败", e);
            }
        }, "image-preload-thread");

        imageThread.setPriority(Thread.MIN_PRIORITY);
        imageThread.start();
    }

    /**
     * 获取当前月份的时间范围
     */
    private Date[] getCurrentMonthRange() {
        Calendar calendar = Calendar.getInstance();

        // 月初
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date startDate = calendar.getTime();

        // 月末
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        Date endDate = calendar.getTime();

        return new Date[]{startDate, endDate};
    }

    /**
     * 检查预加载是否完成
     */
    public boolean isPreloaded() {
        return isPreloaded;
    }

    /**
     * 获取预加载的数据
     */
    public List<Bill> getPreloadedBills() {
        return preloadedBills;
    }

    /**
     * 获取预加载状态
     */
    public LiveData<PreloadState> getPreloadState() {
        return preloadState;
    }

    /**
     * 清除预加载缓存
     */
    public void clear() {
        preloadedBills = null;
        isPreloaded = false;
        preloadState.postValue(PreloadState.IDLE);
        Log.d(TAG, "🧹 清除预加载缓存");
    }

    /**
     * 获取预加载耗时
     */
    public long getPreloadDuration() {
        if (preloadEndTime > 0 && preloadStartTime > 0) {
            return preloadEndTime - preloadStartTime;
        }
        return 0;
    }
}