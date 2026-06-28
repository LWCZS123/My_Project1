package com.example.my_project1.utils;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AppExecutors（增强版）
 * ---------------------------------------
 * 统一管理线程池，支持：
 *  - diskIO()：磁盘I/O操作（Room、文件等）
 *  - networkIO()：网络请求（Bmob、Retrofit等）
 *  - computation()：计算密集型任务（Json解析、大数据处理）
 *  - mainThread()：主线程（UI操作）
 *
 * 单例 + 命名线程，方便调试 & 性能监控
 */
public class AppExecutors {

    // ✅ 线程数量配置
    private static final int DISK_IO_THREADS = 2;
    private static final int NETWORK_IO_THREADS = 4;
    private static final int COMPUTATION_THREADS =
            Math.max(2, Runtime.getRuntime().availableProcessors() - 1);

    private static volatile AppExecutors instance;

    private final ExecutorService diskIO;
    private final ExecutorService networkIO;
    private final ExecutorService computation;
    private final Executor mainThread;

    private AppExecutors() {
        this.diskIO = Executors.newFixedThreadPool(DISK_IO_THREADS, new NamedThreadFactory("disk-io"));
        this.networkIO = Executors.newFixedThreadPool(NETWORK_IO_THREADS, new NamedThreadFactory("network-io"));
        this.computation = Executors.newFixedThreadPool(COMPUTATION_THREADS, new NamedThreadFactory("computation"));
        this.mainThread = new MainThreadExecutor();
    }

    /**
     * ✅ 获取单例
     */
    public static AppExecutors get() {
        if (instance == null) {
            synchronized (AppExecutors.class) {
                if (instance == null) {
                    instance = new AppExecutors();
                }
            }
        }
        return instance;
    }

    /**
     * 🧩 磁盘IO（数据库、文件）
     */
    public ExecutorService diskIO() {
        return diskIO;
    }

    /**
     * 🌐 网络请求（Retrofit、Bmob、OkHttp）
     */
    public ExecutorService networkIO() {
        return networkIO;
    }

    /**
     * ⚙️ 计算密集任务（如JSON解析、数据转换）
     */
    public ExecutorService computation() {
        return computation;
    }

    /**
     * 🪄 主线程任务（UI回调）
     */
    public Executor mainThread() {
        return mainThread;
    }

    /**
     * 通用 execute() 默认使用 diskIO
     */
    public void execute(Runnable task) {
        diskIO.execute(task);
    }

    /**
     * ✅ 自定义命名线程工厂
     */
    private static class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger count = new AtomicInteger(1);
        private final String prefix;

        NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + count.getAndIncrement());
            t.setDaemon(false);
            return t;
        }
    }

    /**
     * ✅ 主线程执行器
     */
    private static class MainThreadExecutor implements Executor {
        private final Handler mainHandler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(Runnable command) {
            mainHandler.post(command);
        }
    }
}
