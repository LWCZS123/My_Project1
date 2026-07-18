package com.example.my_project1.data.remote.model.cloudaccount;

import android.content.Context;
import android.util.Log;

import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.SyncState;
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
 */
public class BmobAccountApiImpl {

    private static final String TAG = "BmobAccountApiImpl";

    private final Context context;
    private final AppDatabase db;

    public BmobAccountApiImpl(Context context) {
        this.context = context.getApplicationContext();
        this.db = AppDatabase.getInstance(this.context);
    }

    public BmobAccountApiImpl() {
        this.context = null;
        this.db = null;
    }

    /** 获取当前登录用户 ID */
    public String getCurrentUserId() {
        BmobUser user = BmobUser.getCurrentUser();
        return user != null ? user.getObjectId() : null;
    }

    // ----------------------------------------------------------------------
    // 🟢 账户组（AccountGroup）部分
    // ----------------------------------------------------------------------

    /** 上传账户组 (异步) */
    public void uploadAccountGroup(com.example.my_project1.data.model.account.AccountGroup localGroup, SaveListener<String> listener) {
        AccountGroup cloud = AccountGroup.fromLocal(localGroup);
        cloud.setUser(BmobPointerUtil.user(getCurrentUserId()));

        cloud.save(new SaveListener<String>() {
            @Override
            public void done(String objectId, BmobException e) {
                if (e == null) {
                    localGroup.setObjectId(objectId);
                    localGroup.setSyncState(SyncState.SYNCED);
                    listener.done(objectId, null);
                } else {
                    listener.done(null, e);
                }
            }
        });
    }

    /** 更新账户组 (异步) */
    public void updateAccountGroup(com.example.my_project1.data.model.account.AccountGroup localGroup, UpdateListener listener) {
        if (localGroup.getObjectId() == null) {
            listener.done(new BmobException(900, "cloudId为空，无法更新账户组"));
            return;
        }
        AccountGroup cloud = AccountGroup.fromLocal(localGroup);
        cloud.setObjectId(localGroup.getObjectId());
        cloud.update(localGroup.getObjectId(), listener);
    }

    /** 删除账户组 (异步) */
    public void deleteAccountGroup(String cloudId, UpdateListener listener) {
        if (cloudId == null) {
            listener.done(new BmobException(901, "cloudId为空，无法删除账户组"));
            return;
        }
        AccountGroup cloud = new AccountGroup();
        cloud.setObjectId(cloudId);
        cloud.delete(listener);
    }

    /** 拉取用户的所有账户组 (异步) */
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

    /**
     * 🔴 同步上传账户组 (阻塞)
     * 用于后台同步任务，包含去重逻辑
     */
    public boolean uploadAccountGroupSync(com.example.my_project1.data.model.account.AccountGroup local) {
        try {
            String userId = getCurrentUserId();
            if (userId == null) return false;

            AccountGroup cloud = AccountGroup.fromLocal(local);
            cloud.setUser(BmobPointerUtil.user(userId));

            String cloudId = local.getObjectId();
            if (cloudId == null || cloudId.isEmpty()) {
                // 1. 去重检查：根据名称查找该用户下是否已存在同名组
                BmobQuery<AccountGroup> query = new BmobQuery<>();
                query.addWhereEqualTo("user", BmobPointerUtil.user(userId));
                query.addWhereEqualTo("name", local.getName());
                List<AccountGroup> existing = query.findObjectsSync(AccountGroup.class);
                
                if (existing != null && !existing.isEmpty()) {
                    cloudId = existing.get(0).getObjectId();
                    Log.d(TAG, "♻️ 云端已存在同名账户组: " + local.getName() + " -> " + cloudId);
                } else {
                    // 2. 创建新账户组
                    cloudId = cloud.saveSync();
                    if (cloudId == null || cloudId.isEmpty()) return false;
                    Log.d(TAG, "✅ 同步创建账户组成功: " + local.getName() + " -> " + cloudId);
                }
                local.setObjectId(cloudId);
            } else {
                // 更新现有账户组
                cloud.setObjectId(cloudId);
                cloud.updateSync(cloudId);
                Log.d(TAG, "✅ 同步更新账户组成功: " + local.getName());
            }

            local.setSyncState(SyncState.SYNCED);
            if (db != null) {
                db.accountDao().updateGroup(local);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "❌ 同步上传账户组失败: " + e.getMessage(), e);
            return false;
        }
    }

    // ----------------------------------------------------------------------
    // 🟡 账户（Account）部分
    // ----------------------------------------------------------------------

    /** 上传账户 (异步) */
    public void uploadAccount(com.example.my_project1.data.model.account.Account local, SaveListener<String> listener) {
        String userId = getCurrentUserId();
        if (userId == null) {
            listener.done(null, new BmobException(903, "用户未登录"));
            return;
        }
        Account cloud = Account.fromLocal(local);
        cloud.setUser(BmobPointerUtil.user(userId));
        if (local.getGroupId() != null) {
            cloud.setGroup(BmobPointerUtil.group(local.getGroupId()));
        }
        cloud.save(listener);
    }

    /** 更新账户 (异步) */
    public void updateAccount(com.example.my_project1.data.model.account.Account local, UpdateListener listener) {
        if (local.getObjectId() == null) {
            listener.done(new BmobException(904, "cloudId为空，无法更新账户"));
            return;
        }
        Account cloud = Account.fromLocal(local);
        cloud.setObjectId(local.getObjectId());
        if (local.getGroupId() != null) {
            cloud.setGroup(BmobPointerUtil.group(local.getGroupId()));
        }
        cloud.update(local.getObjectId(), listener);
    }

    /** 删除账户 (异步) */
    public void deleteAccount(String cloudId, UpdateListener listener) {
        if (cloudId == null) {
            listener.done(new BmobException(905, "cloudId为空，无法删除账户"));
            return;
        }
        Account cloud = new Account();
        cloud.setObjectId(cloudId);
        cloud.delete(listener);
    }

    /** 拉取当前用户的所有账户 (异步) */
    public void fetchAccounts(FindListener<Account> listener) {
        String userId = getCurrentUserId();
        if (userId == null) {
            listener.done(null, new BmobException(906, "用户未登录"));
            return;
        }
        BmobQuery<Account> query = new BmobQuery<>();
        query.addWhereEqualTo("user", BmobPointerUtil.user(userId));
        query.include("group");
        query.order("createdAt");
        query.findObjects(listener);
    }

    /** 同步上传单个账户 (阻塞) */
    public boolean uploadAccountSync(com.example.my_project1.data.model.account.Account local) {
        try {
            String userId = getCurrentUserId();
            if (userId == null) return false;

            Account cloud = Account.fromLocal(local);
            cloud.setUser(BmobPointerUtil.user(userId));

            // 关联账户组 (确保使用 objectId)
            if (local.getGroupId() != null && !local.getGroupId().isEmpty()) {
                cloud.setGroup(BmobPointerUtil.group(local.getGroupId()));
            }

            String cloudId = local.getObjectId();
            if (cloudId == null || cloudId.isEmpty()) {
                // 🔴 关键逻辑：如果 group 还是本地临时 ID，上传会失败。
                // 但 Worker 逻辑保证了 Group 先同步，所以此时 groupId 应该是 objectId。
                cloudId = cloud.saveSync();
                if (cloudId == null || cloudId.isEmpty()) return false;
                local.setObjectId(cloudId);
            } else {
                cloud.setObjectId(cloudId);
                cloud.updateSync(cloudId);
            }

            local.setSyncState(SyncState.SYNCED);
            if (db != null) {
                db.accountDao().update(local);
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
