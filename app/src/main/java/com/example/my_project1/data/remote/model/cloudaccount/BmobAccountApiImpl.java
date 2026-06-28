package com.example.my_project1.data.remote.model.cloudaccount;

import android.content.Context;
import android.util.Log;

import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.utils.BmobPointerUtil;

import java.util.List;

import cn.bmob.v3.BmobQuery;
import cn.bmob.v3.BmobUser;
import cn.bmob.v3.exception.BmobException;
import cn.bmob.v3.listener.FindListener;
import cn.bmob.v3.listener.SaveListener;
import cn.bmob.v3.listener.UpdateListener;

/**
 * BmobAccountApiImpl
 * ----------------------------------------------------------------
 * 功能：
 *   - 上传 / 更新 / 删除 账户组(AccountGroup)
 *   - 上传 / 更新 / 删除 账户(Account)
 *   - 拉取当前用户的账户及分组
 *   - 与本地 Room 数据表同步
 *
 * 说明：
 *   - 所有方法均基于 Bmob SDK 的异步回调接口（SaveListener / UpdateListener / FindListener）
 *   - Repository 层可封装成协程、LiveData 或 RxJava 形式使用
 *   - CloudAccount / CloudAccountGroup 在 Bmob 云端需建立对应表结构
 */
public class BmobAccountApiImpl {

    private static final String TAG = "BmobAccountApiImpl";

    private final Context context;
    private final AppDatabase db;

    public BmobAccountApiImpl(Context context) {
        this.context = context.getApplicationContext();
        this.db = AppDatabase.getInstance(this.context);
    }

    // 无参构造器（用于单元测试或静态调用）
    public BmobAccountApiImpl() {
        this.context = null;
        this.db = null;
    }

    /** 获取当前登录用户 ID */
    public String getCurrentUserId() {
        BmobUser user = BmobUser.getCurrentUser(BmobUser.class);
        return user != null ? user.getObjectId() : null;
    }

    // ----------------------------------------------------------------------
    // 🟢 账户组（AccountGroup）部分
    // ----------------------------------------------------------------------

    /** 上传账户组 */
    public void uploadAccountGroup(com.example.my_project1.data.model.account.AccountGroup localGroup, SaveListener<String> listener) {
        AccountGroup cloud = AccountGroup.fromLocal(localGroup);
        cloud.setUser(BmobPointerUtil.user(getCurrentUserId()));

        cloud.save(new SaveListener<String>() {
            @Override
            public void done(String objectId, BmobException e) {
                if (e == null) {
                    Log.d(TAG, "✅ 上传账户组成功: " + objectId);
                    localGroup.setObjectId(objectId);
                    localGroup.setSyncState(SyncState.SYNCED);
                    listener.done(objectId, null);
                } else {
                    Log.e(TAG, "❌ 上传账户组失败: " + e.getMessage());
                    listener.done(null, e);
                }
            }
        });
    }

    /** 更新账户组 */
    public void updateAccountGroup(com.example.my_project1.data.model.account.AccountGroup localGroup, UpdateListener listener) {
        if (localGroup.getObjectId() == null) {
            listener.done(new BmobException(900, "cloudId为空，无法更新账户组"));
            return;
        }

        AccountGroup cloud = AccountGroup.fromLocal(localGroup);

        cloud.update(localGroup.getObjectId(), new UpdateListener() {
            @Override
            public void done(BmobException e) {
                if (e == null) {
                    Log.d(TAG, "✅ 更新账户组成功: " + localGroup.getName());
                    localGroup.setSyncState(SyncState.SYNCED);
                    listener.done(null);
                } else {
                    Log.e(TAG, "❌ 更新账户组失败: " + e.getMessage());
                    listener.done(e);
                }
            }
        });
    }

    /** 删除账户组 */
    public void deleteAccountGroup(String cloudId, UpdateListener listener) {
        if (cloudId == null) {
            listener.done(new BmobException(901, "cloudId为空，无法删除账户组"));
            return;
        }

        AccountGroup cloud = new AccountGroup();
        cloud.setObjectId(cloudId);
        cloud.delete(listener);
    }

    /** 拉取用户的所有账户组 */
    public void fetchAccountGroups(FindListener<AccountGroup> listener) {
        String userId = getCurrentUserId();
        if (userId == null) {
            listener.done(null, new BmobException(902, "用户未登录"));
            return;
        }
        BmobQuery<AccountGroup> query = new BmobQuery<>();
        query.addWhereEqualTo("user", BmobPointerUtil.user(userId));
        query.order("order");
        query.findObjects(listener);
    }

    // ----------------------------------------------------------------------
    // 🟡 账户（Account）部分
    // ----------------------------------------------------------------------

    /** 上传账户 */
    public void uploadAccount(com.example.my_project1.data.model.account.Account local, SaveListener<String> listener) {
        String userId = getCurrentUserId();
        if (userId == null) {
            listener.done(null, new BmobException(903, "用户未登录"));
            return;
        }

        Account cloud = Account.fromLocal(local);
        cloud.setUser(BmobPointerUtil.user(getCurrentUserId()));

        // 关联账户组（如果存在 groupCloudId）
        if (local.getGroupId() != null) {
            cloud.setGroup(BmobPointerUtil.group(local.getGroupId()));
        }

        cloud.save(new SaveListener<String>() {
            @Override
            public void done(String objectId, BmobException e) {
                if (e == null) {
                    Log.d(TAG, "✅ 上传账户成功: " + local.getName());
                    local.setObjectId(objectId);
                    local.setSyncState(SyncState.SYNCED);
                    listener.done(objectId, null);
                } else {
                    Log.e(TAG, "❌ 上传账户失败: " + e.getMessage());
                    listener.done(null, e);
                }
            }
        });
    }


    /**
     * 🔴 新增：同步上传单个账户组（阻塞）
     */
    public boolean uploadAccountGroupSync(com.example.my_project1.data.model.account.AccountGroup local) {
        try {
            AccountGroup cloud = AccountGroup.fromLocal(local);
            cloud.setUser(BmobPointerUtil.user(getCurrentUserId()));

            String cloudId;
            if (local.getObjectId() == null || local.getObjectId().isEmpty()) {
                // 创建新账户组
                cloudId = cloud.saveSync();
                local.setObjectId(cloudId);
                Log.d(TAG, "✅ 同步创建账户组成功: " + local.getName() + " -> " + cloudId);
            } else {
                // 更新现有账户组
                cloud.updateSync(local.getObjectId());
                cloudId = local.getObjectId();
                Log.d(TAG, "✅ 同步更新账户组成功: " + local.getName());
            }

            local.setSyncState(SyncState.SYNCED);

            if (db != null) {
                AppExecutors.get().diskIO().execute(new Runnable() {
                    @Override
                    public void run() {
                        db.accountDao().updateGroup(local);
                    }
                });
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, "❌ 同步上传账户组失败: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 🔴 新增：同步上传单个账户组（阻塞）
     */
    public boolean uploadAccountSync(com.example.my_project1.data.model.account.AccountGroup local) {
        try {
            AccountGroup cloud = AccountGroup.fromLocal(local);
            cloud.setUser(BmobPointerUtil.user(getCurrentUserId()));

            String cloudId;
            if (local.getObjectId() == null || local.getObjectId().isEmpty()) {
                // 创建新账户组
                cloudId = cloud.saveSync();
                local.setObjectId(cloudId);
                Log.d(TAG, "✅ 同步创建账户组成功: " + local.getName() + " -> " + cloudId);
            } else {
                // 更新现有账户组
                cloud.updateSync(local.getObjectId());
                cloudId = local.getObjectId();
                Log.d(TAG, "✅ 同步更新账户组成功: " + local.getName());
            }

            local.setSyncState(SyncState.SYNCED);

            if (db != null) {
                AppExecutors.get().diskIO().execute(() -> db.accountDao().updateGroup(local));
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, "❌ 同步上传账户组失败: " + e.getMessage(), e);
            return false;
        }
    }

    /** 更新账户 */
    public void updateAccount(com.example.my_project1.data.model.account.Account local, UpdateListener listener) {
        if (local.getObjectId() == null) {
            listener.done(new BmobException(904, "cloudId为空，无法更新账户"));
            return;
        }

        Account cloud = Account.fromLocal(local);

        if (local.getGroupId() != null) {
            cloud.setGroup(BmobPointerUtil.group(local.getGroupId()));
        }

        cloud.update(local.getObjectId(), new UpdateListener() {
            @Override
            public void done(BmobException e) {
                if (e == null) {
                    Log.d(TAG, "✅ 更新账户成功: " + local.getName());
                    local.setSyncState(SyncState.SYNCED);
                    listener.done(null);
                } else {
                    Log.e(TAG, "❌ 更新账户失败: " + e.getMessage());
                    listener.done(e);
                }
            }
        });
    }

    /** 删除账户 */
    public void deleteAccount(String cloudId, UpdateListener listener) {
        if (cloudId == null) {
            listener.done(new BmobException(905, "cloudId为空，无法删除账户"));
            return;
        }

        Account cloud = new Account();
        cloud.setObjectId(cloudId);
        cloud.delete(listener);
    }

    /** 拉取当前用户的所有账户 */
    public void fetchAccounts(FindListener<Account> listener) {
        String userId = getCurrentUserId();
        if (userId == null) {
            listener.done(null, new BmobException(906, "用户未登录"));
            return;
        }

        BmobQuery<Account> query = new BmobQuery<>();
        query.addWhereEqualTo("user", BmobPointerUtil.user(userId));
        query.include("group"); // 展开账户组对象
        query.order("createdAt");
        query.findObjects(listener);
    }

    // ----------------------------------------------------------------------
    // 🧩 同步接口（同步方法，仅在后台任务或初始化使用）
    // ----------------------------------------------------------------------

    /** 同步上传单个账户（阻塞） */
    public boolean uploadAccountSync(com.example.my_project1.data.model.account.Account local) {
        try {
            Account cloud = Account.fromLocal(local);
            cloud.setUser(BmobPointerUtil.user(getCurrentUserId()));

            // 关联账户组（如果存在 groupCloudId）
            if (local.getGroupId() != null && !local.getGroupId().isEmpty()) {
                cloud.setGroup(BmobPointerUtil.group(local.getGroupId()));
            }

            String cloudId;
            if (local.getObjectId() == null || local.getObjectId().isEmpty()) {
                // 🔴 新建：直接 saveSync，由云端分配 objectId
                cloudId = cloud.saveSync();
                local.setObjectId(cloudId);
            } else {
                // 🔴 修复：必须先把 objectId 设置到 cloud 对象本身，
                //    Bmob updateSync 内部通过 getObjectId() 构建 URL，
                //    如果 objectId 为空，请求体序列化为 null，导致 NPE。
                cloud.setObjectId(local.getObjectId());
                cloud.updateSync(local.getObjectId());
                cloudId = local.getObjectId();
            }

            local.setSyncState(SyncState.SYNCED);

            if (db != null) {
                AppExecutors.get().diskIO().execute(() -> db.accountDao().update(local));
            }

            Log.d(TAG, "✅ 同步上传账户成功: " + local.getName() + " -> " + cloudId);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "❌ 同步上传账户失败: " + e.getMessage(), e);
            return false;
        }
    }

    /** 同步获取当前用户的所有账户 */
    public List<Account> getAllAccountsSync(String userId) throws Exception {
        if (userId == null) return null;
        BmobQuery<Account> query = new BmobQuery<>();
        query.addWhereEqualTo("user", BmobPointerUtil.user(userId));
        query.order("createdAt");
        return query.findObjectsSync(Account.class);
    }

    /** 同步获取所有账户组 */
    public List<AccountGroup> getAllAccountGroupsSync(String userId) throws Exception {
        if (userId == null) return null;
        BmobQuery<AccountGroup> query = new BmobQuery<>();
        query.addWhereEqualTo("user", BmobPointerUtil.user(userId));
        query.order("order");
        return query.findObjectsSync(AccountGroup.class);
    }
}