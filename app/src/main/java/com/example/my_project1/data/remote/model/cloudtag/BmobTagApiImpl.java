package com.example.my_project1.data.remote.model.cloudtag;

import android.content.Context;
import android.util.Log;

import com.example.my_project1.data.model.tag.BillTagCrossRef;
import com.example.my_project1.data.model.tag.TagEntity;
import com.example.my_project1.data.model.tag.TagGroupEntity;
import com.example.my_project1.utils.BmobPointerUtil;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import cn.bmob.v3.BmobQuery;
import cn.bmob.v3.BmobUser;
import cn.bmob.v3.exception.BmobException;
import cn.bmob.v3.listener.FindListener;
import cn.bmob.v3.listener.SaveListener;
import cn.bmob.v3.listener.UpdateListener;

/**
 * 标签模块 Bmob 云端 API 实现类
 */
public class BmobTagApiImpl {

    private static final String TAG = "BmobTagApiImpl";
    private final Context context;

    /**
     * 构造函数
     *
     * @param context 上下文
     */
    public BmobTagApiImpl(Context context) {
        this.context = context != null ? context.getApplicationContext() : null;
    }

    /**
     * 判断异常是否为 Bmob 表不存在或对象未找到 (错误码 101)
     *
     * @param e 异常对象
     * @return 若为 101 表未找到错误，返回 true
     */
    public static boolean isTableNotFoundException(Throwable e) {
        if (e == null) return false;
        if (e instanceof BmobException) {
            BmobException be = (BmobException) e;
            if (be.getErrorCode() == 101) return true;
        }
        String msg = e.getMessage();
        if (msg != null && (msg.contains("101") || msg.contains("object not found"))) {
            return true;
        }
        if (e.getCause() != null && e.getCause() != e) {
            return isTableNotFoundException(e.getCause());
        }
        return false;
    }

    /**
     * 获取当前登录用户的 objectId
     *
     * @return 当前用户ID，未登录返回 null
     */
    public String getCurrentUserId() {
        BmobUser user = BmobUser.getCurrentUser();
        return user != null ? user.getObjectId() : null;
    }

    // ==================== 标签组异步 API ====================

    /**
     * 异步新增标签组到云端
     *
     * @param localGroup 本地标签组实体
     * @param listener   回调监听器
     */
    public void uploadTagGroup(TagGroupEntity localGroup, SaveListener<String> listener) {
        CloudTagGroup cloud = CloudTagGroup.fromLocal(localGroup);
        cloud.setUser(BmobPointerUtil.user(getCurrentUserId()));
        cloud.save(new SaveListener<String>() {
            @Override
            public void done(String objectId, BmobException e) {
                if (e == null) {
                    listener.done(objectId, null);
                } else {
                    Log.e(TAG, "uploadTagGroup error: ", e);
                    listener.done(null, e);
                }
            }
        });
    }

    /**
     * 异步更新云端标签组
     *
     * @param localGroup 本地标签组实体
     * @param listener   回调监听器
     */
    public void updateTagGroup(TagGroupEntity localGroup, UpdateListener listener) {
        if (localGroup.getObjectId() == null || localGroup.getObjectId().isEmpty()) {
            listener.done(new BmobException(900, "objectId is null"));
            return;
        }
        CloudTagGroup cloud = CloudTagGroup.fromLocal(localGroup);
        cloud.update(localGroup.getObjectId(), listener);
    }

    /**
     * 异步删除云端标签组
     *
     * @param objectId 云端 objectId
     * @param listener 回调监听器
     */
    public void deleteTagGroup(String objectId, UpdateListener listener) {
        if (objectId == null || objectId.isEmpty()) {
            listener.done(new BmobException(901, "objectId is null"));
            return;
        }
        CloudTagGroup cloud = new CloudTagGroup();
        cloud.setObjectId(objectId);
        cloud.delete(listener);
    }

    /**
     * 异步获取当前用户的所有云端标签组
     *
     * @param listener 回调监听器
     */
    public void fetchTagGroups(FindListener<CloudTagGroup> listener) {
        String userId = getCurrentUserId();
        if (userId == null) {
            if (listener != null) listener.done(null, new BmobException(902, "User not logged in"));
            return;
        }
        BmobQuery<CloudTagGroup> query = new BmobQuery<>("CloudTagGroup");
        query.addWhereEqualTo("user", BmobPointerUtil.user(userId));
        query.setLimit(500);
        query.findObjects(new FindListener<CloudTagGroup>() {
            @Override
            public void done(List<CloudTagGroup> list, BmobException e) {
                if (e != null && isTableNotFoundException(e)) {
                    Log.d(TAG, "CloudTagGroup 表在云端尚未建立或无记录 (101)，返回空列表");
                    if (listener != null) listener.done(new java.util.ArrayList<>(), null);
                } else {
                    if (listener != null) listener.done(list, e);
                }
            }
        });
    }

    // ==================== 标签异步 API ====================

    /**
     * 异步新增标签到云端
     *
     * @param localTag      本地标签实体
     * @param groupObjectId 所属分组的云端 objectId
     * @param listener      回调监听器
     */
    public void uploadTag(TagEntity localTag, String groupObjectId, SaveListener<String> listener) {
        CloudTag cloud = CloudTag.fromLocal(localTag, groupObjectId);
        cloud.setUser(BmobPointerUtil.user(getCurrentUserId()));
        cloud.save(new SaveListener<String>() {
            @Override
            public void done(String objectId, BmobException e) {
                if (e == null) {
                    listener.done(objectId, null);
                } else {
                    Log.e(TAG, "uploadTag error: ", e);
                    listener.done(null, e);
                }
            }
        });
    }

    /**
     * 异步更新云端标签
     *
     * @param localTag      本地标签实体
     * @param groupObjectId 所属分组的云端 objectId
     * @param listener      回调监听器
     */
    public void updateTag(TagEntity localTag, String groupObjectId, UpdateListener listener) {
        if (localTag.getObjectId() == null || localTag.getObjectId().isEmpty()) {
            listener.done(new BmobException(900, "objectId is null"));
            return;
        }
        CloudTag cloud = CloudTag.fromLocal(localTag, groupObjectId);
        cloud.update(localTag.getObjectId(), listener);
    }

    /**
     * 异步删除云端标签
     *
     * @param objectId 云端 objectId
     * @param listener 回调监听器
     */
    public void deleteTag(String objectId, UpdateListener listener) {
        if (objectId == null || objectId.isEmpty()) {
            listener.done(new BmobException(901, "objectId is null"));
            return;
        }
        CloudTag cloud = new CloudTag();
        cloud.setObjectId(objectId);
        cloud.delete(listener);
    }

    /**
     * 异步获取当前用户的所有云端标签
     *
     * @param listener 回调监听器
     */
    public void fetchTags(FindListener<CloudTag> listener) {
        String userId = getCurrentUserId();
        if (userId == null) {
            if (listener != null) listener.done(null, new BmobException(902, "User not logged in"));
            return;
        }
        BmobQuery<CloudTag> query = new BmobQuery<>("CloudTag");
        query.addWhereEqualTo("user", BmobPointerUtil.user(userId));
        query.setLimit(500);
        query.findObjects(new FindListener<CloudTag>() {
            @Override
            public void done(List<CloudTag> list, BmobException e) {
                if (e != null && isTableNotFoundException(e)) {
                    Log.d(TAG, "CloudTag 表在云端尚未建立或无记录 (101)，返回空列表");
                    if (listener != null) listener.done(new java.util.ArrayList<>(), null);
                } else {
                    if (listener != null) listener.done(list, e);
                }
            }
        });
    }

    // ==================== 账单关联异步 API ====================

    /**
     * 异步新增账单标签关联到云端
     *
     * @param ref          本地关联实体
     * @param billObjectId 账单云端 objectId
     * @param tagObjectId  标签云端 objectId
     * @param listener     回调监听器
     */
    public void uploadCrossRef(BillTagCrossRef ref, String billObjectId, String tagObjectId, SaveListener<String> listener) {
        CloudBillTagCrossRef cloud = CloudBillTagCrossRef.fromLocal(ref, billObjectId, tagObjectId);
        cloud.setUser(BmobPointerUtil.user(getCurrentUserId()));
        cloud.save(new SaveListener<String>() {
            @Override
            public void done(String objectId, BmobException e) {
                if (e == null) {
                    listener.done(objectId, null);
                } else {
                    Log.e(TAG, "uploadCrossRef error: ", e);
                    listener.done(null, e);
                }
            }
        });
    }

    /**
     * 异步获取当前用户的所有账单标签关联
     *
     * @param listener 回调监听器
     */
    public void fetchCrossRefs(FindListener<CloudBillTagCrossRef> listener) {
        String userId = getCurrentUserId();
        if (userId == null) {
            if (listener != null) listener.done(null, new BmobException(902, "User not logged in"));
            return;
        }
        BmobQuery<CloudBillTagCrossRef> query = new BmobQuery<>("CloudBillTagCrossRef");
        query.addWhereEqualTo("user", BmobPointerUtil.user(userId));
        query.setLimit(1000);
        query.findObjects(new FindListener<CloudBillTagCrossRef>() {
            @Override
            public void done(List<CloudBillTagCrossRef> list, BmobException e) {
                if (e != null && isTableNotFoundException(e)) {
                    Log.d(TAG, "CloudBillTagCrossRef 表在云端尚未建立或无记录 (101)，返回空列表");
                    if (listener != null) listener.done(new java.util.ArrayList<>(), null);
                } else {
                    if (listener != null) listener.done(list, e);
                }
            }
        });
    }

    // ==================== 标签组同步阻塞 API ====================

    /**
     * 同步（阻塞）保存或更新标签组到云端
     *
     * @param localGroup 本地标签组实体
     * @return 执行成功返回 true，失败返回 false
     */
    public boolean uploadTagGroupSync(TagGroupEntity localGroup) {
        if (localGroup == null) return false;
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] result = {false};

        if (localGroup.getObjectId() != null && !localGroup.getObjectId().isEmpty()) {
            // 已有云端 ID 则执行更新
            updateTagGroup(localGroup, new UpdateListener() {
                @Override
                public void done(BmobException e) {
                    if (e == null) {
                        result[0] = true;
                        latch.countDown();
                    } else if (isTableNotFoundException(e)) {
                        // 云端数据不存在，重置 ID 并重新上传
                        localGroup.setObjectId(null);
                        uploadTagGroup(localGroup, new SaveListener<String>() {
                            @Override
                            public void done(String objectId, BmobException ex) {
                                if (ex == null && objectId != null) {
                                    localGroup.setObjectId(objectId);
                                    result[0] = true;
                                } else {
                                    Log.e(TAG, "Re-upload tag group failed: ", ex);
                                }
                                latch.countDown();
                            }
                        });
                    } else {
                        Log.e(TAG, "updateTagGroup failed: ", e);
                        latch.countDown();
                    }
                }
            });
        } else {
            // 没有云端 ID 则新建上传
            uploadTagGroup(localGroup, new SaveListener<String>() {
                @Override
                public void done(String objectId, BmobException e) {
                    if (e == null && objectId != null) {
                        localGroup.setObjectId(objectId);
                        result[0] = true;
                    } else {
                        Log.e(TAG, "uploadTagGroup failed: ", e);
                    }
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result[0];
    }

    /**
     * 同步（阻塞）删除云端标签组
     *
     * @param objectId 云端 objectId
     * @return 执行成功或目标不存在返回 true
     */
    public boolean deleteTagGroupSync(String objectId) {
        if (objectId == null || objectId.isEmpty()) return true;
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] result = {false};

        deleteTagGroup(objectId, new UpdateListener() {
            @Override
            public void done(BmobException e) {
                if (e == null || isTableNotFoundException(e)) {
                    result[0] = true;
                } else {
                    Log.e(TAG, "deleteTagGroup failed: ", e);
                }
                latch.countDown();
            }
        });

        try {
            latch.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result[0];
    }

    /**
     * 同步（阻塞）获取当前用户的云端标签组列表
     *
     * @return 标签组列表，失败返回 null
     */
    public List<CloudTagGroup> fetchTagGroupsSync() {
        String userId = getCurrentUserId();
        if (userId == null) return null;
        try {
            BmobQuery<CloudTagGroup> query = new BmobQuery<>("CloudTagGroup");
            query.addWhereEqualTo("user", BmobPointerUtil.user(userId));
            query.setLimit(500);
            return query.findObjectsSync(CloudTagGroup.class);
        } catch (Exception e) {
            if (isTableNotFoundException(e)) {
                Log.d(TAG, "CloudTagGroup 表在云端尚未建立或没有记录 (101)，返回空列表");
                return new java.util.ArrayList<>();
            }
            Log.e(TAG, "fetchTagGroupsSync error: " + e.getMessage(), e);
            return null;
        }
    }

    // ==================== 标签同步阻塞 API ====================

    /**
     * 同步（阻塞）保存或更新标签到云端
     *
     * @param localTag      本地标签实体
     * @param groupObjectId 所属分组的云端 objectId
     * @return 执行成功返回 true
     */
    public boolean uploadTagSync(TagEntity localTag, String groupObjectId) {
        if (localTag == null) return false;
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] result = {false};

        if (localTag.getObjectId() != null && !localTag.getObjectId().isEmpty()) {
            updateTag(localTag, groupObjectId, new UpdateListener() {
                @Override
                public void done(BmobException e) {
                    if (e == null) {
                        result[0] = true;
                        latch.countDown();
                    } else if (isTableNotFoundException(e)) {
                        localTag.setObjectId(null);
                        uploadTag(localTag, groupObjectId, new SaveListener<String>() {
                            @Override
                            public void done(String objectId, BmobException ex) {
                                if (ex == null && objectId != null) {
                                    localTag.setObjectId(objectId);
                                    result[0] = true;
                                } else {
                                    Log.e(TAG, "Re-upload tag failed: ", ex);
                                }
                                latch.countDown();
                            }
                        });
                    } else {
                        Log.e(TAG, "updateTag failed: ", e);
                        latch.countDown();
                    }
                }
            });
        } else {
            uploadTag(localTag, groupObjectId, new SaveListener<String>() {
                @Override
                public void done(String objectId, BmobException e) {
                    if (e == null && objectId != null) {
                        localTag.setObjectId(objectId);
                        result[0] = true;
                    } else {
                        Log.e(TAG, "uploadTag failed: ", e);
                    }
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result[0];
    }

    /**
     * 同步（阻塞）删除云端标签
     *
     * @param objectId 云端 objectId
     * @return 执行成功或目标不存在返回 true
     */
    public boolean deleteTagSync(String objectId) {
        if (objectId == null || objectId.isEmpty()) return true;
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] result = {false};

        deleteTag(objectId, new UpdateListener() {
            @Override
            public void done(BmobException e) {
                if (e == null || isTableNotFoundException(e)) {
                    result[0] = true;
                } else {
                    Log.e(TAG, "deleteTag failed: ", e);
                }
                latch.countDown();
            }
        });

        try {
            latch.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result[0];
    }

    /**
     * 同步（阻塞）获取当前用户的云端标签列表
     *
     * @return 标签列表，失败返回 null
     */
    public List<CloudTag> fetchTagsSync() {
        String userId = getCurrentUserId();
        if (userId == null) return null;
        try {
            BmobQuery<CloudTag> query = new BmobQuery<>("CloudTag");
            query.addWhereEqualTo("user", BmobPointerUtil.user(userId));
            query.setLimit(500);
            return query.findObjectsSync(CloudTag.class);
        } catch (Exception e) {
            if (isTableNotFoundException(e)) {
                Log.d(TAG, "CloudTag 表在云端尚未建立或没有记录 (101)，返回空列表");
                return new java.util.ArrayList<>();
            }
            Log.e(TAG, "fetchTagsSync error: " + e.getMessage(), e);
            return null;
        }
    }

    // ==================== 账单关联同步阻塞 API ====================

    /**
     * 同步（阻塞）保存账单标签关联到云端
     *
     * @param ref          关联实体
     * @param billObjectId 账单云端 objectId
     * @param tagObjectId  标签云端 objectId
     * @return 执行成功返回 true
     */
    public boolean uploadCrossRefSync(BillTagCrossRef ref, String billObjectId, String tagObjectId) {
        if (ref == null || billObjectId == null || tagObjectId == null) return false;
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] result = {false};

        uploadCrossRef(ref, billObjectId, tagObjectId, new SaveListener<String>() {
            @Override
            public void done(String objectId, BmobException e) {
                if (e == null) {
                    result[0] = true;
                } else {
                    Log.e(TAG, "uploadCrossRef failed: ", e);
                }
                latch.countDown();
            }
        });

        try {
            latch.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result[0];
    }

    /**
     * 同步（阻塞）获取当前用户的账单标签关联列表
     *
     * @return 关联关系列表，失败返回 null
     */
    public List<CloudBillTagCrossRef> fetchCrossRefsSync() {
        String userId = getCurrentUserId();
        if (userId == null) return null;
        try {
            BmobQuery<CloudBillTagCrossRef> query = new BmobQuery<>("CloudBillTagCrossRef");
            query.addWhereEqualTo("user", BmobPointerUtil.user(userId));
            query.setLimit(1000);
            return query.findObjectsSync(CloudBillTagCrossRef.class);
        } catch (Exception e) {
            if (isTableNotFoundException(e)) {
                Log.d(TAG, "CloudBillTagCrossRef 表在云端尚未建立或没有记录 (101)，返回空列表");
                return new java.util.ArrayList<>();
            }
            Log.e(TAG, "fetchCrossRefsSync error: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * 同步（阻塞）按账单和标签的云端 objectId 删除云端关联关系
     *
     * @param billObjectId 账单云端 objectId
     * @param tagObjectId  标签云端 objectId
     * @return 删除成功或记录不存在返回 true
     */
    public boolean deleteCrossRefSync(String billObjectId, String tagObjectId) {
        if (billObjectId == null || tagObjectId == null) return true;
        String userId = getCurrentUserId();
        if (userId == null) return false;

        boolean[] result = {false};

        try {
            BmobQuery<CloudBillTagCrossRef> query = new BmobQuery<>("CloudBillTagCrossRef");
            query.addWhereEqualTo("user", BmobPointerUtil.user(userId));
            query.addWhereEqualTo("billObjectId", billObjectId);
            query.addWhereEqualTo("tagObjectId", tagObjectId);
            List<CloudBillTagCrossRef> list = query.findObjectsSync(CloudBillTagCrossRef.class);
            if (list != null && !list.isEmpty()) {
                CountDownLatch deleteLatch = new CountDownLatch(list.size());
                final boolean[] allDeleted = {true};
                for (CloudBillTagCrossRef cloudRef : list) {
                    cloudRef.delete(new UpdateListener() {
                        @Override
                        public void done(BmobException ex) {
                            if (ex != null && !isTableNotFoundException(ex)) {
                                allDeleted[0] = false;
                            }
                            deleteLatch.countDown();
                        }
                    });
                }
                try {
                    deleteLatch.await(15, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                result[0] = allDeleted[0];
            } else {
                result[0] = true;
            }
        } catch (Exception e) {
            if (isTableNotFoundException(e)) {
                result[0] = true;
            } else {
                Log.e(TAG, "deleteCrossRefSync error: " + e.getMessage(), e);
            }
        }
        return result[0];
    }
}
