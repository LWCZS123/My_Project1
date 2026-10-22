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
import com.example.my_project1.data.remote.model.cloudtag.BmobTagApiImpl;
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
    public static final String DEFAULT_GROUP_NAME = "默认分组";

    private final Context context;
    private final TagGroupDao tagGroupDao;
    private final TagDao tagDao;
    private final BillTagCrossRefDao crossRefDao;
    private final BmobTagApiImpl bmobTagApi;

    public TagRepository(Context context) {
        this.context = context.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(this.context);
        this.tagGroupDao = db.tagGroupDao();
        this.tagDao = db.tagDao();
        this.crossRefDao = db.billTagCrossRefDao();
        this.bmobTagApi = new BmobTagApiImpl(this.context);
    }

    private void runInBackground(Runnable runnable) {
        AppExecutors.get().diskIO().execute(runnable);
    }

    private void runOnMain(Runnable runnable) {
        AppExecutors.get().mainThread().execute(runnable);
    }

    private void triggerSync() {
        try {
            TagSyncWorker.enqueue(context);
        } catch (Exception e) {
            Log.e(TAG, "触发 TagSyncWorker 失败: " + e.getMessage());
        }
    }

    // ==================== 默认分组初始化 ====================

    public void ensureDefaultGroupCreated(String userId, ApiResponse.Callback<Boolean> callback) {
        runInBackground(() -> {
            try {
                TagGroupEntity defaultGroup = tagGroupDao.getDefaultGroup(userId);
                if (defaultGroup == null) {
                    List<TagGroupEntity> groups = tagGroupDao.getActiveGroupsSync(userId);
                    int sortOrder = groups != null ? groups.size() + 100 : 100;
                    defaultGroup = new TagGroupEntity(userId, DEFAULT_GROUP_NAME, sortOrder, true);
                    long id = tagGroupDao.insertGroup(defaultGroup);
                    defaultGroup.setId(id);
                }
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

    public TagGroupEntity getDefaultGroupSync(String userId) {
        TagGroupEntity defaultGroup = tagGroupDao.getDefaultGroup(userId);
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

    public LiveData<List<TagGroupWithTags>> getGroupsWithTagsLiveData(String userId) {
        ensureDefaultGroupCreated(userId, null);
        return tagGroupDao.getGroupsWithTagsLiveData(userId);
    }

    public LiveData<List<TagGroupEntity>> getActiveGroupsLiveData(String userId) {
        ensureDefaultGroupCreated(userId, null);
        return tagGroupDao.getActiveGroupsLiveData(userId);
    }

    public void createGroup(String userId, String groupName, ApiResponse.Callback<Long> callback) {
        runInBackground(() -> {
            try {
                TagGroupEntity existingGroup = tagGroupDao.getGroupByName(userId, groupName);
                TagEntity existingTag = tagDao.getTagByName(userId, groupName);
                if (existingGroup != null || existingTag != null) {
                    runOnMain(() -> callback.onComplete(ApiResponse.error("标签和标签组不能同名")));
                    return;
                }
                List<TagGroupEntity> activeGroups = tagGroupDao.getActiveGroupsSync(userId);
                int sortOrder = activeGroups != null ? activeGroups.size() : 0;
                TagGroupEntity newGroup = new TagGroupEntity(userId, groupName, sortOrder, false);
                long id = tagGroupDao.insertGroup(newGroup);
                if (id > 0) {
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

    public void updateGroupName(TagGroupEntity group, String newName, ApiResponse.Callback<Void> callback) {
        runInBackground(() -> {
            try {
                if (group.isDefault()) {
                    runOnMain(() -> callback.onComplete(ApiResponse.error("默认分组不可重命名")));
                    return;
                }
                TagGroupEntity existingGroup = tagGroupDao.getGroupByName(group.getUserId(), newName);
                TagEntity existingTag = tagDao.getTagByName(group.getUserId(), newName);
                if ((existingGroup != null && existingGroup.getId() != group.getId()) || existingTag != null) {
                    runOnMain(() -> callback.onComplete(ApiResponse.error("标签和标签组不能同名")));
                    return;
                }
                group.setName(newName);
                group.setUpdatedAt(new Date());
                group.setSyncState(SyncState.TO_UPDATE);
                tagGroupDao.updateGroup(group);
                triggerSync();
                runOnMain(() -> callback.onComplete(ApiResponse.success(null, "重命名成功")));
            } catch (Exception e) {
                Log.e(TAG, "updateGroupName error", e);
                runOnMain(() -> callback.onComplete(ApiResponse.error(e)));
            }
        });
    }

    public void updateGroupFoldState(long groupId, boolean isFolded) {
        runInBackground(() -> tagGroupDao.updateFoldState(groupId, isFolded));
    }

    public void deleteGroup(TagGroupEntity group, ApiResponse.Callback<Void> callback) {
        runInBackground(() -> {
            try {
                if (group.isDefault()) {
                    runOnMain(() -> callback.onComplete(ApiResponse.error("默认分组不能删除")));
                    return;
                }
                TagGroupEntity defaultGroup = getDefaultGroupSync(group.getUserId());
                tagDao.moveTagsToGroup(group.getUserId(), group.getId(), defaultGroup.getId());

                group.setDeleted(true);
                group.setUpdatedAt(new Date());
                group.setSyncState(SyncState.TO_DELETE);
                tagGroupDao.updateGroup(group);

                triggerSync();
                runOnMain(() -> callback.onComplete(ApiResponse.success(null, "删除分组成功，标签已移至默认分组")));
            } catch (Exception e) {
                Log.e(TAG, "deleteGroup error", e);
                runOnMain(() -> callback.onComplete(ApiResponse.error(e)));
            }
        });
    }

    // ==================== 标签操作 ====================

    public LiveData<List<TagEntity>> getAllActiveTagsLiveData(String userId) {
        return tagDao.getAllActiveTagsLiveData(userId);
    }

    public LiveData<List<TagEntity>> getArchivedTagsLiveData(String userId) {
        return tagDao.getArchivedTagsLiveData(userId);
    }

    public void saveTag(TagEntity tag, ApiResponse.Callback<Long> callback) {
        runInBackground(() -> {
            try {
                TagGroupEntity existingGroup = tagGroupDao.getGroupByName(tag.getUserId(), tag.getName());
                TagEntity existingTag = tagDao.getTagByName(tag.getUserId(), tag.getName());
                if (existingGroup != null || (existingTag != null && existingTag.getId() != tag.getId())) {
                    runOnMain(() -> callback.onComplete(ApiResponse.error("标签和标签组不能同名")));
                    return;
                }
                if (tag.getGroupId() <= 0) {
                    TagGroupEntity defaultGroup = getDefaultGroupSync(tag.getUserId());
                    tag.setGroupId(defaultGroup.getId());
                }
                tag.setUpdatedAt(new Date());
                long resultId = tag.getId();
                if (tag.getId() <= 0) {
                    tag.setSyncState(SyncState.TO_CREATE);
                    resultId = tagDao.insertTag(tag);
                    tag.setId(resultId);
                } else {
                    tag.setSyncState(SyncState.TO_UPDATE);
                    tagDao.updateTag(tag);
                }
                triggerSync();
                final long savedId = resultId;
                runOnMain(() -> callback.onComplete(ApiResponse.success(savedId, "保存成功")));
            } catch (Exception e) {
                Log.e(TAG, "saveTag error", e);
                runOnMain(() -> callback.onComplete(ApiResponse.error(e)));
            }
        });
    }

    public void moveTagGroup(long tagId, long newGroupId, String userId, ApiResponse.Callback<Void> callback) {
        runInBackground(() -> {
            try {
                TagEntity tag = tagDao.getTagById(tagId);
                if (tag != null) {
                    tag.setGroupId(newGroupId);
                    tag.setUpdatedAt(new Date());
                    tag.setSyncState(SyncState.TO_UPDATE);
                    tagDao.updateTag(tag);
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

    public void sortTagsInGroup(long groupId, String userId, ApiResponse.Callback<Void> callback) {
        runInBackground(() -> {
            try {
                List<TagEntity> tags = tagDao.getTagsByGroupIdSync(userId, groupId);
                if (tags != null && tags.size() > 1) {
                    java.util.Collections.sort(tags, (t1, t2) -> {
                        if (t1.getName() == null) return -1;
                        if (t2.getName() == null) return 1;
                        return t1.getName().compareToIgnoreCase(t2.getName());
                    });
                    for (int i = 0; i < tags.size(); i++) {
                        TagEntity tag = tags.get(i);
                        tag.setSortOrder(i);
                        tag.setUpdatedAt(new Date());
                        tag.setSyncState(SyncState.TO_UPDATE);
                        tagDao.updateTag(tag);
                    }
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

    public void toggleTagArchive(long tagId, boolean archive, String userId, ApiResponse.Callback<Void> callback) {
        runInBackground(() -> {
            try {
                TagEntity tag = tagDao.getTagById(tagId);
                if (tag != null) {
                    tag.setArchived(archive);
                    tag.setUpdatedAt(new Date());
                    tag.setSyncState(SyncState.TO_UPDATE);
                    tagDao.updateTag(tag);
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

    public void deleteTag(long tagId, String userId, ApiResponse.Callback<Void> callback) {
        runInBackground(() -> {
            try {
                TagEntity tag = tagDao.getTagById(tagId);
                if (tag != null) {
                    tag.setDeleted(true);
                    tag.setUpdatedAt(new Date());
                    tag.setSyncState(SyncState.TO_DELETE);
                    tagDao.updateTag(tag);

                    crossRefDao.deleteByTagId(tagId);
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

    public LiveData<List<TagEntity>> getTagsForBillLiveData(long billId) {
        return crossRefDao.getTagsForBillLiveData(billId);
    }

    public List<TagEntity> getTagsForBillSync(long billId) {
        return crossRefDao.getTagsForBillSync(billId);
    }

    public void saveBillTags(long billId, String userId, List<TagEntity> selectedTags, ApiResponse.Callback<Void> callback) {
        runInBackground(() -> {
            try {
                crossRefDao.deleteByBillId(billId);
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

    // ==================== Bmob 云端同步 (由 TagSyncWorker 异步执行) ====================

    public void uploadPendingChanges(String userId) {
        if (userId == null || userId.isEmpty()) return;
        runInBackground(() -> {
            try {
                List<TagGroupEntity> unsyncedGroups = tagGroupDao.getUnsyncedGroups(userId);
                if (unsyncedGroups != null) {
                    for (TagGroupEntity group : unsyncedGroups) {
                        if (group.getSyncState() == SyncState.TO_CREATE) {
                            bmobTagApi.uploadTagGroup(group, new cn.bmob.v3.listener.SaveListener<String>() {
                                @Override
                                public void done(String objectId, cn.bmob.v3.exception.BmobException e) {
                                    if (e == null && objectId != null) {
                                        group.setObjectId(objectId);
                                        group.setSyncState(SyncState.SYNCED);
                                        runInBackground(() -> tagGroupDao.updateGroup(group));
                                    }
                                }
                            });
                        } else if (group.getSyncState() == SyncState.TO_UPDATE) {
                            bmobTagApi.updateTagGroup(group, new cn.bmob.v3.listener.UpdateListener() {
                                @Override
                                public void done(cn.bmob.v3.exception.BmobException e) {
                                    if (e == null) {
                                        group.setSyncState(SyncState.SYNCED);
                                        runInBackground(() -> tagGroupDao.updateGroup(group));
                                    }
                                }
                            });
                        } else if (group.getSyncState() == SyncState.TO_DELETE) {
                            if (group.getObjectId() != null) {
                                bmobTagApi.deleteTagGroup(group.getObjectId(), new cn.bmob.v3.listener.UpdateListener() {
                                    @Override
                                    public void done(cn.bmob.v3.exception.BmobException e) {
                                        if (e == null) {
                                            runInBackground(() -> tagGroupDao.deleteGroupHard(group.getId()));
                                        }
                                    }
                                });
                            } else {
                                tagGroupDao.deleteGroupHard(group.getId());
                            }
                        }
                    }
                }

                List<TagEntity> unsyncedTags = tagDao.getUnsyncedTags(userId);
                if (unsyncedTags != null) {
                    for (TagEntity tag : unsyncedTags) {
                        TagGroupEntity group = tagGroupDao.getGroupById(tag.getGroupId());
                        String groupObjId = group != null ? group.getObjectId() : null;

                        if (tag.getSyncState() == SyncState.TO_CREATE) {
                            bmobTagApi.uploadTag(tag, groupObjId, new cn.bmob.v3.listener.SaveListener<String>() {
                                @Override
                                public void done(String objectId, cn.bmob.v3.exception.BmobException e) {
                                    if (e == null && objectId != null) {
                                        tag.setObjectId(objectId);
                                        tag.setSyncState(SyncState.SYNCED);
                                        runInBackground(() -> tagDao.updateTag(tag));
                                    }
                                }
                            });
                        } else if (tag.getSyncState() == SyncState.TO_UPDATE) {
                            bmobTagApi.updateTag(tag, groupObjId, new cn.bmob.v3.listener.UpdateListener() {
                                @Override
                                public void done(cn.bmob.v3.exception.BmobException e) {
                                    if (e == null) {
                                        tag.setSyncState(SyncState.SYNCED);
                                        runInBackground(() -> tagDao.updateTag(tag));
                                    }
                                }
                            });
                        } else if (tag.getSyncState() == SyncState.TO_DELETE) {
                            if (tag.getObjectId() != null) {
                                bmobTagApi.deleteTag(tag.getObjectId(), new cn.bmob.v3.listener.UpdateListener() {
                                    @Override
                                    public void done(cn.bmob.v3.exception.BmobException e) {
                                        if (e == null) {
                                            runInBackground(() -> tagDao.deleteTagHard(tag.getId()));
                                        }
                                    }
                                });
                            } else {
                                tagDao.deleteTagHard(tag.getId());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "uploadPendingChanges exception", e);
            }
        });
    }
}
