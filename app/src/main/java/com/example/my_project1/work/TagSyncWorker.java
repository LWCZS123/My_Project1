package com.example.my_project1.work;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.my_project1.data.dao.BillDao;
import com.example.my_project1.data.dao.BillTagCrossRefDao;
import com.example.my_project1.data.dao.TagDao;
import com.example.my_project1.data.dao.TagGroupDao;
import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.data.model.tag.BillTagCrossRef;
import com.example.my_project1.data.model.tag.TagEntity;
import com.example.my_project1.data.model.tag.TagGroupEntity;
import com.example.my_project1.data.remote.model.cloudtag.BmobTagApiImpl;
import com.example.my_project1.data.remote.model.cloudtag.CloudBillTagCrossRef;
import com.example.my_project1.data.remote.model.cloudtag.CloudTag;
import com.example.my_project1.data.remote.model.cloudtag.CloudTagGroup;

import java.util.Date;
import java.util.List;

import cn.bmob.v3.BmobUser;

/**
 * 标签模块后台同步 Task (WorkManager Worker)
 * 遵循离线优先原则与推拉双向同步：
 * 1. 处理本地逻辑标记删除的数据（同步删除云端并清理本地记录）
 * 2. 处理本地待新建或待修改的数据（上传或更新至云端，并回写 objectId）
 * 3. 拉取云端最新数据（合并云端标签组、标签、账单关联关系至本地）
 */
public class TagSyncWorker extends Worker {

    /** WorkManager 任务唯一标识名称 */
    public static final String WORK_NAME = "TagSync";

    /** 日志标签 */
    private static final String TAG = "TagSyncWorker";

    /** 最大重试次数 */
    private static final int MAX_RETRIES = 3;

    /** 数据库实例 */
    private final AppDatabase database;

    /** 标签分组 DAO */
    private final TagGroupDao tagGroupDao;

    /** 标签 DAO */
    private final TagDao tagDao;

    /** 账单标签交叉关联表 DAO */
    private final BillTagCrossRefDao crossRefDao;

    /** 账单 DAO */
    private final BillDao billDao;

    /** Bmob 云端 API 服务实现类 */
    private final BmobTagApiImpl api;

    /**
     * 构造函数，初始化数据库访问对象与 Bmob 云端 API 服务
     *
     * @param context      上下文
     * @param workerParams Worker 参数
     */
    public TagSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);

        // 获取数据库单例
        database = AppDatabase.getInstance(context);

        // 初始化关联的所有 DAO 接口
        tagGroupDao = database.tagGroupDao();
        tagDao = database.tagDao();
        crossRefDao = database.billTagCrossRefDao();
        billDao = database.billDao();

        // 初始化 Bmob 网络接口类
        api = new BmobTagApiImpl(context);
    }

    /**
     * 获取 Worker 运行的默认约束（网络连接正常时才触发后台任务）
     *
     * @return 约束条件
     */
    public static Constraints getDefaultConstraints() {
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
    }

    /**
     * 将标签同步任务加入 WorkManager 队列执行（防止重复排队）
     *
     * @param context 上下文
     */
    public static void enqueue(Context context) {
        // 创建单次网络同步请求
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(TagSyncWorker.class)
                .setConstraints(getDefaultConstraints())
                .build();

        // 使用 REPLACE 策略将任务提交给 WorkManager 管理，确保最新任务能立刻响应
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request);
    }

    /**
     * 执行后台同步主逻辑
     *
     * @return 同步结果（成功、失败、需要重试）
     */
    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Executing TagSyncWorker background sync...");

        // 检查用户登录状态，未登录时跳过同步
        BmobUser user = BmobUser.getCurrentUser();
        if (user == null) {
            Log.w(TAG, "用户未登录，跳过标签同步");
            return Result.success();
        }

        String userId = user.getObjectId();

        try {
            Log.i(TAG, "开始标签模块同步任务");

            // 1. 同步物理/逻辑删除的数据（由底向上：交叉关联 -> 标签 -> 标签组）
            syncCrossRefDeletes(userId);
            syncTagDeletes(userId);
            syncGroupDeletes(userId);

            // 2. 拉取云端数据合并至本地 SQLite 数据库，并检查云端数据表重建/重置情况
            mergeCloud(userId);

            // 3. 同步写入本地新建或更改的数据（由顶向下：标签组 -> 标签 -> 交叉关联）
            syncGroupWrites(userId);
            syncTagWrites(userId);
            syncCrossRefWrites(userId);

            Log.i(TAG, "标签模块同步任务完成");
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "标签同步异常: " + e.getMessage(), e);

            // 异常时根据重试次数判断是重试还是标记失败
            return getRunAttemptCount() < MAX_RETRIES ? Result.retry() : Result.failure();
        }
    }

    // ==================== 1. 同步删除逻辑 ====================

    /**
     * 同步账单与标签关联记录的删除
     *
     * @param userId 用户ID
     */
    private void syncCrossRefDeletes(String userId) {
        // 查询本地标记为待删除的关联记录
        List<BillTagCrossRef> toDelete = crossRefDao.getToDeleteCrossRefs(userId);
        if (toDelete == null || toDelete.isEmpty()) return;

        for (BillTagCrossRef ref : toDelete) {
            // 根据本地ID查找父账单与父标签的云端 objectId
            Bill bill = billDao.getByIdSync(ref.getBillId());
            TagEntity tag = tagDao.getTagById(ref.getTagId());
            String billObjId = bill != null ? bill.getObjectId() : null;
            String tagObjId = tag != null ? tag.getObjectId() : null;

            boolean success = true;
            // 若云端ID有效，发送网络请求删除云端交叉关联记录
            if (billObjId != null && tagObjId != null) {
                success = api.deleteCrossRefSync(billObjId, tagObjId);
            }

            // 云端删除成功后，物理清理本地记录
            if (success) {
                crossRefDao.deleteCrossRefHard(ref.getBillId(), ref.getTagId());
                Log.d(TAG, "物理删除本地账单标签关联成功: BillId=" + ref.getBillId() + ", TagId=" + ref.getTagId());
            }
        }
    }

    /**
     * 同步标签的删除
     *
     * @param userId 用户ID
     */
    private void syncTagDeletes(String userId) {
        // 查询本地标记为待删除的标签
        List<TagEntity> toDelete = tagDao.getToDeleteTags(userId);
        if (toDelete == null || toDelete.isEmpty()) return;

        for (TagEntity tag : toDelete) {
            boolean success = true;
            // 删除云端存储的标签记录
            if (tag.getObjectId() != null && !tag.getObjectId().isEmpty()) {
                success = api.deleteTagSync(tag.getObjectId());
            }

            // 云端成功后，清理本地交叉关联表并物理删除本地标签
            if (success) {
                crossRefDao.deleteByTagIdHard(tag.getId());
                tagDao.deleteTagHard(tag.getId());
                Log.d(TAG, "物理删除本地标签成功: ID=" + tag.getId());
            }
        }
    }

    /**
     * 同步标签分组的删除
     *
     * @param userId 用户ID
     */
    private void syncGroupDeletes(String userId) {
        // 查询本地标记为待删除的标签分组
        List<TagGroupEntity> toDelete = tagGroupDao.getToDeleteGroups(userId);
        if (toDelete == null || toDelete.isEmpty()) return;

        for (TagGroupEntity group : toDelete) {
            boolean success = true;
            // 删除云端对应的 TagGroup 记录
            if (group.getObjectId() != null && !group.getObjectId().isEmpty()) {
                success = api.deleteTagGroupSync(group.getObjectId());
            }

            // 云端删除成功后，物理删除本地数据库中的标签分组记录
            if (success) {
                tagGroupDao.deleteGroupHard(group.getId());
                Log.d(TAG, "物理删除本地标签组成功: ID=" + group.getId());
            }
        }
    }

    // ==================== 2. 同步写入逻辑（上传/更新） ====================

    /**
     * 同步本地待上传或更新的标签分组
     *
     * @param userId 用户ID
     */
    private void syncGroupWrites(String userId) {
        // 查找同步状态不为 SYNCED 的标签分组
        List<TagGroupEntity> pending = tagGroupDao.getPendingSyncGroups(userId);
        if (pending == null || pending.isEmpty()) return;

        for (TagGroupEntity group : pending) {
            // 跳过已删除的分组（由删除逻辑统一处理）
            if (group.isDeleted() || group.getSyncState() == SyncState.TO_DELETE) continue;

            // 上传或更新至 Bmob 云端
            boolean ok = api.uploadTagGroupSync(group);
            if (ok) {
                // 上传成功后将本地 syncState 修改为 SYNCED，并更新云端返回的 objectId
                group.setSyncState(SyncState.SYNCED);
                tagGroupDao.updateGroup(group);
                Log.d(TAG, "同步标签组成功: " + group.getName() + " (ObjectId: " + group.getObjectId() + ")");
            } else {
                Log.w(TAG, "同步标签组失败: " + group.getName());
            }
        }
    }

    /**
     * 同步本地待上传或更新的标签
     *
     * @param userId 用户ID
     */
    private void syncTagWrites(String userId) {
        // 查找待同步的标签
        List<TagEntity> pending = tagDao.getPendingSyncTags(userId);
        if (pending == null || pending.isEmpty()) return;

        for (TagEntity tag : pending) {
            // 跳过已删除的标签
            if (tag.isDeleted() || tag.getSyncState() == SyncState.TO_DELETE) continue;

            // 查找标签所属分组的云端 objectId，保持云端外键关联
            TagGroupEntity group = tagGroupDao.getGroupById(tag.getGroupId());
            String groupObjId = group != null ? group.getObjectId() : null;

            // 上传标签到云端
            boolean ok = api.uploadTagSync(tag, groupObjId);
            if (ok) {
                // 上传成功，将同步状态标记为 SYNCED 并持久化本地
                tag.setSyncState(SyncState.SYNCED);
                tagDao.updateTag(tag);
                Log.d(TAG, "同步标签成功: " + tag.getName() + " (ObjectId: " + tag.getObjectId() + ")");
            } else {
                Log.w(TAG, "同步标签失败: " + tag.getName());
            }
        }
    }

    /**
     * 同步本地待上传或更新的账单标签关联关系
     *
     * @param userId 用户ID
     */
    private void syncCrossRefWrites(String userId) {
        // 查找待同步的关联记录
        List<BillTagCrossRef> pending = crossRefDao.getPendingSyncCrossRefs(userId);
        if (pending == null || pending.isEmpty()) return;

        for (BillTagCrossRef ref : pending) {
            // 跳过标记为删除的关联记录
            if (ref.isDeleted() || ref.getSyncState() == SyncState.TO_DELETE) continue;

            // 检查关联的本地账单与标签是否都已产生云端 objectId
            Bill bill = billDao.getByIdSync(ref.getBillId());
            TagEntity tag = tagDao.getTagById(ref.getTagId());

            if (bill == null || bill.getObjectId() == null || bill.getObjectId().isEmpty()) {
                Log.w(TAG, "账单标签关联跳过上传，因为无法找到父账单的 objectId: BillId=" + ref.getBillId());
                continue;
            }
            if (tag == null || tag.getObjectId() == null || tag.getObjectId().isEmpty()) {
                Log.w(TAG, "账单标签关联跳过上传，因为无法找到标签的 objectId: TagId=" + ref.getTagId());
                continue;
            }

            // 同步关联信息到云端
            boolean ok = api.uploadCrossRefSync(ref, bill.getObjectId(), tag.getObjectId());
            if (ok) {
                // 上传成功，标记为已同步
                ref.setSyncState(SyncState.SYNCED);
                crossRefDao.updateCrossRef(ref);
                Log.d(TAG, "同步账单标签关联成功: BillId=" + ref.getBillId() + ", TagId=" + ref.getTagId());
            } else {
                Log.w(TAG, "同步账单标签关联失败: BillId=" + ref.getBillId() + ", TagId=" + ref.getTagId());
            }
        }
    }

    // ==================== 3. 拉取与合并云端数据逻辑 ====================

    /**
     * 拉取云端标签组、标签及账单关联，并合并至本地数据库
     *
     * @param userId 用户ID
     */
    private void mergeCloud(String userId) {
        // 1. 获取云端所有标签分组列表，并在数据库事务中合并
        List<CloudTagGroup> cloudGroups = api.fetchTagGroupsSync();
        if (cloudGroups != null) {
            database.runInTransaction(() -> {
                java.util.Set<String> cloudGroupObjectIds = new java.util.HashSet<>();
                for (CloudTagGroup cloud : cloudGroups) {
                    if (cloud.getObjectId() != null && !cloud.getObjectId().isEmpty()) {
                        cloudGroupObjectIds.add(cloud.getObjectId());
                    }
                    mergeGroup(cloud, userId);
                }
                // 检查本地分组：如果本地标为 SYNCED 且 objectId 不在云端中（如云端表被清空/重建），重置为待同步
                List<TagGroupEntity> localGroups = tagGroupDao.getActiveGroupsSync(userId);
                if (localGroups != null) {
                    for (TagGroupEntity local : localGroups) {
                        if (local.getSyncState() == SyncState.SYNCED && local.getObjectId() != null && !local.getObjectId().isEmpty()) {
                            if (!cloudGroupObjectIds.contains(local.getObjectId())) {
                                Log.d(TAG, "本地标签组在云端不存在，重置对象ID并重新同步: " + local.getName());
                                local.setObjectId(null);
                                local.setSyncState(SyncState.TO_CREATE);
                                tagGroupDao.updateGroup(local);
                            }
                        }
                    }
                }
            });
        }

        // 2. 获取云端所有标签列表，并在数据库事务中合并
        List<CloudTag> cloudTags = api.fetchTagsSync();
        if (cloudTags != null) {
            database.runInTransaction(() -> {
                java.util.Set<String> cloudTagObjectIds = new java.util.HashSet<>();
                for (CloudTag cloud : cloudTags) {
                    if (cloud.getObjectId() != null && !cloud.getObjectId().isEmpty()) {
                        cloudTagObjectIds.add(cloud.getObjectId());
                    }
                    mergeTag(cloud, userId);
                }
                // 检查本地标签：如果本地标为 SYNCED 且 objectId 不在云端中，重置为待同步
                List<TagEntity> localTags = tagDao.getAllActiveTagsSync(userId);
                if (localTags != null) {
                    for (TagEntity local : localTags) {
                        if (local.getSyncState() == SyncState.SYNCED && local.getObjectId() != null && !local.getObjectId().isEmpty()) {
                            if (!cloudTagObjectIds.contains(local.getObjectId())) {
                                Log.d(TAG, "本地标签在云端不存在，重置对象ID并重新同步: " + local.getName());
                                local.setObjectId(null);
                                local.setSyncState(SyncState.TO_CREATE);
                                tagDao.updateTag(local);
                            }
                        }
                    }
                }
            });
        }

        // 3. 获取云端所有账单标签关联列表，并在数据库事务中合并
        List<CloudBillTagCrossRef> cloudCrossRefs = api.fetchCrossRefsSync();
        if (cloudCrossRefs != null) {
            database.runInTransaction(() -> {
                java.util.Set<String> cloudRefKeys = new java.util.HashSet<>();
                for (CloudBillTagCrossRef cloud : cloudCrossRefs) {
                    if (cloud.getBillObjectId() != null && cloud.getTagObjectId() != null) {
                        cloudRefKeys.add(cloud.getBillObjectId() + "_" + cloud.getTagObjectId());
                    }
                    mergeCrossRef(cloud, userId);
                }
                // 检查本地关联：如果本地标为 SYNCED 但在云端未查到关联关系，重置为待同步
                List<BillTagCrossRef> localRefs = crossRefDao.getAllActiveCrossRefsSync(userId);
                if (localRefs != null) {
                    for (BillTagCrossRef ref : localRefs) {
                        if (ref.getSyncState() == SyncState.SYNCED) {
                            Bill bill = billDao.getByIdSync(ref.getBillId());
                            TagEntity tag = tagDao.getTagById(ref.getTagId());
                            String bObjId = bill != null ? bill.getObjectId() : null;
                            String tObjId = tag != null ? tag.getObjectId() : null;
                            if (bObjId == null || tObjId == null || !cloudRefKeys.contains(bObjId + "_" + tObjId)) {
                                Log.d(TAG, "本地账单标签关联在云端不存在，准备重新同步: BillId=" + ref.getBillId() + ", TagId=" + ref.getTagId());
                                ref.setSyncState(SyncState.TO_CREATE);
                                crossRefDao.updateCrossRef(ref);
                            }
                        }
                    }
                }
            });
        }
    }

    /**
     * 合并单条云端标签组至本地 SQLite 数据库
     *
     * @param cloud  云端标签组模型
     * @param userId 用户ID
     */
    private void mergeGroup(CloudTagGroup cloud, String userId) {
        if (cloud == null) return;

        TagGroupEntity incoming = cloud.toLocalEntity();
        incoming.setUserId(userId);
        incoming.setSyncState(SyncState.SYNCED);

        if (cloud.isDeleted()) {
            TagGroupEntity local = tagGroupDao.getGroupByObjectIdSync(cloud.getObjectId());
            if (local != null) {
                TagGroupEntity defaultGroup = tagGroupDao.getDefaultGroup(userId);
                if (defaultGroup != null && defaultGroup.getId() != local.getId()) {
                    tagDao.moveTagsToGroup(userId, local.getId(), defaultGroup.getId());
                }
                tagGroupDao.deleteGroupHard(local.getId());
            }
            return;
        }

        TagGroupEntity local = tagGroupDao.getGroupByObjectIdSync(cloud.getObjectId());
        if (local == null) {
            TagGroupEntity sameName = tagGroupDao.getGroupByName(userId, incoming.getName());
            if (sameName != null) {
                sameName.setObjectId(cloud.getObjectId());
                sameName.setSortOrder(incoming.getSortOrder());
                sameName.setDefault(incoming.isDefault());
                sameName.setDeleted(false);
                sameName.setSyncState(SyncState.SYNCED);
                tagGroupDao.updateGroup(sameName);
                Log.d(TAG, "合并云端标签组 - 关联已存在的同名本地组: " + sameName.getName());
            } else {
                incoming.setId(0);
                tagGroupDao.insertGroup(incoming);
                Log.d(TAG, "合并云端标签组 - 新增本地组: " + incoming.getName());
            }
        } else {
            if (local.getSyncState() == SyncState.SYNCED && isCloudNewer(incoming.getUpdatedAt(), local.getUpdatedAt())) {
                incoming.setId(local.getId());
                tagGroupDao.updateGroup(incoming);
                Log.d(TAG, "合并云端标签组 - 更新本地组: " + incoming.getName());
            }
        }
    }

    /**
     * 合并单条云端标签至本地 SQLite 数据库
     *
     * @param cloud  云端标签模型
     * @param userId 用户ID
     */
    private void mergeTag(CloudTag cloud, String userId) {
        if (cloud == null) return;

        if (cloud.isDeleted()) {
            TagEntity local = tagDao.getTagByObjectIdSync(cloud.getObjectId());
            if (local != null) {
                crossRefDao.deleteByTagIdHard(local.getId());
                tagDao.deleteTagHard(local.getId());
            }
            return;
        }

        long localGroupId = 0;
        if (cloud.getGroupObjectId() != null && !cloud.getGroupObjectId().isEmpty()) {
            TagGroupEntity group = tagGroupDao.getGroupByObjectIdSync(cloud.getGroupObjectId());
            if (group != null) {
                localGroupId = group.getId();
            }
        }

        if (localGroupId <= 0) {
            TagGroupEntity defaultGroup = tagGroupDao.getDefaultGroup(userId);
            if (defaultGroup == null) {
                List<TagGroupEntity> groups = tagGroupDao.getActiveGroupsSync(userId);
                int sortOrder = groups != null ? groups.size() + 100 : 100;
                defaultGroup = new TagGroupEntity(userId, com.example.my_project1.data.repository.tag.TagRepository.DEFAULT_GROUP_NAME, sortOrder, true);
                long defId = tagGroupDao.insertGroup(defaultGroup);
                defaultGroup.setId(defId);
            }
            localGroupId = defaultGroup.getId();
        }

        TagEntity incoming = cloud.toLocalEntity(localGroupId);
        incoming.setUserId(userId);
        incoming.setSyncState(SyncState.SYNCED);

        TagEntity local = tagDao.getTagByObjectIdSync(cloud.getObjectId());
        if (local == null) {
            TagEntity sameName = tagDao.getTagByName(userId, incoming.getName());
            if (sameName != null) {
                sameName.setObjectId(cloud.getObjectId());
                sameName.setGroupId(localGroupId);
                sameName.setBgColor(incoming.getBgColor());
                sameName.setTextColor(incoming.getTextColor());
                sameName.setSortOrder(incoming.getSortOrder());
                sameName.setArchived(incoming.isArchived());
                sameName.setDeleted(false);
                sameName.setSyncState(SyncState.SYNCED);
                tagDao.updateTag(sameName);
                Log.d(TAG, "合并云端标签 - 关联已存在的同名本地标签: " + sameName.getName());
            } else {
                incoming.setId(0);
                tagDao.insertTag(incoming);
                Log.d(TAG, "合并云端标签 - 新增本地标签: " + incoming.getName());
            }
        } else {
            if (local.getSyncState() == SyncState.SYNCED) {
                if (isCloudNewer(incoming.getUpdatedAt(), local.getUpdatedAt()) || local.getGroupId() != localGroupId || local.isArchived() != incoming.isArchived()) {
                    incoming.setId(local.getId());
                    tagDao.updateTag(incoming);
                    Log.d(TAG, "合并云端标签 - 更新本地标签: " + incoming.getName());
                }
            }
        }
    }

    /**
     * 合并单条云端账单标签关联至本地交叉表
     *
     * @param cloud  云端账单标签关联模型
     * @param userId 用户ID
     */
    private void mergeCrossRef(CloudBillTagCrossRef cloud, String userId) {
        if (cloud == null || cloud.getBillObjectId() == null || cloud.getTagObjectId() == null) return;

        Bill bill = billDao.getBillByObjectIdSync(cloud.getBillObjectId());
        TagEntity tag = tagDao.getTagByObjectIdSync(cloud.getTagObjectId());
        if (tag == null && cloud.getTagName() != null) {
            tag = tagDao.getTagByName(userId, cloud.getTagName());
        }

        if (cloud.isDeleted()) {
            if (bill != null && tag != null) {
                crossRefDao.deleteCrossRefHard(bill.getId(), tag.getId());
            }
            return;
        }

        if (bill == null || tag == null) {
            Log.w(TAG, "跳过合并云端账单标签关联，本地无对应的 Bill 或 Tag: BillObjId=" + cloud.getBillObjectId() + ", TagObjId=" + cloud.getTagObjectId());
            return;
        }

        BillTagCrossRef localRef = crossRefDao.getCrossRefSync(bill.getId(), tag.getId());
        if (localRef == null) {
            BillTagCrossRef incoming = cloud.toLocalEntity(bill.getId(), tag.getId());
            incoming.setUserId(userId);
            crossRefDao.insertCrossRef(incoming);
            Log.d(TAG, "合并云端账单标签关联 - 新增本地关联: BillId=" + bill.getId() + ", TagId=" + tag.getId());
        } else if (localRef.isDeleted()) {
            localRef.setDeleted(false);
            localRef.setSyncState(SyncState.SYNCED);
            crossRefDao.updateCrossRef(localRef);
        }
    }

    /**
     * 判断云端更新时间是否晚于本地更新时间
     *
     * @param cloudDate 云端更新时间
     * @param localDate 本地更新时间
     * @return 云端更新时间较晚返回 true
     */
    private boolean isCloudNewer(Date cloudDate, Date localDate) {
        if (cloudDate == null) return false;
        return localDate == null || cloudDate.after(localDate);
    }
}
