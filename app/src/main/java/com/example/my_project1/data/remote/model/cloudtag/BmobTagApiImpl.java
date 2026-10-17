package com.example.my_project1.data.remote.model.cloudtag;

import android.content.Context;
import android.util.Log;

import com.example.my_project1.data.model.tag.BillTagCrossRef;
import com.example.my_project1.data.model.tag.TagEntity;
import com.example.my_project1.data.model.tag.TagGroupEntity;
import com.example.my_project1.utils.BmobPointerUtil;

import java.util.List;

import cn.bmob.v3.BmobQuery;
import cn.bmob.v3.BmobUser;
import cn.bmob.v3.exception.BmobException;
import cn.bmob.v3.listener.FindListener;
import cn.bmob.v3.listener.SaveListener;
import cn.bmob.v3.listener.UpdateListener;

public class BmobTagApiImpl {

    private static final String TAG = "BmobTagApiImpl";
    private final Context context;

    public BmobTagApiImpl(Context context) {
        this.context = context != null ? context.getApplicationContext() : null;
    }

    public String getCurrentUserId() {
        BmobUser user = BmobUser.getCurrentUser();
        return user != null ? user.getObjectId() : null;
    }

    // ==================== 标签组 ====================

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

    public void updateTagGroup(TagGroupEntity localGroup, UpdateListener listener) {
        if (localGroup.getObjectId() == null || localGroup.getObjectId().isEmpty()) {
            listener.done(new BmobException(900, "objectId is null"));
            return;
        }
        CloudTagGroup cloud = CloudTagGroup.fromLocal(localGroup);
        cloud.update(localGroup.getObjectId(), listener);
    }

    public void deleteTagGroup(String objectId, UpdateListener listener) {
        if (objectId == null || objectId.isEmpty()) {
            listener.done(new BmobException(901, "objectId is null"));
            return;
        }
        CloudTagGroup cloud = new CloudTagGroup();
        cloud.setObjectId(objectId);
        cloud.delete(listener);
    }

    public void fetchTagGroups(FindListener<CloudTagGroup> listener) {
        String userId = getCurrentUserId();
        if (userId == null) {
            listener.done(null, new BmobException(902, "User not logged in"));
            return;
        }
        BmobQuery<CloudTagGroup> query = new BmobQuery<>();
        query.addWhereEqualTo("user", BmobPointerUtil.user(userId));
        query.findObjects(listener);
    }

    // ==================== 标签 ====================

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

    public void updateTag(TagEntity localTag, String groupObjectId, UpdateListener listener) {
        if (localTag.getObjectId() == null || localTag.getObjectId().isEmpty()) {
            listener.done(new BmobException(900, "objectId is null"));
            return;
        }
        CloudTag cloud = CloudTag.fromLocal(localTag, groupObjectId);
        cloud.update(localTag.getObjectId(), listener);
    }

    public void deleteTag(String objectId, UpdateListener listener) {
        if (objectId == null || objectId.isEmpty()) {
            listener.done(new BmobException(901, "objectId is null"));
            return;
        }
        CloudTag cloud = new CloudTag();
        cloud.setObjectId(objectId);
        cloud.delete(listener);
    }

    public void fetchTags(FindListener<CloudTag> listener) {
        String userId = getCurrentUserId();
        if (userId == null) {
            listener.done(null, new BmobException(902, "User not logged in"));
            return;
        }
        BmobQuery<CloudTag> query = new BmobQuery<>();
        query.addWhereEqualTo("user", BmobPointerUtil.user(userId));
        query.findObjects(listener);
    }

    // ==================== 账单关联 ====================

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

    public void fetchCrossRefs(FindListener<CloudBillTagCrossRef> listener) {
        String userId = getCurrentUserId();
        if (userId == null) {
            listener.done(null, new BmobException(902, "User not logged in"));
            return;
        }
        BmobQuery<CloudBillTagCrossRef> query = new BmobQuery<>();
        query.addWhereEqualTo("user", BmobPointerUtil.user(userId));
        query.findObjects(listener);
    }
}
