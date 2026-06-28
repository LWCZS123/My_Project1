package com.example.my_project1.work;

import android.content.Context;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.remote.model.cloudaccount.BmobAccountApiImpl;
import com.example.my_project1.data.remote.model.cloudaccount.AccountGroup;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import cn.bmob.v3.exception.BmobException;
import cn.bmob.v3.listener.SaveListener;
import cn.bmob.v3.listener.UpdateListener;
import io.reactivex.annotations.NonNull;

/**
 * AccountSyncWorker - 混合删除方案优化版
 *
 * 关键改进：
 * - 账户删除成功后物理删除本地数据（防止"复活"）
 * - 删除失败保留 TO_DELETE 状态（下次重试）
 * - 处理云端对象不存在的情况（404视为成功）
 * - 统一删除逻辑，避免重复处理
 */
public class AccountSyncWorker extends Worker {

    private static final String TAG = "AccountSyncWorker";
    private static final int MAX_RETRIES = 3;
    private static final long LATCH_TIMEOUT_SECONDS = 30;

    private final AppDatabase db;
    private final BmobAccountApiImpl api;

    public AccountSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        db = AppDatabase.getInstance(context);
        api = new BmobAccountApiImpl(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Log.i(TAG, "========== 开始同步 ==========");

            // 🔴 关键修改：先处理删除，再处理创建和更新
            // 删除顺序：先账户（避免外键约束），再账户组
            boolean okDeleteAccounts = syncDeleteAccounts();
            if (!okDeleteAccounts) {
                Log.w(TAG, "账户删除同步未完全成功，将重试");
                return Result.retry();
            }

            boolean okDeleteGroups = syncDeleteAccountGroups();
            if (!okDeleteGroups) {
                Log.w(TAG, "账户组删除同步未完全成功，将重试");
                return Result.retry();
            }

            // 同步创建和更新
            boolean okGroups = syncAccountGroups();
            if (!okGroups) return Result.retry();

            boolean okAccounts = syncAccounts();
            if (!okAccounts) return Result.retry();

            Log.i(TAG, "========== 同步完成 ==========");
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "doWork 异常，准备重试：" + e.getMessage(), e);
            return Result.retry();
        }
    }

    // ======================== 🔴 新增：专门处理账户删除 ========================

    /**
     * 同步删除账户
     * - 云端删除成功 → 物理删除本地数据
     * - 云端删除失败 → 保留 TO_DELETE 状态，下次重试
     * - 云端对象不存在（404）→ 视为成功，物理删除本地
     */
    private boolean syncDeleteAccounts() {
        List<Account> deletedAccounts = db.accountDao().getPendingSyncAccounts();
        if (deletedAccounts == null || deletedAccounts.isEmpty()) {
            Log.d(TAG, "syncDeleteAccounts - 无待删除账户");
            return true;
        }

        // 筛选出 TO_DELETE 状态的账户
        int deleteCount = 0;
        int successCount = 0;
        int failCount = 0;

        for (Account account : deletedAccounts) {
            if (account.getSyncState() != SyncState.TO_DELETE) {
                continue;
            }
            deleteCount++;

            String objectId = account.getObjectId();
            Log.d(TAG, "🗑️ 处理待删除账户: " + account.getName() + " (ID: " + objectId + ")");

            if (objectId == null || objectId.isEmpty()) {
                // 本地账户没有云端ID，直接物理删除
                Log.i(TAG, "   - 无云端ID，直接物理删除本地数据");
                db.accountDao().delete(account);
                successCount++;
                continue;
            }

            // 尝试删除云端账户
            boolean cloudDeleteSuccess = false;
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    final boolean[] ok = {false};
                    final int[] errorCode = {0};
                    final CountDownLatch latch = new CountDownLatch(1);

                    int finalAttempt = attempt;
                    api.deleteAccount(objectId, new UpdateListener() {
                        @Override
                        public void done(BmobException e) {
                            if (e == null) {
                                Log.d(TAG, "   ✅ 云端删除成功（第" + finalAttempt + "次）");
                                ok[0] = true;
                            } else {
                                errorCode[0] = e.getErrorCode();
                                Log.e(TAG, "   ❌ 云端删除失败（第" + finalAttempt + "次）: "
                                        + e.getMessage() + " (错误码: " + errorCode[0] + ")");
                            }
                            latch.countDown();
                        }
                    });

                    boolean awaited = latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                    if (!awaited) {
                        Log.w(TAG, "   ⚠️ 等待云端删除超时（第" + attempt + "次）");
                        continue;
                    }

                    if (ok[0]) {
                        cloudDeleteSuccess = true;
                        break;
                    }

                    // 🔴 关键：如果云端对象不存在（错误码101），视为删除成功
                    if (errorCode[0] == 101) {
                        Log.i(TAG, "   ✅ 云端对象已不存在（404），视为删除成功");
                        cloudDeleteSuccess = true;
                        break;
                    }

                } catch (Exception e) {
                    Log.e(TAG, "   ❌ 删除异常（第" + attempt + "次）: " + e.getMessage(), e);
                }
            }

            // 🔴 关键：根据云端删除结果决定本地操作
            if (cloudDeleteSuccess) {
                // 云端删除成功，物理删除本地数据
                try {
                    db.accountDao().delete(account);
                    Log.i(TAG, "   ✅ 本地账户物理删除成功: " + account.getName());
                    successCount++;
                } catch (Exception e) {
                    Log.e(TAG, "   ❌ 本地物理删除失败: " + e.getMessage(), e);
                    failCount++;
                }
            } else {
                // 云端删除失败，保留 TO_DELETE 状态，下次重试
                Log.w(TAG, "   ⚠️ 云端删除失败，保留待删除标记: " + account.getName());
                failCount++;
            }
        }

        Log.i(TAG, String.format("syncDeleteAccounts 完成 - 总计:%d, 成功:%d, 失败:%d",
                deleteCount, successCount, failCount));

        // 🔴 只要有失败的，就返回 false 让 Worker 重试
        return failCount == 0;
    }

    /**
     * 同步删除账户组
     */
    private boolean syncDeleteAccountGroups() {
        List<com.example.my_project1.data.model.account.AccountGroup> groups =
                db.accountDao().getPendingSyncGroups();

        if (groups == null || groups.isEmpty()) {
            Log.d(TAG, "syncDeleteAccountGroups - 无待删除账户组");
            return true;
        }

        int deleteCount = 0;
        int successCount = 0;
        int failCount = 0;

        for (com.example.my_project1.data.model.account.AccountGroup group : groups) {
            if (group.getSyncState() != SyncState.TO_DELETE) {
                continue;
            }
            deleteCount++;

            Log.d(TAG, "🗑️ 处理待删除账户组: " + group.getName());

            boolean cloudDeleteSuccess = deleteGroupSync(group);

            if (cloudDeleteSuccess) {
                // 云端删除成功，物理删除本地数据
                try {
                    db.accountDao().deleteGroup(group);
                    Log.i(TAG, "   ✅ 本地账户组物理删除成功: " + group.getName());
                    successCount++;
                } catch (Exception e) {
                    Log.e(TAG, "   ❌ 本地物理删除失败: " + e.getMessage(), e);
                    failCount++;
                }
            } else {
                Log.w(TAG, "   ⚠️ 云端删除失败，保留待删除标记: " + group.getName());
                failCount++;
            }
        }

        Log.i(TAG, String.format("syncDeleteAccountGroups 完成 - 总计:%d, 成功:%d, 失败:%d",
                deleteCount, successCount, failCount));

        return failCount == 0;
    }

    // ======================== 账户组同步（创建/更新）========================

    private boolean syncAccountGroups() {
        List<com.example.my_project1.data.model.account.AccountGroup> groups =
                db.accountDao().getPendingSyncGroups();

        if (groups == null || groups.isEmpty()) {
            Log.d(TAG, "syncAccountGroups - 无待同步账户组");
            return true;
        }

        Log.i(TAG, "syncAccountGroups - 待同步组数量: " + groups.size());

        for (com.example.my_project1.data.model.account.AccountGroup g : groups) {
            // 🔴 跳过待删除的（已在 syncDeleteAccountGroups 中处理）
            if (g.getSyncState() == SyncState.TO_DELETE) {
                continue;
            }

            boolean ok = false;
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    SyncState state = g.getSyncState();
                    if (state == SyncState.TO_CREATE) {
                        ok = uploadGroupSync(g);
                    } else if (state == SyncState.TO_UPDATE) {
                        ok = updateGroupSync(g);
                    } else {
                        ok = true;
                    }

                    if (ok) break;
                    Log.w(TAG, "syncAccountGroups - 组同步第 " + attempt + " 次失败, name=" + g.getName());
                } catch (Throwable t) {
                    Log.e(TAG, "syncAccountGroups - 组同步异常, attempt=" + attempt, t);
                }
            }

            if (!ok) {
                Log.e(TAG, "syncAccountGroups - 最终失败: " + g.getName());
                return false;
            }

            // 标记为已同步
            g.setSyncState(SyncState.SYNCED);
            db.accountDao().updateGroup(g);
        }
        return true;
    }

    private boolean uploadGroupSync(com.example.my_project1.data.model.account.AccountGroup group) {
        try {
            String userId = group.getUserId();
            if (userId == null) {
                userId = api.getCurrentUserId();
                if (userId == null) {
                    Log.e(TAG, "uploadGroupSync - userId为空: " + group.getName());
                    return false;
                }
            }

            // 检查云端是否已存在同名
            List<AccountGroup> cloudList = api.getAllAccountGroupsSync(userId);
            if (cloudList != null) {
                for (AccountGroup c : cloudList) {
                    if (c.getName() != null && c.getName().equals(group.getName())) {
                        Log.w(TAG, "uploadGroupSync - 云端已存在同名组: " + group.getName());
                        return true;
                    }
                }
            }

            final boolean[] ok = {false};
            final CountDownLatch latch = new CountDownLatch(1);

            api.uploadAccountGroup(group, new SaveListener<String>() {
                @Override
                public void done(String objectId, BmobException e) {
                    if (e == null) {
                        Log.d(TAG, "uploadGroupSync - 上传成功: " + group.getName());
                        group.setObjectId(objectId);
                        ok[0] = true;
                    } else {
                        Log.e(TAG, "uploadGroupSync - 上传失败: " + e.getMessage());
                    }
                    latch.countDown();
                }
            });

            latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return ok[0];
        } catch (Exception e) {
            Log.e(TAG, "uploadGroupSync 异常: " + e.getMessage(), e);
            return false;
        }
    }

    private boolean updateGroupSync(com.example.my_project1.data.model.account.AccountGroup group) {
        try {
            final boolean[] ok = {false};
            final CountDownLatch latch = new CountDownLatch(1);

            api.updateAccountGroup(group, new UpdateListener() {
                @Override
                public void done(BmobException e) {
                    if (e == null) {
                        Log.d(TAG, "updateGroupSync - 更新成功: " + group.getName());
                        ok[0] = true;
                    } else {
                        Log.e(TAG, "updateGroupSync - 更新失败: " + e.getMessage());
                    }
                    latch.countDown();
                }
            });

            latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return ok[0];
        } catch (Exception e) {
            Log.e(TAG, "updateGroupSync 异常: " + e.getMessage(), e);
            return false;
        }
    }

    private boolean deleteGroupSync(com.example.my_project1.data.model.account.AccountGroup group) {
        String objectId = group.getObjectId();

        if (objectId == null || objectId.isEmpty()) {
            Log.i(TAG, "deleteGroupSync - 无云端ID，视为删除成功: " + group.getName());
            return true;
        }

        try {
            final boolean[] ok = {false};
            final int[] errorCode = {0};
            final CountDownLatch latch = new CountDownLatch(1);

            api.deleteAccountGroup(objectId, new UpdateListener() {
                @Override
                public void done(BmobException e) {
                    if (e == null) {
                        Log.d(TAG, "deleteGroupSync - 云端删除成功: " + group.getName());
                        ok[0] = true;
                    } else {
                        errorCode[0] = e.getErrorCode();
                        Log.e(TAG, "deleteGroupSync - 云端删除失败: " + e.getMessage());
                    }
                    latch.countDown();
                }
            });

            latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // 🔴 如果云端对象不存在，视为删除成功
            if (errorCode[0] == 101) {
                Log.i(TAG, "deleteGroupSync - 云端对象已不存在，视为删除成功");
                return true;
            }

            return ok[0];
        } catch (Exception e) {
            Log.e(TAG, "deleteGroupSync 异常: " + e.getMessage(), e);
            return false;
        }
    }

    // ======================== 账户同步（创建/更新）========================

    private boolean syncAccounts() {
        List<Account> accounts = db.accountDao().getPendingSyncAccounts();

        if (accounts == null || accounts.isEmpty()) {
            Log.d(TAG, "syncAccounts - 无待同步账户");
            return true;
        }

        Log.i(TAG, "syncAccounts - 待同步账户数量: " + accounts.size());

        for (Account a : accounts) {
            // 🔴 跳过待删除的（已在 syncDeleteAccounts 中处理）
            if (a.getSyncState() == SyncState.TO_DELETE) {
                continue;
            }

            boolean ok = false;
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    SyncState state = a.getSyncState();
                    if (state == SyncState.TO_CREATE || state == SyncState.TO_UPDATE) {
                        ok = api.uploadAccountSync(a);
                    } else {
                        ok = true;
                    }

                    if (ok) break;
                    Log.w(TAG, "syncAccounts - 第 " + attempt + " 次失败: " + a.getName());
                } catch (Throwable t) {
                    Log.e(TAG, "syncAccounts - 异常 attempt=" + attempt, t);
                }
            }

            if (!ok) {
                Log.e(TAG, "syncAccounts - 最终失败: " + a.getName());
                return false;
            }

            // 标记为已同步
            a.setSyncState(SyncState.SYNCED);
            db.accountDao().update(a);
        }
        return true;
    }

    // ======================== WorkManager 入口 ========================

    public static Constraints getDefaultConstraints() {
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
    }

    public static void enqueue(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(AccountSyncWorker.class)
                .setConstraints(getDefaultConstraints())
                .build();
        WorkManager.getInstance(context)
                .enqueueUniqueWork("AccountSync", ExistingWorkPolicy.KEEP, request);
    }
}