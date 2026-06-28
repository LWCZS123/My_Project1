package com.example.my_project1.ui.viewmodel.accountvm;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.account.AccountGroup;
import com.example.my_project1.data.repository.account.AccountRepository;
import com.example.my_project1.utils.ui.UiMessageLiveData;
import com.example.my_project1.utils.ui.UiState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.bmob.v3.BmobUser;
import io.reactivex.annotations.NonNull;

/**
 * AccountViewModel - 修复版本 (添加单个账户LiveData监听)
 * -------------------------------------------------------
 * 🔴 核心修复:
 * 1. 添加 getAccountById() 方法返回LiveData
 * 2. 支持实时监听单个账户的变化
 * 3. 当账户余额更新时,UI自动刷新
 */
public class AccountViewModel extends AndroidViewModel {

    private static final String TAG = "AccountViewModel";

    private UiMessageLiveData uiMessage = new UiMessageLiveData();
    public UiMessageLiveData getUiMessage() { return uiMessage; }

    // 🔴 改进：使用 Map 管理多个账户组的数据源
    private final Map<String, LiveData<List<Account>>> groupAccountsMap = new HashMap<>();
    private final Map<String, MediatorLiveData<List<Account>>> groupAccountsMediatorMap = new HashMap<>();

    // 🔴 新增：单个账户的LiveData缓存
    private final Map<String, LiveData<Account>> accountLiveDataMap = new HashMap<>();

    // 单个账户状态
    private final MediatorLiveData<UiState<Account>> _addAccountState = new MediatorLiveData<>();
    public LiveData<UiState<Account>> addAccountState = _addAccountState;

    // 观察账户组列表
    private final MediatorLiveData<UiState<List<AccountGroup>>> _groupState = new MediatorLiveData<>();
    public LiveData<UiState<List<AccountGroup>>> groupState = _groupState;

    // 单个账户组状态
    private final MediatorLiveData<UiState<AccountGroup>> _addGroupState = new MediatorLiveData<>();
    public LiveData<UiState<AccountGroup>> addGroupState = _addGroupState;

    private final AccountRepository repository;
    private LiveData<List<AccountGroup>> allGroups;
    private LiveData<List<Account>> allAccount;

    // 🔴 新增：用于通知特定组的账户更新
    private final MutableLiveData<Map<String, List<Account>>> _groupAccountsUpdate = new MutableLiveData<>();
    public LiveData<Map<String, List<Account>>> groupAccountsUpdate = _groupAccountsUpdate;

    public interface ResultCallback {
        void onResult(boolean success, String message);
    }

    public interface DeleteGroupCallback {
        void onNeedUserConfirm();
        void onDeleted();
    }

    public AccountViewModel(@NonNull Application application) {
        super(application);
        repository = new AccountRepository(application);
        String userId = BmobUser.getCurrentUser().getObjectId();
        allGroups = repository.getAccountGroups(userId);
    }

    /**
     * 同步获取账户（用于Activity初始化）
     */
    public Account getAccountByIdSync(String accountId) {
        return repository.getAccountByIdSync(accountId);
    }

    /**
     * 🔴 新增：获取单个账户的LiveData（支持实时监听）
     * 用于AccountDetailActivity实时监听余额变化
     */
    public LiveData<Account> getAccountById(String accountId) {
        if (!accountLiveDataMap.containsKey(accountId)) {
            LiveData<Account> accountLiveData = repository.getAccountById(accountId);
            accountLiveDataMap.put(accountId, accountLiveData);
            Log.d(TAG, "✅ 创建账户LiveData监听: " + accountId);
        }
        return accountLiveDataMap.get(accountId);
    }

    public LiveData<List<AccountGroup>> getAccountGroups() {
        if (allGroups == null) {
            Log.d(TAG,"首次进入无数据 → 自动加载账户组");
            loadAccountGroups(BmobUser.getCurrentUser().getObjectId());
        }
        return allGroups;
    }

    // ===================== 🔹 获取数据 =====================

    /**
     * 为指定的账户组创建或获取 LiveData
     */
    public LiveData<List<Account>> getAccountsByGroupId(String groupId) {
        if (!groupAccountsMediatorMap.containsKey(groupId)) {
            MediatorLiveData<List<Account>> mediator = new MediatorLiveData<>();
            LiveData<List<Account>> source = repository.getAccountsByGroup(groupId);

            mediator.addSource(source, accounts -> {
                Log.d(TAG, "✅ 组 " + groupId + " 的账户数据更新: " +
                        (accounts != null ? accounts.size() : 0) + " 条");
                mediator.setValue(accounts);

                // 通知特定组更新
                notifyGroupAccountsUpdate(groupId, accounts);
            });

            groupAccountsMap.put(groupId, source);
            groupAccountsMediatorMap.put(groupId, mediator);
        }

        return groupAccountsMediatorMap.get(groupId);
    }

    /**
     *  新增：通知特定组的账户数据更新
     */
    private void notifyGroupAccountsUpdate(String groupId, List<Account> accounts) {
        Map<String, List<Account>> updateMap = new HashMap<>();
        updateMap.put(groupId, accounts);
        _groupAccountsUpdate.postValue(updateMap);
    }

    /**
     * 加载指定组的账户（保持兼容性）
     */
    public void loadAccountsByGroup(String groupId) {
        Log.d(TAG, "🔄 触发加载账户组账户: " + groupId);
        // 直接调用 getAccountsByGroupId 会自动触发数据更新
        getAccountsByGroupId(groupId);
    }

    public void loadAccountGroups(String userId) {
        _groupState.setValue(UiState.loading());
        LiveData<List<AccountGroup>> source = repository.getAccountGroups(userId);
        _groupState.addSource(source, groups -> {
            if (groups != null) {
                _groupState.setValue(UiState.success(groups));
                Log.d(TAG, "✅ 账户组加载成功: " + groups.size());
            } else {
                _groupState.setValue(UiState.error("暂无账户组数据"));
            }
            _groupState.removeSource(source);
        });
    }

    // ===================== 插入操作 =====================

    public void insertAccount(Account account, ResultCallback callback) {
        repository.insertAccount(account, (success, message) -> {
            if (success) {
                _addAccountState.setValue(UiState.success(account));
                uiMessage.postMessage("新增账户成功：" + account.getName());
                Log.d(TAG, "✅ 新增账户成功：" + account.getName());

                if (callback != null) {
                    callback.onResult(true, "新增账户成功：" + account.getName());
                }
            } else {
                _addAccountState.setValue(UiState.error(message != null ? message : "新增账户失败"));
                uiMessage.postMessage("新增账户失败：" + (message != null ? message : ""));
                Log.d(TAG, "❌ 新增账户失败：" + (message != null ? message : ""));

                if (callback != null) {
                    callback.onResult(false, message != null ? message : "新增账户失败");
                }
            }
        });
    }

    public void insertAccount(Account account) {
        insertAccount(account, null);
    }

    public void insertAccountGroup(AccountGroup group, ResultCallback callback) {
        _addGroupState.setValue(UiState.loading());
        repository.insertAccountGroup(group, (success, message) -> {
            if (success) {
                _addGroupState.setValue(UiState.success(group));
                uiMessage.postMessage("新增账户组成功：" + group.getName());
                Log.d(TAG, "✅ 新增账户组成功：" + group.getName());

                if (callback != null) {
                    callback.onResult(true, "新增账户组成功：" + group.getName());
                }
            } else {
                _addGroupState.setValue(UiState.error(message != null ? message : "新增账户组失败"));
                uiMessage.postMessage(message != null ? message : "新增账户组失败");
                Log.d(TAG, "❌ 新增账户组失败：" + (message != null ? message : ""));

                if (callback != null) {
                    callback.onResult(false, message != null ? message : "新增账户组失败");
                }
            }
        });
    }

    public void insertAccountGroup(AccountGroup group) {
        insertAccountGroup(group, null);
    }

    // ===================== 更新操作 =====================

    public void updateAccount(Account account, ResultCallback callback) {
        repository.updateAccount(account, (success, message) -> {
            if (success) {
                uiMessage.postMessage("更新账户成功：" + account.getName());
                Log.d(TAG, "✅ 更新账户成功：" + account.getName());

                if (callback != null) {
                    callback.onResult(true, "更新账户成功：" + account.getName());
                }
            } else {
                uiMessage.postMessage("更新账户失败：" + (message != null ? message : ""));
                Log.e(TAG, "❌ 更新账户失败：" + (message != null ? message : ""));

                if (callback != null) {
                    callback.onResult(false, message != null ? message : "更新账户失败");
                }
            }
        });
    }

    public void updateAccount(Account account) {
        updateAccount(account, null);
    }

    public void updateAccountGroup(AccountGroup group, ResultCallback callback) {
        repository.updateAccountGroup(group, (success, message) -> {
            if (success) {
                uiMessage.postMessage("更新账户组成功：" + group.getName());
                Log.d(TAG, "✅ 更新账户组成功：" + group.getName());

                if (callback != null) {
                    callback.onResult(true, "更新账户组成功：" + group.getName());
                }
            } else {
                uiMessage.postMessage("更新账户组失败：" + (message != null ? message : ""));
                Log.e(TAG, "❌ 更新账户组失败：" + (message != null ? message : ""));

                if (callback != null) {
                    callback.onResult(false, message != null ? message : "更新账户组失败");
                }
            }
        });
    }

    public void updateAccountGroup(AccountGroup group) {
        updateAccountGroup(group, null);
    }

    // ===================== 删除操作 =====================

    public void deleteAccount(Account account, ResultCallback callback) {
        repository.deleteAccount(account, (success, message) -> {
            if (success) {
                uiMessage.postMessage("删除账户成功：" + account.getName());
                Log.d(TAG, "✅ 删除账户成功：" + account.getName());

                if (callback != null) {
                    callback.onResult(true, "删除账户成功：" + account.getName());
                }
            } else {
                uiMessage.postMessage("删除账户失败：" + (message != null ? message : ""));
                Log.e(TAG, "❌ 删除账户失败：" + (message != null ? message : ""));

                if (callback != null) {
                    callback.onResult(false, message != null ? message : "删除账户失败");
                }
            }
        });
    }

    public void deleteAccount(Account account) {
        deleteAccount(account, null);
    }

    public void deleteAccountGroup(AccountGroup group, ResultCallback callback) {
        repository.deleteAccountGroup(group, (success, message) -> {
            if (success) {
                uiMessage.postMessage("删除账户组成功:" + group.getName());
                Log.d(TAG, "✅ 删除账户组成功:" + group.getName());

                if (callback != null) {
                    callback.onResult(true, "删除账户组成功");
                }
            } else {
                uiMessage.postMessage("删除账户组失败:" + (message != null ? message : ""));
                Log.e(TAG, "❌ 删除账户组失败:" + (message != null ? message : ""));

                if (callback != null) {
                    callback.onResult(false, message != null ? message : "删除账户组失败");
                }
            }
        });
    }

    public void moveAccountsToGroup(String oldGroupId, String newGroupId, ResultCallback callback) {
        repository.moveAccountsToGroup(oldGroupId, newGroupId, (success, message) -> {
            if (success) {
                uiMessage.postMessage(message != null ? message : "账户移动成功");
                Log.d(TAG, "✅ 账户移动成功:" + message);

                if (callback != null) {
                    callback.onResult(true, message != null ? message : "账户移动成功");
                }
            } else {
                uiMessage.postMessage("移动账户失败:" + (message != null ? message : ""));
                Log.e(TAG, "❌ 移动账户失败:" + (message != null ? message : ""));

                if (callback != null) {
                    callback.onResult(false, message != null ? message : "移动账户失败");
                }
            }
        });
    }

    /**
     * 移动单个账户后确保两个组都更新
     */
    public void moveSingleAccount(String accountId, String newGroupId, ResultCallback callback) {
        String oldGroupId = repository.getGroupIdByAccountId(accountId);

        repository.moveSingleAccountToGroup(accountId, newGroupId, (success, message) -> {
            if (success) {
                uiMessage.postMessage(message != null ? message : "账户移动成功");
                Log.d(TAG, "✅ 单个账户移动成功:" + message);

                // 🔴 关键修复：确保两个组都已经订阅了 LiveData
                if (newGroupId != null) {
                    getAccountsByGroupId(newGroupId); // 确保新组已订阅
                }
                if (oldGroupId != null && !oldGroupId.equals(newGroupId)) {
                    getAccountsByGroupId(oldGroupId); // 确保旧组已订阅
                }

                if (callback != null) {
                    callback.onResult(true, message != null ? message : "账户移动成功");
                }
            } else {
                uiMessage.postMessage("移动账户失败:" + (message != null ? message : ""));
                Log.e(TAG, "❌ 单个账户移动失败:" + (message != null ? message : ""));

                if (callback != null) {
                    callback.onResult(false, message != null ? message : "移动账户失败");
                }
            }
        });
    }

    /**
     * 根据 groupId 获取账户组名称（异步回调）
     */
    public void getAccountByGroupName(String groupId, ResultCallback callback) {
        if (groupId == null) {
            if (callback != null) {
                callback.onResult(false, null);
            }
            return;
        }

        repository.getAccountByGroupName(groupId, (success, result) -> {
            if (success) {
                Log.d(TAG, "✅ 获取组名成功: " + result);
                if (callback != null) {
                    callback.onResult(true, result); // 返回组名
                }
            } else {
                Log.e(TAG, "❌ 获取组名失败: " + groupId);
                if (callback != null) {
                    callback.onResult(false, null);
                }
            }
        });
    }

    /**
     * 根据accountId获取账户名称
     */
    public void getAccountNameById(String accountId, ResultCallback callback) {
        if (accountId == null || accountId.isEmpty()) {
            if (callback != null) {
                callback.onResult(false, null);
            }
            return;
        }

        repository.getAccountNameById(accountId, (success, accountName) -> {
            if (success && accountName != null) {
                Log.d(TAG, "✅ 获取账户名称成功: " + accountName);
                if (callback != null) {
                    callback.onResult(true, accountName);
                }
            } else {
                Log.e(TAG, "❌ 获取账户名称失败: accountId=" + accountId);
                if (callback != null) {
                    callback.onResult(false, null);
                }
            }
        });
    }

    /**
     * 同步获取账户组信息
     */
    public AccountGroup getAccountGroupByIdSync(String groupId) {
        return repository.getAccountGroupByIdSync(groupId);
    }

    public void deleteAccountGroup(AccountGroup group) {
        deleteAccountGroup(group, null);
    }

    public void confirmDeleteGroupAndAccounts(AccountGroup group, ResultCallback callback) {
        repository.confirmDeleteGroupAndAccounts(group, (success, message) -> {
            if (success) {
                uiMessage.postMessage("删除账户组及其账户成功：" + group.getName());

                if (callback != null) {
                    callback.onResult(true, "删除账户组及其账户成功：" + group.getName());
                }
            } else {
                uiMessage.postMessage("删除账户组及其账户失败：" + (message != null ? message : ""));

                if (callback != null) {
                    callback.onResult(false, message != null ? message : "删除账户组及其账户失败");
                }
            }
        });
    }

    public void confirmDeleteGroupAndAccounts(AccountGroup group) {
        confirmDeleteGroupAndAccounts(group, null);
    }

    // ===================== 🔹 云同步 =====================

    public void syncFromCloud(ResultCallback callback) {
        repository.syncFromAccountGroupCloud((success, message) -> {
            if (success) {
                Log.d(TAG, "云端同步成功");
                BmobUser currentUser = BmobUser.getCurrentUser();
                if (currentUser == null) {
                    Log.w("AccountViewModel", "用户未登录，跳过云同步");
                    return;
                }
                loadAccountGroups(currentUser.getObjectId());

                if (callback != null) {
                    callback.onResult(true, "云端同步成功");
                }
            } else {
                Log.d(TAG, "云端同步失败：" + (message != null ? message : ""));

                if (callback != null) {
                    callback.onResult(false, message != null ? message : "云端同步失败");
                }
            }
        });
    }

    public void syncFromCloud() {
        syncFromCloud(null);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        // 清理所有 MediatorLiveData
        for (MediatorLiveData<List<Account>> mediator : groupAccountsMediatorMap.values()) {
            for (LiveData<List<Account>> source : groupAccountsMap.values()) {
                mediator.removeSource(source);
            }
        }
        groupAccountsMap.clear();
        groupAccountsMediatorMap.clear();
        accountLiveDataMap.clear(); //清理单个账户的LiveData缓存
    }
}