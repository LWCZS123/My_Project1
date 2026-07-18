package com.example.my_project1.data.repository.account;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.work.WorkManager;

import com.example.my_project1.data.dao.AccountDao;
import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.remote.model.cloudaccount.Account;
import com.example.my_project1.data.remote.model.cloudaccount.AccountGroup;
import com.example.my_project1.data.remote.model.cloudaccount.BmobAccountApiImpl;
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.utils.DateConvertUtil;
import com.example.my_project1.work.AccountSyncWorker;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import cn.bmob.v3.exception.BmobException;
import cn.bmob.v3.listener.FindListener;

/**
 * AccountRepository (修复同步问题版)
 * -------------------------------------------------------
 * 🔧 核心修复：
 * 1. ✅ 同步前优先上传本地待同步数据
 * 2. ✅ 避免云端数据覆盖本地未上传的修改
 * 3. ✅ 账户余额更新时立即触发同步
 * 4. ✅ 下载时跳过本地待同步的数据
 */
public class AccountRepository {

    private static final String TAG = "AccountRepo";

    private final AccountDao accountDao;
    private final WorkManager workManager;
    private final Context context;

    public AccountRepository(Context context) {
        this.context = context.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(context);
        accountDao = db.accountDao();
        workManager = WorkManager.getInstance(context);
    }

    /** 统一回调：返回成功/失败 + 可选描述信息（用于 UI 提示） */
    public interface ResultCallback {
        void onResult(boolean success, String message);
    }

    // -------------------------------
    // 线程与调度工具
    // -------------------------------

    private void runInBackground(Runnable task) {
        AppExecutors.get().diskIO().execute(task);
    }

    private void postOnMain(Runnable action) {
        AppExecutors.get().mainThread().execute(action);
    }

    // -------------------------------
    // 🟢 账户组操作
    // -------------------------------

    public void insertAccountGroup(com.example.my_project1.data.model.account.AccountGroup group,
                                   ResultCallback callback) {
        runInBackground(new Runnable() {
            @Override
            public void run() {
                try {
                    com.example.my_project1.data.model.account.AccountGroup existing =
                            accountDao.getGroupByName(group.getUserId(), group.getName());

                    if (existing != null) {
                        Log.w(TAG, "⚠️ 插入失败，账户组重名：" + group.getName());
                        postOnMain(new Runnable() {
                            @Override
                            public void run() {
                                callback.onResult(false, "该账户组名称已存在");
                            }
                        });
                        return;
                    }

                    Date now = new Date();
                    group.setCreatedAt(now);
                    group.setUpdatedAt(now);
                    group.setSyncState(SyncState.TO_CREATE);
                    group.setAccountCount(0);
                    long insertedId = accountDao.insertGroup(group);

                    if (insertedId > 0) {
                        Log.i(TAG, "✅ 插入账户组成功: " + group.getName() + " (Room ID: " + insertedId + ")");
                        enqueueSync();
                        postOnMain(new Runnable() {
                            @Override
                            public void run() {
                                callback.onResult(true, "添加成功");
                            }
                        });
                    } else {
                        Log.e(TAG, "❌ 插入失败,返回ID: " + insertedId);
                        postOnMain(new Runnable() {
                            @Override
                            public void run() {
                                callback.onResult(false, "插入失败");
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "❌ 插入账户组异常：", e);
                    postOnMain(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(false, "插入失败，请稍后重试");
                        }
                    });
                }
            }
        });
    }

    public void updateAccountGroup(com.example.my_project1.data.model.account.AccountGroup group, ResultCallback callback) {
        runInBackground(new Runnable() {
            @Override
            public void run() {
                try {
                    com.example.my_project1.data.model.account.AccountGroup existingGroup =
                            accountDao.getGroupByCloudId(group.getObjectId());

                    if (existingGroup == null) {
                        Log.e(TAG, "❌ 更新失败,数据库中找不到该账户组: " + group.getObjectId());
                        postOnMain(new Runnable() {
                            @Override
                            public void run() {
                                callback.onResult(false, "账户组不存在");
                            }
                        });
                        return;
                    }

                    com.example.my_project1.data.model.account.AccountGroup duplicateCheck =
                            accountDao.getGroupByName(group.getUserId(), group.getName());

                    if (duplicateCheck != null && duplicateCheck.getId() != existingGroup.getId()) {
                        Log.w(TAG, "⚠️ 更新失败,账户组名称重复: " + group.getName());
                        postOnMain(new Runnable() {
                            @Override
                            public void run() {
                                callback.onResult(false, "该账户组名称已存在");
                            }
                        });
                        return;
                    }

                    existingGroup.setName(group.getName());
                    existingGroup.setIconUrl(group.getIconUrl());
                    existingGroup.setUserId(group.getUserId());
                    existingGroup.setUpdatedAt(new java.util.Date());
                    existingGroup.setSyncState(SyncState.TO_UPDATE);

                    if (group.getAccountCount() > 0) {
                        existingGroup.setAccountCount(group.getAccountCount());
                    }

                    int rowsAffected = accountDao.updateGroup(existingGroup);

                    if (rowsAffected > 0) {
                        Log.i(TAG, "✅ 更新账户组成功: " + existingGroup.getName());
                        enqueueSync();
                        postOnMain(new Runnable() {
                            @Override
                            public void run() {
                                callback.onResult(true, "更新成功");
                            }
                        });
                    } else {
                        Log.e(TAG, "❌ 更新失败,没有行被更新");
                        postOnMain(new Runnable() {
                            @Override
                            public void run() {
                                callback.onResult(false, "更新失败");
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "❌ 更新账户组异常：", e);
                    postOnMain(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(false, "更新失败，请稍后重试");
                        }
                    });
                }
            }
        });
    }

    public void deleteAccountGroup(com.example.my_project1.data.model.account.AccountGroup group,
                                   ResultCallback callback) {
        runInBackground(new Runnable() {
            @Override
            public void run() {
                try {
                    com.example.my_project1.data.model.account.AccountGroup existing =
                            accountDao.getGroupByCloudId(group.getObjectId());

                    if (existing == null) {
                        Log.w(TAG, "⚠️ 账户组不存在，可能已删除: " + group.getObjectId());
                        postOnMain(new Runnable() {
                            @Override
                            public void run() {
                                callback.onResult(true, "账户组已删除");
                            }
                        });
                        return;
                    }

                    List<com.example.my_project1.data.model.account.Account> accounts =
                            accountDao.getAccountsByGroupIdSync(existing.getObjectId());

                    int activeCount = 0;
                    if (accounts != null) {
                        for (com.example.my_project1.data.model.account.Account acc : accounts) {
                            if (acc.getSyncState() != SyncState.TO_DELETE) activeCount++;
                        }
                    }

                    if (activeCount > 0) {
                        Log.e(TAG, "❌ 删除失败：组下还有 " + activeCount + " 个账户");
                        final int count = activeCount;
                        postOnMain(new Runnable() {
                            @Override
                            public void run() {
                                callback.onResult(false, "账户组下还有账户，请先移动或删除账户");
                            }
                        });
                        return;
                    }

                    existing.setSyncState(SyncState.TO_DELETE);
                    existing.setUpdatedAt(new java.util.Date());

                    int updated = accountDao.updateGroup(existing);

                    if (updated > 0) {
                        Log.i(TAG, "✅ 账户组标记删除成功: " + existing.getName());
                        enqueueSync();
                        postOnMain(new Runnable() {
                            @Override
                            public void run() {
                                callback.onResult(true, "删除成功");
                            }
                        });
                    } else {
                        Log.e(TAG, "❌ 标记删除失败");
                        postOnMain(new Runnable() {
                            @Override
                            public void run() {
                                callback.onResult(false, "删除失败，请重试");
                            }
                        });
                    }

                } catch (Exception e) {
                    Log.e(TAG, "❌ 删除账户组异常:", e);
                    postOnMain(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(false, "删除失败: " + e.getMessage());
                        }
                    });
                }
            }
        });
    }

    public void confirmDeleteGroupAndAccounts(com.example.my_project1.data.model.account.AccountGroup group, ResultCallback callback) {
        runInBackground(new Runnable() {
            @Override
            public void run() {
                try {
                    long now = System.currentTimeMillis();
                    // 1. 批量标记账户为删除状态
                    accountDao.markAccountsAsDeletedByGroup(group.getObjectId(), now);
                    
                    // 2. 标记账户组为删除状态
                    group.setSyncState(SyncState.TO_DELETE);
                    group.setUpdatedAt(new Date(now));
                    accountDao.updateGroup(group);
                    
                    enqueueSync();

                    Log.i(TAG, "🗑 批量标记删除账户组及其账户：" + group.getName());
                    postOnMain(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(true, null);
                        }
                    });

                } catch (Exception e) {
                    Log.e(TAG, "❌ 批量确认删除失败：", e);
                    postOnMain(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(false, "删除失败，请稍后重试");
                        }
                    });
                }
            }
        });
    }

    public void moveAccountsToGroup(String oldGroupId, String newGroupId, ResultCallback callback) {
        runInBackground(new Runnable() {
            @Override
            public void run() {
                try {
                    com.example.my_project1.data.model.account.AccountGroup newGroup =
                            accountDao.getGroupByCloudId(newGroupId);

                    if (newGroup == null) {
                        Log.e(TAG, "❌ 新账户组不存在: " + newGroupId);
                        postOnMain(new Runnable() {
                            @Override
                            public void run() {
                                callback.onResult(false, "目标账户组不存在");
                            }
                        });
                        return;
                    }

                    List<com.example.my_project1.data.model.account.Account> accounts =
                            accountDao.getAccountsByGroupIdSync(oldGroupId);

                    if (accounts == null || accounts.isEmpty()) {
                        Log.w(TAG, "⚠️ 旧组下没有账户需要移动");
                        postOnMain(new Runnable() {
                            @Override
                            public void run() {
                                callback.onResult(true, "无需移动账户");
                            }
                        });
                        return;
                    }

                    int movedCount = 0;
                    for (com.example.my_project1.data.model.account.Account acc : accounts) {
                        if (acc.getSyncState() == SyncState.TO_DELETE) {
                            continue;
                        }

                        String oldGroup = acc.getGroupId();
                        acc.setGroupId(newGroupId);
                        acc.setSyncState(SyncState.TO_UPDATE);
                        acc.setUpdatedAt(new Date());

                        int updated = accountDao.update(acc);
                        if (updated > 0) {
                            movedCount++;
                            Log.d(TAG, "📦 移动账户:" + acc.getName() + " [" + oldGroup + " → " + newGroupId + "]");
                        }
                    }

                    updateGroupAccountCountInternal(oldGroupId);
                    updateGroupAccountCountInternal(newGroupId);
                    enqueueSync();

                    Log.i(TAG, "✅ 账户移动完成:共移动 " + movedCount + " 个账户");
                    final int finalMovedCount = movedCount;
                    postOnMain(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(true, "已移动 " + finalMovedCount + " 个账户");
                        }
                    });

                } catch (Exception e) {
                    Log.e(TAG, "❌ 移动账户失败:", e);
                    postOnMain(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(false, "移动失败:" + e.getMessage());
                        }
                    });
                }
            }
        });
    }

    public void moveSingleAccountToGroup(String accountId, String newGroupId, ResultCallback callback) {
        runInBackground(new Runnable() {
            @Override
            public void run() {
                try {
                    com.example.my_project1.data.model.account.AccountGroup newGroup =
                            accountDao.getGroupByCloudId(newGroupId);

                    if (newGroup == null) {
                        Log.e(TAG, "❌ 目标账户组不存在: " + newGroupId);
                        postOnMain(new Runnable() {
                            @Override
                            public void run() {
                                callback.onResult(false, "目标账户组不存在");
                            }
                        });
                        return;
                    }

                    com.example.my_project1.data.model.account.Account account =
                            accountDao.getAccountByCloudId(accountId);

                    if (account == null) {
                        Log.e(TAG, "❌ 账户不存在: " + accountId);
                        postOnMain(new Runnable() {
                            @Override
                            public void run() {
                                callback.onResult(false, "账户不存在");
                            }
                        });
                        return;
                    }

                    String oldGroupId = account.getGroupId();
                    account.setGroupId(newGroupId);
                    account.setSyncState(SyncState.TO_UPDATE);
                    account.setUpdatedAt(new Date());

                    int updated = accountDao.update(account);
                    if (updated <= 0) {
                        Log.e(TAG, "❌ 更新失败：" + account.getName());
                        postOnMain(new Runnable() {
                            @Override
                            public void run() {
                                callback.onResult(false, "移动失败");
                            }
                        });
                        return;
                    }

                    Log.d(TAG, "📦 移动账户: " + account.getName()
                            + " [" + oldGroupId + " → " + newGroupId + "]");

                    updateGroupAccountCountInternal(oldGroupId);
                    updateGroupAccountCountInternal(newGroupId);
                    enqueueSync();

                    Log.i(TAG, "✅ 单个账户移动完成: " + account.getName());

                    postOnMain(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(true, "移动成功");
                        }
                    });

                } catch (Exception e) {
                    Log.e(TAG, "❌ 移动单个账户失败:", e);
                    postOnMain(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(false, "移动失败:" + e.getMessage());
                        }
                    });
                }
            }
        });
    }

    public String getGroupIdByAccountId(String accountId) {
        try {
            com.example.my_project1.data.model.account.Account account =
                    accountDao.getAccountByCloudId(accountId);
            return account != null ? account.getGroupId() : null;
        } catch (Exception e) {
            Log.e(TAG, "❌ 获取账户所属组失败: " + accountId, e);
            return null;
        }
    }

    public void getAccountByGroupName(String groupId, ResultCallback callback) {
        runInBackground(new Runnable() {
            @Override
            public void run() {
                try {
                    com.example.my_project1.data.model.account.AccountGroup group =
                            accountDao.getGroupByCloudId(groupId);

                    final String groupName = group != null ? group.getName() : null;

                    postOnMain(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(true, groupName);
                        }
                    });

                } catch (Exception e) {
                    Log.e(TAG, "❌ 获取账户所属组失败: " + groupId, e);
                    postOnMain(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(false, null);
                        }
                    });
                }
            }
        });
    }

    public com.example.my_project1.data.model.account.Account getAccountByIdSync(String accountId) {
        try {
            return accountDao.getAccountById(accountId);
        } catch (Exception e) {
            Log.e(TAG, "❌ 获取账户失败: " + accountId, e);
            return null;
        }
    }

    public com.example.my_project1.data.model.account.Account getAccountByLocalIdSync(long localId) {
        try {
            return accountDao.getAccountByLocalId(localId);
        } catch (Exception e) {
            Log.e(TAG, "❌ 获取本地账户失败: " + localId, e);
            return null;
        }
    }

    public void getAccountNameById(String accountId, ResultCallback callback) {
        runInBackground(new Runnable() {
            @Override
            public void run() {
                try {
                    com.example.my_project1.data.model.account.Account account =
                            accountDao.getAccountById(accountId);

                    final String accountName = account != null ? account.getName() : null;

                    Log.d(TAG, "✅ 查询账户名称: accountId=" + accountId + ", name=" + accountName);
                    postOnMain(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(true, accountName);
                        }
                    });

                } catch (Exception e) {
                    Log.e(TAG, "❌ 获取账户名称失败: " + accountId, e);
                    postOnMain(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(false, null);
                        }
                    });
                }
            }
        });
    }

    public LiveData<List<com.example.my_project1.data.model.account.AccountGroup>> getAccountGroups(String userId) {
        return accountDao.getGroupsByUser(userId);
    }

    public LiveData<com.example.my_project1.data.model.account.Account> getAccountById(String accountId) {
        return accountDao.getAccountByIdLive(accountId);
    }

    // -------------------------------
    // 🟡 账户操作
    // ------------------------------


    public com.example.my_project1.data.model.account.AccountGroup getAccountGroupByIdSync(String groupId) {
        return accountDao.getById(groupId);
    }
    public void insertAccount(com.example.my_project1.data.model.account.Account account, ResultCallback callback) {
        runInBackground(new Runnable() {
            @Override
            public void run() {
                try {
                    Date now = new Date();
                    account.setCreatedAt(now);
                    account.setUpdatedAt(now);
                    account.setSyncState(SyncState.TO_CREATE);
                    accountDao.insertAccount(account);
                    updateGroupAccountCountInternal(account.getGroupId());
                    enqueueSync();
                    Log.i(TAG, "✅ 插入账户：" + account.getName());
                    postOnMain(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(true, null);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "❌ 插入账户异常：", e);
                    postOnMain(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(false, "新增账户失败");
                        }
                    });
                }
            }
        });
    }

    /**
     * 🔴 关键修复：更新账户时立即标记为待同步
     */
    public void updateAccount(com.example.my_project1.data.model.account.Account account, ResultCallback callback) {
        runInBackground(new Runnable() {
            @Override
            public void run() {
                try {
                    com.example.my_project1.data.model.account.Account existingAccount = null;
                    if (account.getObjectId() != null && !account.getObjectId().isEmpty()) {
                        existingAccount = accountDao.getAccountByCloudId(account.getObjectId());
                    } else if (account.getId() > 0) {
                        existingAccount = accountDao.getAccountByLocalId(account.getId());
                    }

                    if (existingAccount == null) {
                        Log.e(TAG, "❌ 更新失败，账户不存在: " + (account.getObjectId() != null ? account.getObjectId() : account.getId()));
                        postOnMain(new Runnable() {
                            @Override
                            public void run() {
                                callback.onResult(false, "账户不存在");
                            }
                        });
                        return;
                    }

                    String oldGroupId = existingAccount.getGroupId();

                    existingAccount.setName(account.getName());
                    existingAccount.setBalance(account.getBalance());
                    existingAccount.setIconUrl(account.getIconUrl());
                    existingAccount.setRemark(account.getRemark());
                    existingAccount.setCardNumber(account.getCardNumber());
                    existingAccount.setCreditLimit(account.getCreditLimit());
                    existingAccount.setGroupId(account.getGroupId());
                    existingAccount.setAccountType(account.getAccountType());
                    existingAccount.setCategory(account.getCategory());
                    existingAccount.setBillingDay(account.getBillingDay());
                    existingAccount.setRepaymentDay(account.getRepaymentDay());
                    existingAccount.setIncludeBill(account.isIncludeBill());
                    existingAccount.setIncludeInTotal(account.isIncludeInTotal());
                    existingAccount.setCanBeSelected(account.isCanBeSelected());
                    existingAccount.setUpdatedAt(new Date());

                    // 🔴 关键：如果已同步，标记为需要更新
                    if (existingAccount.getSyncState() == SyncState.SYNCED) {
                        existingAccount.setSyncState(SyncState.TO_UPDATE);
                    }

                    accountDao.update(existingAccount);

                    if (oldGroupId != null && !oldGroupId.equals(account.getGroupId())) {
                        updateGroupAccountCountInternal(oldGroupId);
                        updateGroupAccountCountInternal(account.getGroupId());
                    }

                    // 🔴 关键：立即触发同步
                    enqueueSync();

                    Log.i(TAG, "🔄 更新账户：" + account.getName() + ", 状态: " + existingAccount.getSyncState());
                    postOnMain(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(true, "更新成功");
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "❌ 更新账户异常：", e);
                    postOnMain(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(false, "更新失败");
                        }
                    });
                }
            }
        });
    }

    public void deleteAccount(com.example.my_project1.data.model.account.Account account, ResultCallback callback) {
        runInBackground(new Runnable() {
            @Override
            public void run() {
                try {
                    com.example.my_project1.data.model.account.Account existingAccount = null;
                    
                    if (account.getObjectId() != null && !account.getObjectId().isEmpty()) {
                        existingAccount = accountDao.getAccountByCloudId(account.getObjectId());
                    } else if (account.getId() > 0) {
                        existingAccount = accountDao.getAccountByLocalId(account.getId());
                    }

                    if (existingAccount == null) {
                        Log.w(TAG, "⚠️ 账户不存在，可能已被删除: " + account.getName());
                        postOnMain(new Runnable() {
                            @Override
                            public void run() {
                                callback.onResult(true, "账户已删除");
                            }
                        });
                        return;
                    }

                    String groupId = existingAccount.getGroupId();

                    if (existingAccount.getObjectId() == null || existingAccount.getObjectId().isEmpty()) {
                        // 如果从未同步过，直接物理删除
                        int deleted = accountDao.delete(existingAccount);
                        if (deleted > 0) {
                            Log.i(TAG, "✅ 物理删除本地账户: " + account.getName());
                            if (groupId != null && !groupId.isEmpty()) updateGroupAccountCountInternal(groupId);
                            postOnMain(() -> callback.onResult(true, "删除成功"));
                        } else {
                            postOnMain(() -> callback.onResult(false, "删除失败"));
                        }
                        return;
                    }

                    existingAccount.setSyncState(SyncState.TO_DELETE);
                    existingAccount.setUpdatedAt(new java.util.Date());

                    int rowsAffected = accountDao.update(existingAccount);

                    if (rowsAffected > 0) {
                        Log.i(TAG, "✅ 账户标记删除成功: " + account.getName());

                        if (groupId != null && !groupId.isEmpty()) {
                            updateGroupAccountCountInternal(groupId);
                        }

                        enqueueSync();

                        postOnMain(new Runnable() {
                            @Override
                            public void run() {
                                callback.onResult(true, "删除成功");
                            }
                        });
                    } else {
                        Log.e(TAG, "❌ 账户标记删除失败");
                        postOnMain(new Runnable() {
                            @Override
                            public void run() {
                                callback.onResult(false, "删除失败，请重试");
                            }
                        });
                    }

                } catch (Exception e) {
                    Log.e(TAG, "❌ 删除账户异常:", e);
                    postOnMain(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(false, "删除失败: " + e.getMessage());
                        }
                    });
                }
            }
        });
    }

    public LiveData<List<com.example.my_project1.data.model.account.Account>> getAccountsByGroup(String groupId) {
        return accountDao.getAccountsByGroup(groupId);
    }

    public LiveData<List<com.example.my_project1.data.model.account.Account>> getAccountsByUser(String userId) {
        return accountDao.getAccountsByUser(userId);
    }

    // -------------------------------
    // 内部工具：更新组计数
    // -------------------------------
    private void updateGroupAccountCountInternal(String groupId) {
        if (groupId == null || groupId.isEmpty()) {
            Log.w(TAG, "⚠️ groupId 为空,跳过计数更新");
            return;
        }

        try {
            List<com.example.my_project1.data.model.account.Account> accounts =
                    accountDao.getAccountsByGroupIdSync(groupId);

            int actualCount = 0;
            if (accounts != null) {
                for (com.example.my_project1.data.model.account.Account acc : accounts) {
                    if (acc.getSyncState() != SyncState.TO_DELETE) {
                        actualCount++;
                    }
                }
            }

            com.example.my_project1.data.model.account.AccountGroup group =
                    accountDao.getGroupByCloudId(groupId);

            if (group != null) {
                if (group.getAccountCount() != actualCount) {
                    group.setAccountCount(actualCount);
                    group.setSyncState(SyncState.TO_UPDATE);
                    accountDao.updateGroup(group);
                    enqueueSync();
                    Log.d(TAG, "📊 更新账户组计数: " + group.getName() + " = " + actualCount);
                }
            } else {
                Log.w(TAG, "⚠️ 未找到账户组: " + groupId);
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ 更新账户组计数失败", e);
        }
    }

    // -------------------------------
    // ☁️ 云端同步（修复版）
    // -------------------------------
    private void enqueueSync() {
        AccountSyncWorker.enqueue(context);
    }

    /**
     * 🔧 关键修复：从云端同步
     * 改进策略：
     * 1. 先上传本地待同步的数据
     * 2. 再从云端下载数据
     * 3. 下载时跳过本地有待同步修改的数据
     */
    public void syncFromAccountGroupCloud(ResultCallback callback) {
        AppExecutors.get().networkIO().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "========== 开始账户同步 ==========");

                    // 🔴 步骤1：先上传本地待同步的数据
                    uploadPendingChanges();

                    // 🔴 步骤2：再从云端下载
                    downloadFromCloud(callback);

                    Log.d(TAG, "========== 账户同步完成 ==========");

                } catch (Exception e) {
                    Log.e(TAG, "❌ 同步失败: " + e.getMessage(), e);
                    postOnMain(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(false, e.getMessage());
                        }
                    });
                }
            }
        });
    }

    /**
     * 🔴 新增：上传本地待同步的修改（含删除）
     */
    private void uploadPendingChanges() {
        try {
            BmobAccountApiImpl api = new BmobAccountApiImpl(context);

            // 1. 处理待删除的账户（优先处理删除，防止外键约束冲突）
            List<com.example.my_project1.data.model.account.Account> pendingAccounts =
                    accountDao.getPendingSyncAccounts();
            
            if (pendingAccounts != null) {
                for (com.example.my_project1.data.model.account.Account account : pendingAccounts) {
                    if (account.getSyncState() == SyncState.TO_DELETE) {
                        String objectId = account.getObjectId();
                        if (objectId != null && !objectId.isEmpty()) {
                            // 同步删除（阻塞直到完成或超时）
                            boolean success = deleteAccountFromCloudSync(api, objectId);
                            if (success) {
                                accountDao.delete(account);
                                Log.d(TAG, "✅ 云端及本地删除账户成功: " + account.getName());
                            }
                        } else {
                            accountDao.delete(account);
                        }
                    }
                }
            }

            // 2. 处理待删除的账户组
            List<com.example.my_project1.data.model.account.AccountGroup> pendingGroups =
                    accountDao.getPendingSyncGroups();
            
            if (pendingGroups != null) {
                for (com.example.my_project1.data.model.account.AccountGroup group : pendingGroups) {
                    if (group.getSyncState() == SyncState.TO_DELETE) {
                        String objectId = group.getObjectId();
                        if (objectId != null && !objectId.isEmpty()) {
                            boolean success = deleteGroupFromCloudSync(api, objectId);
                            if (success) {
                                accountDao.deleteGroup(group);
                                Log.d(TAG, "✅ 云端及本地删除账户组成功: " + group.getName());
                            }
                        } else {
                            accountDao.deleteGroup(group);
                        }
                    }
                }
            }

            // 3. 处理待创建/更新的账户组
            pendingGroups = accountDao.getPendingSyncGroups();
            if (pendingGroups != null) {
                for (com.example.my_project1.data.model.account.AccountGroup group : pendingGroups) {
                    if (group.getSyncState() != SyncState.TO_DELETE) {
                        boolean success = api.uploadAccountGroupSync(group);
                        if (success) {
                            group.setSyncState(SyncState.SYNCED);
                            accountDao.updateGroup(group);
                        }
                    }
                }
            }

            // 4. 处理待创建/更新的账户
            pendingAccounts = accountDao.getPendingSyncAccounts();
            if (pendingAccounts != null) {
                for (com.example.my_project1.data.model.account.Account account : pendingAccounts) {
                    if (account.getSyncState() != SyncState.TO_DELETE) {
                        boolean success = api.uploadAccountSync(account);
                        if (success) {
                            account.setSyncState(SyncState.SYNCED);
                            accountDao.update(account);
                            Log.d(TAG, "✅ 上传账户成功: " + account.getName());
                        }
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ 上传待同步数据失败: " + e.getMessage(), e);
        }
    }

    /** 辅助方法：同步删除云端账户 */
    private boolean deleteAccountFromCloudSync(BmobAccountApiImpl api, String objectId) {
        final boolean[] result = {false};
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        api.deleteAccount(objectId, new cn.bmob.v3.listener.UpdateListener() {
            @Override
            public void done(cn.bmob.v3.exception.BmobException e) {
                if (e == null || e.getErrorCode() == 101) { // 101 为对象不存在
                    result[0] = true;
                }
                latch.countDown();
            }
        });
        try { latch.await(15, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception ignored) {}
        return result[0];
    }

    /** 辅助方法：同步删除云端账户组 */
    private boolean deleteGroupFromCloudSync(BmobAccountApiImpl api, String objectId) {
        final boolean[] result = {false};
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        api.deleteAccountGroup(objectId, new cn.bmob.v3.listener.UpdateListener() {
            @Override
            public void done(cn.bmob.v3.exception.BmobException e) {
                if (e == null || e.getErrorCode() == 101) {
                    result[0] = true;
                }
                latch.countDown();
            }
        });
        try { latch.await(15, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception ignored) {}
        return result[0];
    }

    /**
     * 🔴 修改：从云端下载数据（跳过待同步的数据）
     */
    private void downloadFromCloud(final ResultCallback callback) {
        BmobAccountApiImpl api = new BmobAccountApiImpl();
        api.fetchAccountGroups(new FindListener<AccountGroup>() {
            @Override
            public void done(List<AccountGroup> cloudGroups, BmobException e) {
                if (e != null) {
                    Log.e(TAG, "❌ 拉取账户组失败：" + e.getMessage());
                    postOnMain(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(false, e.getMessage());
                        }
                    });
                    return;
                }

                if (cloudGroups == null) cloudGroups = new ArrayList<>();

                final List<AccountGroup> finalCloudGroups = cloudGroups;
                AppExecutors.get().diskIO().execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            List<com.example.my_project1.data.model.account.AccountGroup> localGroups = accountDao.getAllGroupsSync();

                            List<String> cloudGroupIds = new ArrayList<>();
                            for (AccountGroup cloud : finalCloudGroups) cloudGroupIds.add(cloud.getObjectId());

                            // 删除本地多余的组
                            for (com.example.my_project1.data.model.account.AccountGroup localGroup : localGroups) {
                                if (!cloudGroupIds.contains(localGroup.getObjectId())) {
                                    // 🔴 跳过待同步的数据
                                    if (localGroup.getSyncState() != SyncState.SYNCED) {
                                        Log.d(TAG, "⏭️ 跳过待同步的账户组: " + localGroup.getName());
                                        continue;
                                    }
                                    Log.d(TAG, "🗑️ 删除本地账户组（云端已删除）: " + localGroup.getName());
                                    accountDao.deleteAccountsByGroup(localGroup.getObjectId());
                                    accountDao.deleteGroup(localGroup);
                                }
                            }

                            // 更新/插入云端组
                            for (AccountGroup cloud : finalCloudGroups) {
                                com.example.my_project1.data.model.account.AccountGroup local = accountDao.getGroupByCloudId(cloud.getObjectId());

                                // 🔴 跳过待同步的数据
                                if (local != null && local.getSyncState() != SyncState.SYNCED) {
                                    Log.d(TAG, "⏭️ 跳过待同步的账户组: " + local.getName());
                                    continue;
                                }

                                if (local == null) {
                                    com.example.my_project1.data.model.account.AccountGroup newGroup = new com.example.my_project1.data.model.account.AccountGroup();
                                    newGroup.setObjectId(cloud.getObjectId());
                                    if (cloud.getUser() != null) newGroup.setUserId(cloud.getUser().getObjectId());
                                    newGroup.setName(cloud.getName());
                                    newGroup.setIconUrl(cloud.getIconUrl());
                                    newGroup.setSyncState(SyncState.SYNCED);
                                    accountDao.insertGroup(newGroup);
                                    Log.d(TAG, "🆕 新增账户组：" + newGroup.getName());
                                } else {
                                    local.setName(cloud.getName());
                                    local.setIconUrl(cloud.getIconUrl());
                                    local.setAccountCount(cloud.getAccountCount());
                                    local.setCreatedAt(DateConvertUtil.safeConvertToDate(cloud.getCreatedAt()));
                                    local.setUpdatedAt(DateConvertUtil.safeConvertToDate(cloud.getUpdatedAt()));
                                    local.setSyncState(SyncState.SYNCED);
                                    accountDao.updateGroup(local);
                                    Log.d(TAG, "🔄 更新账户组：" + local.getName());
                                }
                            }

                            Log.d(TAG, "✅ 账户组同步完成，开始同步账户");

                            // 同步账户
                            List<String> cloudGroupIdsCopy = new ArrayList<>(cloudGroupIds);
                            syncFromAccountsCloud(cloudGroupIdsCopy, callback);

                        } catch (Exception ex) {
                            Log.e(TAG, "❌ 同步账户组失败", ex);
                            postOnMain(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onResult(false, "同步失败");
                                }
                            });
                        }
                    }
                });
            }
        });
    }

    /**
     * 🔴 修改：同步账户（跳过待同步的数据）
     */
    private void syncFromAccountsCloud(List<String> cloudGroupIds, final ResultCallback callback) {
        BmobAccountApiImpl api = new BmobAccountApiImpl();
        api.fetchAccounts(new FindListener<Account>() {
            @Override
            public void done(List<Account> cloudAccounts, BmobException e) {
                if (e != null) {
                    Log.e(TAG, "❌ 拉取账户失败：" + e.getMessage());
                    postOnMain(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(false, e.getMessage());
                        }
                    });
                    return;
                }

                if (cloudAccounts == null) cloudAccounts = new ArrayList<>();

                final List<Account> finalCloudAccounts = cloudAccounts;
                AppExecutors.get().diskIO().execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            List<com.example.my_project1.data.model.account.Account> localAccounts = accountDao.getAllAccountsSync();

                            List<String> cloudAccountIds = new ArrayList<>();
                            for (Account cloud : finalCloudAccounts) cloudAccountIds.add(cloud.getObjectId());

                            // 删除本地多余账户
                            for (com.example.my_project1.data.model.account.Account localAccount : localAccounts) {
                                if (!cloudAccountIds.contains(localAccount.getObjectId())) {
                                    // 🔴 跳过待同步的数据
                                    if (localAccount.getSyncState() != SyncState.SYNCED) {
                                        Log.d(TAG, "⏭️ 跳过待同步的账户: " + localAccount.getName());
                                        continue;
                                    }
                                    Log.d(TAG, "🗑️ 删除本地账户（云端已删除）: " + localAccount.getName());
                                    accountDao.delete(localAccount);
                                }
                            }

                            // 更新/插入云端账户
                            for (Account cloud : finalCloudAccounts) {
                                com.example.my_project1.data.model.account.Account local = accountDao.getAccountByCloudId(cloud.getObjectId());

                                // 🔴 关键：跳过待同步的账户
                                if (local != null && local.getSyncState() != SyncState.SYNCED) {
                                    Log.d(TAG, "⏭️ 跳过待同步的账户: " + local.getName() +
                                            ", 状态: " + local.getSyncState() +
                                            ", 本地余额: " + local.getBalance());
                                    continue;
                                }

                                if (local == null) {
                                    com.example.my_project1.data.model.account.Account acc = new com.example.my_project1.data.model.account.Account();
                                    acc.setObjectId(cloud.getObjectId());
                                    acc.setGroupId(cloud.getGroupId());
                                    acc.setUserId(cloud.getUserId());
                                    acc.setName(cloud.getName());
                                    acc.setBalance(cloud.getBalance());
                                    acc.setIconUrl(cloud.getIconUrl());
                                    acc.setRemark(cloud.getRemark());
                                    acc.setCardNumber(cloud.getCardNumber());
                                    acc.setCreditLimit(cloud.getCreditLimit());
                                    acc.setAccountType(cloud.getAccountType());
                                    acc.setCategory(cloud.getCategory());
                                    acc.setBillingDay(cloud.getBillingDay() != null ? cloud.getBillingDay() : 0);
                                    acc.setRepaymentDay(cloud.getRepaymentDay() != null ? cloud.getRepaymentDay() : 0);
                                    acc.setIncludeBill(cloud.getIncludeBill() != null ? cloud.getIncludeBill() : false);
                                    acc.setIncludeInTotal(cloud.getIncludeInTotal() != null ? cloud.getIncludeInTotal() : true);
                                    acc.setCanBeSelected(cloud.getCanBeSelected() != null ? cloud.getCanBeSelected() : true);
                                    acc.setSyncState(SyncState.SYNCED);
                                    accountDao.insertAccount(acc);
                                    Log.d(TAG, "🆕 新增账户：" + acc.getName());
                                } else {
                                    local.setGroupId(cloud.getGroupId());
                                    local.setName(cloud.getName());
                                    local.setBalance(cloud.getBalance());
                                    local.setIconUrl(cloud.getIconUrl());
                                    local.setCredit(cloud.getIsCredit() != null && cloud.getIsCredit());
                                    local.setRemark(cloud.getRemark());
                                    local.setCardNumber(cloud.getCardNumber());
                                    local.setCreditLimit(cloud.getCreditLimit());
                                    local.setAccountType(cloud.getAccountType());
                                    local.setCategory(cloud.getCategory());
                                    local.setBillingDay(cloud.getBillingDay() != null ? cloud.getBillingDay() : 0);
                                    local.setRepaymentDay(cloud.getRepaymentDay() != null ? cloud.getRepaymentDay() : 0);
                                    local.setIncludeBill(cloud.getIncludeBill() != null ? cloud.getIncludeBill() : false);
                                    local.setIncludeInTotal(cloud.getIncludeInTotal() != null ? cloud.getIncludeInTotal() : true);
                                    local.setCanBeSelected(cloud.getCanBeSelected() != null ? cloud.getCanBeSelected() : true);
                                    local.setCreatedAt(DateConvertUtil.safeConvertToDate(cloud.getCreatedAt()));
                                    local.setUpdatedAt(DateConvertUtil.safeConvertToDate(cloud.getUpdatedAt()));
                                    local.setSyncState(SyncState.SYNCED);
                                    accountDao.update(local);
                                    Log.d(TAG, "🔄 更新账户：" + local.getName() + ", 余额: " + local.getBalance());
                                }
                            }

                            Log.d(TAG, "✅ 完全同步成功！");
                            postOnMain(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onResult(true, null);
                                }
                            });

                        } catch (Exception ex) {
                            Log.e(TAG, "❌ 同步账户失败", ex);
                            postOnMain(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onResult(false, "同步失败");
                                }
                            });
                        }
                    }
                });
            }
        });
    }

    private boolean isInitializingDefaults = false;

    /**
     * 初始化系统预设账户组
     */
    public void initDefaultAccountGroups(String userId) {
        if (userId == null || isInitializingDefaults) return;
        isInitializingDefaults = true;
        
        runInBackground(() -> {
            try {
                // 1. 本地检查
                List<com.example.my_project1.data.model.account.AccountGroup> localGroups = 
                        accountDao.getAllGroupsSync();
                
                if (localGroups != null && !localGroups.isEmpty()) {
                    Log.d(TAG, "本地已有账户组，跳过系统预设初始化");
                    isInitializingDefaults = false;
                    return;
                }

                // 2. 云端检查
                BmobAccountApiImpl api = new BmobAccountApiImpl(context);
                api.fetchAccountGroups(new FindListener<AccountGroup>() {
                    @Override
                    public void done(List<AccountGroup> cloudGroups, BmobException e) {
                        if (e != null) {
                            Log.e(TAG, "❌ 查询云端账户组失败：" + e.getMessage());
                            isInitializingDefaults = false;
                            return;
                        }

                        if (cloudGroups != null && !cloudGroups.isEmpty()) {
                            Log.d(TAG, "云端已有账户组，开始下载同步...");
                            syncFromAccountGroupCloud((success, message) -> {
                                isInitializingDefaults = false;
                            });
                            return;
                        }

                        // 3. 云端也没有，初始化本地并标记为待同步
                        runInBackground(() -> {
                            try {
                                List<com.example.my_project1.data.model.account.AccountGroup> defaultGroups = 
                                        com.example.my_project1.data.provider.SystemAccountGroupProvider.getDefaultGroups(userId);

                                for (com.example.my_project1.data.model.account.AccountGroup group : defaultGroups) {
                                    group.setSyncState(SyncState.TO_CREATE);
                                    group.setCreatedAt(new Date());
                                    group.setUpdatedAt(new Date());
                                    accountDao.insertGroup(group);
                                }
                                Log.d(TAG, "✅ 已创建本地系统预设账户组，准备同步到云端");
                                enqueueSync();
                            } finally {
                                isInitializingDefaults = false;
                            }
                        });
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "❌ 初始化预设组异常", e);
                isInitializingDefaults = false;
            }
        });
    }
}