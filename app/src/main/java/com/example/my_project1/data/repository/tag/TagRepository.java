package com.example.my_project1.data.repository.tag;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.example.my_project1.data.dao.BillTagCrossRefDao;
import com.example.my_project1.data.dao.TagDao;
import com.example.my_project1.data.dao.TagGroupDao;
import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.common.ApiResponse;
import com.example.my_project1.data.model.tag.BillTagCrossRef;
import com.example.my_project1.data.model.tag.TagEntity;
import com.example.my_project1.data.model.tag.TagGroupEntity;
import com.example.my_project1.data.model.tag.TagGroupWithTags;
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.work.TagSyncWorker;

import java.util.Date;
import java.util.List;

/**
 * TagRepository - 标签数据仓库
 * -------------------------------------------------------
 * 遵循离线优先原则与单向数据流 (UDF)：
 * 1. 增删改查操作优先写本地 SQLite (Room)，写入成功后立刻触发 TagSyncWorker 后台同步。
 * 2. 异步回调使用通用的 ApiResponse.Callback<T> 接口封装。
 */
public class TagRepository {

    private static final String TAG = "TagRepository";

    /** 系统内置默认标签分组的显示名称 */
    public static final String DEFAULT_GROUP_NAME = "默认分组";

    /** 应用上下文对象 */
    private final Context context;

    /** 标签分组数据库访问对象 */
    private final TagGroupDao tagGroupDao;

    /** 标签数据库访问对象 */
    private final TagDao tagDao;

    /** 账单与标签关联表数据库访问对象 */
    private final BillTagCrossRefDao crossRefDao;

    /**
     * 构造函数，初始化 DAO 对象
     *
     * @param context 上下文
     */
    public TagRepository(Context context) {
        // 保存全局 Context，防止内存泄漏
        this.context = context.getApplicationContext();

        // 获取数据库单例实例并初始化各个 DAO
        AppDatabase db = AppDatabase.getInstance(this.context);
        this.tagGroupDao = db.tagGroupDao();
        this.tagDao = db.tagDao();
        this.crossRefDao = db.billTagCrossRefDao();
    }

    /**
     * 在后台磁盘 IO 线程池中异步执行任务
     *
     * @param runnable 待执行任务
     */
    private void runInBackground(Runnable runnable) {
        AppExecutors.get().diskIO().execute(runnable);
    }

    /**
     * 在主线程（UI 线程）中执行回调任务
     *
     * @param runnable 待执行任务
     */
    private void runOnMain(Runnable runnable) {
        AppExecutors.get().mainThread().execute(runnable);
    }

    /**
     * 触发 TagSyncWorker 将后台云端同步任务入列
     */
    private void triggerSync() {
        try {
            // 调用 Worker 工具方法，将同步 Request 提交给 WorkManager
            TagSyncWorker.enqueue(context);
        } catch (Exception e) {
            Log.e(TAG, "触发 TagSyncWorker 失败: " + e.getMessage());
        }
    }

    // ==================== 默认分组初始化 ====================

    /**
     * 异步确保用户的默认标签分组已创建
     *
     * @param userId   用户ID
     * @param callback 回调函数
     */
    public void ensureDefaultGroupCreated(String userId, ApiResponse.Callback<Boolean> callback) {
        runInBackground(() -> {
            try {
                // 1. 尝试从本地数据库中查询该用户的默认分组
                TagGroupEntity defaultGroup = tagGroupDao.getDefaultGroup(userId);

                // 2. 如果不存在，则创建默认分组实体并写入数据库
                if (defaultGroup == null) {
                    List<TagGroupEntity> groups = tagGroupDao.getActiveGroupsSync(userId);
                    int sortOrder = groups != null ? groups.size() + 100 : 100;
                    defaultGroup = new TagGroupEntity(userId, DEFAULT_GROUP_NAME, sortOrder, true);

                    // 写入本地数据库并记录产生的自增ID
                    long id = tagGroupDao.insertGroup(defaultGroup);
                    defaultGroup.setId(id);
                }

                // 3. 回调通知调用方默认分组已准备就绪
                if (callback != null) {
                    runOnMain(() -> callback.onComplete(ApiResponse.success(true, "Default group ready")));
                }
            } catch (Exception e) {
                Log.e(TAG, "ensureDefaultGroupCreated error", e);
                if (callback != null) {
                    runOnMain(() -> callback.onComplete(ApiResponse.error(e)));
                }
            }
        });
    }

    /**
     * 同步获取或创建默认标签分组
     *
     * @param userId 用户ID
     * @return 默认标签分组实体
     */
    public TagGroupEntity getDefaultGroupSync(String userId) {
        // 先查询本地数据库中的默认分组
        TagGroupEntity defaultGroup = tagGroupDao.getDefaultGroup(userId);

        // 若不存在则立即创建并存入本地数据库
        if (defaultGroup == null) {
            List<TagGroupEntity> groups = tagGroupDao.getActiveGroupsSync(userId);
            int sortOrder = groups != null ? groups.size() + 100 : 100;
            defaultGroup = new TagGroupEntity(userId, DEFAULT_GROUP_NAME, sortOrder, true);
            long id = tagGroupDao.insertGroup(defaultGroup);
            defaultGroup.setId(id);
        }
        return defaultGroup;
    }

    // ==================== 标签组操作 ====================

    /**
     * 获取带有标签列表的分组 LiveData
     *
     * @param userId 用户ID
     * @return 分组及标签列表 LiveData
     */
    public LiveData<List<TagGroupWithTags>> getGroupsWithTagsLiveData(String userId) {
        // 自动保证默认分组已存在
        ensureDefaultGroupCreated(userId, null);
        // 触发云端同步拉取数据
        triggerSync();
        return tagGroupDao.getGroupsWithTagsLiveData(userId);
    }

    /**
     * 获取激活的标签分组列表 LiveData
     *
     * @param userId 用户ID
     * @return 标签分组列表 LiveData
     */
    public LiveData<List<TagGroupEntity>> getActiveGroupsLiveData(String userId) {
        // 自动保证默认分组已存在
        ensureDefaultGroupCreated(userId, null);
        triggerSync();
        return tagGroupDao.getActiveGroupsLiveData(userId);
    }

    /**
     * 创建新的标签分组
     *
     * @param userId    用户ID
     * @param groupName 分组名称
     * @param callback  回调函数，返回新建分组的本地 ID
     */
    public void createGroup(String userId, String groupName, ApiResponse.Callback<Long> callback) {
        runInBackground(() -> {
            try {
                // 1. 检查是否存在同名标签或同名标签组（重名校验）
                TagGroupEntity existingGroup = tagGroupDao.getGroupByName(userId, groupName);
                TagEntity existingTag = tagDao.getTagByName(userId, groupName);
                if (existingGroup != null || existingTag != null) {
                    runOnMain(() -> callback.onComplete(ApiResponse.error("标签和标签组不能同名")));
                    return;
                }

                // 2. 计算新分组的排序权重
                List<TagGroupEntity> activeGroups = tagGroupDao.getActiveGroupsSync(userId);
                int sortOrder = activeGroups != null ? activeGroups.size() : 0;

                // 3. 构建实体并写入本地 Room 数据库
                TagGroupEntity newGroup = new TagGroupEntity(userId, groupName, sortOrder, false);
                long id = tagGroupDao.insertGroup(newGroup);

                if (id > 0) {
                    // 写入本地成功后，立即触发后台同步任务上传云端
                    triggerSync();
                    runOnMain(() -> callback.onComplete(ApiResponse.success(id, "新建分组成功")));
                } else {
                    runOnMain(() -> callback.onComplete(ApiResponse.error("新建分组失败")));
                }
            } catch (Exception e) {
                Log.e(TAG, "createGroup error", e);
                runOnMain(() -> callback.onComplete(ApiResponse.error(e)));
            }
        });
    }

    /**
     * 更新标签分组名称
     *
     * @param group    目标标签分组
     * @param newName  新分组名称
     * @param callback 回调函数
     */
    public void updateGroupName(TagGroupEntity group, String newName, ApiResponse.Callback<Void> callback) {
        runInBackground(() -> {
            try {
                // 默认分组不允许重命名
                if (group.isDefault()) {
                    runOnMain(() -> callback.onComplete(ApiResponse.error("默认分组不可重命名")));
                    return;
                }

                // 重名校验
                TagGroupEntity existingGroup = tagGroupDao.getGroupByName(group.getUserId(), newName);
                TagEntity existingTag = tagDao.getTagByName(group.getUserId(), newName);
                if ((existingGroup != null && existingGroup.getId() != group.getId()) || existingTag != null) {
                    runOnMain(() -> callback.onComplete(ApiResponse.error("标签和标签组不能同名")));
                    return;
                }

                // 更新本地实体属性与修改时间，并标注同步状态为待更新
                group.setName(newName);
                group.setUpdatedAt(new Date());
                group.setSyncState(SyncState.TO_UPDATE);
                tagGroupDao.updateGroup(group);

                // 触发云端同步
                triggerSync();
                runOnMain(() -> callback.onComplete(ApiResponse.success(null, "重命名成功")));
            } catch (Exception e) {
                Log.e(TAG, "updateGroupName error", e);
                runOnMain(() -> callback.onComplete(ApiResponse.error(e)));
            }
        });
    }

    /**
     * 更新标签分组的折叠状态
     *
     * @param groupId  分组ID
     * @param isFolded 是否折叠
     */
    public void updateGroupFoldState(long groupId, boolean isFolded) {
        // 更新分组在UI上的展开折叠状态
        runInBackground(() -> tagGroupDao.updateFoldState(groupId, isFolded));
    }

    /**
     * 删除标签分组（删除前将分组内的标签移至默认分组）
     *
     * @param group    待删除分组
     * @param callback 回调函数
     */
    public void deleteGroup(TagGroupEntity group, ApiResponse.Callback<Void> callback) {
        runInBackground(() -> {
            try {
                // 默认分组不能被删除
                if (group.isDefault()) {
                    runOnMain(() -> callback.onComplete(ApiResponse.error("默认分组不能删除")));
                    return;
                }

                // 1. 先将原分组下的所有标签重新分配归类到默认分组
                TagGroupEntity defaultGroup = getDefaultGroupSync(group.getUserId());
                tagDao.moveTagsToGroup(group.getUserId(), group.getId(), defaultGroup.getId());

                // 2. 将待删除分组标记为逻辑删除，设置 syncState 为 TO_DELETE
                group.setDeleted(true);
                group.setUpdatedAt(new Date());
                group.setSyncState(SyncState.TO_DELETE);
                tagGroupDao.updateGroup(group);

                // 3. 触发云端同步进行后台物理删除
                triggerSync();
                runOnMain(() -> callback.onComplete(ApiResponse.success(null, "删除分组成功，标签已移至默认分组")));
            } catch (Exception e) {
                Log.e(TAG, "deleteGroup error", e);
                runOnMain(() -> callback.onComplete(ApiResponse.error(e)));
            }
        });
    }

    // ==================== 标签操作 ====================

    /**
     * 获取所有激活状态的标签 LiveData
     *
     * @param userId 用户ID
     * @return 激活标签列表 LiveData
     */
    public LiveData<List<TagEntity>> getAllActiveTagsLiveData(String userId) {
        triggerSync();
        return tagDao.getAllActiveTagsLiveData(userId);
    }

    /**
     * 获取所有归档状态的标签 LiveData
     *
     * @param userId 用户ID
     * @return 归档标签列表 LiveData
     */
    public LiveData<List<TagEntity>> getArchivedTagsLiveData(String userId) {
        triggerSync();
        return tagDao.getArchivedTagsLiveData(userId);
    }

    /**
     * 保存或更新标签实体
     *
     * @param tag      标签实体
     * @param callback 回调函数，返回保存后的本地标签 ID
     */
    public void saveTag(TagEntity tag, ApiResponse.Callback<Long> callback) {
        runInBackground(() -> {
            try {
                // 1. 同名校验
                TagGroupEntity existingGroup = tagGroupDao.getGroupByName(tag.getUserId(), tag.getName());
                TagEntity existingTag = tagDao.getTagByName(tag.getUserId(), tag.getName());
                if (existingGroup != null || (existingTag != null && existingTag.getId() != tag.getId())) {
                    runOnMain(() -> callback.onComplete(ApiResponse.error("标签和标签组不能同名")));
                    return;
                }

                // 2. 若未指定分组，默认存入默认分组
                if (tag.getGroupId() <= 0) {
                    TagGroupEntity defaultGroup = getDefaultGroupSync(tag.getUserId());
                    tag.setGroupId(defaultGroup.getId());
                }

                tag.setUpdatedAt(new Date());
                long resultId = tag.getId();

                // 3. 根据自增ID判断是新建还是修改
                if (tag.getId() <= 0) {
                    // 新建标签：标注 syncState 为 TO_CREATE
                    tag.setSyncState(SyncState.TO_CREATE);
                    resultId = tagDao.insertTag(tag);
                    tag.setId(resultId);
                } else {
                    // 更新标签：标注 syncState 为 TO_UPDATE
                    tag.setSyncState(SyncState.TO_UPDATE);
                    tagDao.updateTag(tag);
                }

                // 4. 触发后台 Worker 进行数据同步
                triggerSync();
                final long savedId = resultId;
                runOnMain(() -> callback.onComplete(ApiResponse.success(savedId, "保存成功")));
            } catch (Exception e) {
                Log.e(TAG, "saveTag error", e);
                runOnMain(() -> callback.onComplete(ApiResponse.error(e)));
            }
        });
    }

    /**
     * 将标签移动到指定的标签分组
     *
     * @param tagId      标签ID
     * @param newGroupId 目标分组ID
     * @param userId     用户ID
     * @param callback   回调函数
     */
    public void moveTagGroup(long tagId, long newGroupId, String userId, ApiResponse.Callback<Void> callback) {
        runInBackground(() -> {
            try {
                TagEntity tag = tagDao.getTagById(tagId);
                if (tag != null) {
                    // 更新标签所属的分组ID和同步状态
                    tag.setGroupId(newGroupId);
                    tag.setUpdatedAt(new Date());
                    tag.setSyncState(SyncState.TO_UPDATE);
                    tagDao.updateTag(tag);

                    // 触发后台同步
                    triggerSync();
                    runOnMain(() -> callback.onComplete(ApiResponse.success(null, "移动分组成功")));
                } else {
                    runOnMain(() -> callback.onComplete(ApiResponse.error("标签不存在")));
                }
            } catch (Exception e) {
                Log.e(TAG, "moveTagGroup error", e);
                runOnMain(() -> callback.onComplete(ApiResponse.error(e)));
            }
        });
    }

    /**
     * 在分组内部按标签名称对标签重新排序
     *
     * @param groupId  分组ID
     * @param userId   用户ID
     * @param callback 回调函数
     */
    public void sortTagsInGroup(long groupId, String userId, ApiResponse.Callback<Void> callback) {
        runInBackground(() -> {
            try {
                List<TagEntity> tags = tagDao.getTagsByGroupIdSync(userId, groupId);
                if (tags != null && tags.size() > 1) {
                    // 按拼音/字母顺序排序标签列表
                    java.util.Collections.sort(tags, (t1, t2) -> {
                        if (t1.getName() == null) return -1;
                        if (t2.getName() == null) return 1;
                        return t1.getName().compareToIgnoreCase(t2.getName());
                    });

                    // 重新分配 sortOrder 序号并更新数据库
                    for (int i = 0; i < tags.size(); i++) {
                        TagEntity tag = tags.get(i);
                        tag.setSortOrder(i);
                        tag.setUpdatedAt(new Date());
                        tag.setSyncState(SyncState.TO_UPDATE);
                        tagDao.updateTag(tag);
                    }

                    // 触发后台同步
                    triggerSync();
                    runOnMain(() -> callback.onComplete(ApiResponse.success(null, "标签已在当前分组内重新排序")));
                } else {
                    runOnMain(() -> callback.onComplete(ApiResponse.success(null, "当前分组标签无需排序")));
                }
            } catch (Exception e) {
                Log.e(TAG, "sortTagsInGroup error", e);
                runOnMain(() -> callback.onComplete(ApiResponse.error(e)));
            }
        });
    }

    /**
     * 切换标签的归档/解档状态
     *
     * @param tagId    标签ID
     * @param archive  是否归档
     * @param userId   用户ID
     * @param callback 回调函数
     */
    public void toggleTagArchive(long tagId, boolean archive, String userId, ApiResponse.Callback<Void> callback) {
        runInBackground(() -> {
            try {
                TagEntity tag = tagDao.getTagById(tagId);
                if (tag != null) {
                    // 修改归档状态与同步标志
                    tag.setArchived(archive);
                    tag.setUpdatedAt(new Date());
                    tag.setSyncState(SyncState.TO_UPDATE);
                    tagDao.updateTag(tag);

                    // 触发后台同步
                    triggerSync();
                    String msg = archive ? "已归档" : "已解档";
                    runOnMain(() -> callback.onComplete(ApiResponse.success(null, msg)));
                } else {
                    runOnMain(() -> callback.onComplete(ApiResponse.error("标签不存在")));
                }
            } catch (Exception e) {
                Log.e(TAG, "toggleTagArchive error", e);
                runOnMain(() -> callback.onComplete(ApiResponse.error(e)));
            }
        });
    }

    /**
     * 逻辑删除标签及其关联记录
     *
     * @param tagId    标签ID
     * @param userId   用户ID
     * @param callback 回调函数
     */
    public void deleteTag(long tagId, String userId, ApiResponse.Callback<Void> callback) {
        runInBackground(() -> {
            try {
                TagEntity tag = tagDao.getTagById(tagId);
                if (tag != null) {
                    // 1. 设置标签为逻辑删除，等待 Worker 云端同步删除
                    tag.setDeleted(true);
                    tag.setUpdatedAt(new Date());
                    tag.setSyncState(SyncState.TO_DELETE);
                    tagDao.updateTag(tag);

                    // 2. 清理关联表记录
                    crossRefDao.deleteByTagId(tagId);

                    // 3. 触发云端同步
                    triggerSync();
                    runOnMain(() -> callback.onComplete(ApiResponse.success(null, "删除标签成功")));
                } else {
                    runOnMain(() -> callback.onComplete(ApiResponse.error("标签不存在")));
                }
            } catch (Exception e) {
                Log.e(TAG, "deleteTag error", e);
                runOnMain(() -> callback.onComplete(ApiResponse.error(e)));
            }
        });
    }

    // ==================== 账单标签关联 ====================

    /**
     * 实时监听账单对应的标签列表
     *
     * @param billId 账单ID
     * @return 标签实体列表 LiveData
     */
    public LiveData<List<TagEntity>> getTagsForBillLiveData(long billId) {
        return crossRefDao.getTagsForBillLiveData(billId);
    }

    /**
     * 同步获取账单对应的标签列表
     *
     * @param billId 账单ID
     * @return 标签实体列表
     */
    public List<TagEntity> getTagsForBillSync(long billId) {
        return crossRefDao.getTagsForBillSync(billId);
    }

    /**
     * 保存账单关联的选中标签列表
     *
     * @param billId       账单ID
     * @param userId       用户ID
     * @param selectedTags 选中的标签列表
     * @param callback     回调函数
     */
    public void saveBillTags(long billId, String userId, List<TagEntity> selectedTags, ApiResponse.Callback<Void> callback) {
        runInBackground(() -> {
            try {
                // 1. 先清除该账单原有的所有标签关联
                crossRefDao.deleteByBillId(billId);

                // 2. 遍历选中的标签列表，重新构建交叉表记录并插入
                if (selectedTags != null && !selectedTags.isEmpty()) {
                    for (TagEntity tag : selectedTags) {
                        BillTagCrossRef ref = new BillTagCrossRef(
                                billId,
                                tag.getId(),
                                userId,
                                tag.getName(),
                                tag.getBgColor(),
                                tag.getTextColor()
                        );
                        crossRefDao.insertCrossRef(ref);
                    }
                }

                // 3. 触发云端同步
                triggerSync();
                if (callback != null) {
                    runOnMain(() -> callback.onComplete(ApiResponse.success(null, "账单标签更新成功")));
                }
            } catch (Exception e) {
                Log.e(TAG, "saveBillTags error", e);
                if (callback != null) {
                    runOnMain(() -> callback.onComplete(ApiResponse.error(e)));
                }
            }
        });
    }

    // ==================== Bmob 云端同步入口 ====================

    /**
     * 上传待同步的修改并触发后台 Worker
     *
     * @param userId 用户ID
     */
    public void uploadPendingChanges(String userId) {
        if (userId == null || userId.isEmpty()) return;

        // 触发 TagSyncWorker 后台任务
        triggerSync();
    }
}
