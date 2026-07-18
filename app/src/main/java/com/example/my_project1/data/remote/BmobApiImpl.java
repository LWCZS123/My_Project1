package com.example.my_project1.data.remote;

import android.content.Context;
import android.util.Log;

import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.Category;
import com.example.my_project1.data.model.SubCategory;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.remote.model.CloudCategory;
import com.example.my_project1.data.remote.model.CloudSubCategory;

import java.util.List;

import cn.bmob.v3.BmobQuery;
import cn.bmob.v3.BmobUser;
import cn.bmob.v3.exception.BmobException;
import cn.bmob.v3.listener.FindListener;
import cn.bmob.v3.listener.SaveListener;
import cn.bmob.v3.listener.UpdateListener;

/**
 * Bmob 云端数据访问层
 * ------------------------------------------------------------------
 * 功能：
 *  - 上传 / 更新 / 删除 分类（Category / SubCategory）
 *  - 拉取当前用户所有分类数据
 *  - 与本地 Room 数据结构同步
 *
 * 注意：
 *  - 所有方法都是异步调用（通过 Bmob SDK 的回调）
 *  - 建议在 Repository 层封装为协程 / LiveData / Rx
 *  - Category / SubCategory 在 Bmob 云端应有对应表结构
 */
public class BmobApiImpl {

    private static final String TAG = "BmobApiImpl";

    private final Context context;
    private final AppDatabase db;

    // ✅ 带 Context 构造器
    public BmobApiImpl(Context context) {
        this.context = context.getApplicationContext();
        this.db = AppDatabase.getInstance(this.context);
    }

    // 无参构造器仍可保留，但调用 uploadCategorySync 时必须传 Context
    public BmobApiImpl() {
        this.context = null;
        this.db = null;
    }

    /** 获取当前登录用户对象（BmobUser） */
    private BmobUser getCurrentUser() {
        return BmobUser.getCurrentUser(BmobUser.class);
    }

    /** 获取当前登录用户的 objectId */
    private String getCurrentUserId() {
        BmobUser user = BmobUser.getCurrentUser(BmobUser.class);
        return user != null ? user.getObjectId() : null;
    }

    // ---------------------------------------------------------------------------------------------
    // Category（主分类）部分
    // ---------------------------------------------------------------------------------------------

    public void uploadCategory(Category localCategory, SaveListener<String> listener) {
        CloudCategory cloud = new CloudCategory();
        cloud.setOwnerId(getCurrentUserId());
        cloud.setLocalId(localCategory.getId());
        cloud.setType(localCategory.getType());
        cloud.setName(localCategory.getName());
        cloud.setIconUri(localCategory.getIconUri());
        cloud.setColor(localCategory.getColor());
        cloud.setOrder(localCategory.getOrder());

        cloud.save(new SaveListener<String>() {
            @Override
            public void done(String objectId, BmobException e) {
                if (e == null) {
                    Log.d(TAG, "✅ 上传分类成功: " + objectId);
                    localCategory.setCloudId(objectId);
                    localCategory.setSyncState(SyncState.SYNCED.getValue());
                    listener.done(objectId, null);
                } else {
                    Log.e(TAG, "❌ 上传分类失败: " + e.getMessage());
                    listener.done(null, e);
                }
            }
        });
    }

    public void updateCategory(Category localCategory, UpdateListener listener) {
        if (localCategory.getCloudId() == null) {
            listener.done(new BmobException(999, "cloudId为空，无法更新"));
            return;
        }

        CloudCategory cloud = new CloudCategory();
        cloud.setType(localCategory.getType());
        cloud.setName(localCategory.getName());
        cloud.setIconUri(localCategory.getIconUri());
        cloud.setColor(localCategory.getColor());
        cloud.setOrder(localCategory.getOrder());
        cloud.setLocalId(localCategory.getId());

        cloud.update(localCategory.getCloudId(), new UpdateListener() {
            @Override
            public void done(BmobException e) {
                if (e == null) {
                    Log.d(TAG, "✅ 更新分类成功: " + localCategory.getName());
                    localCategory.setSyncState(SyncState.SYNCED.getValue());
                    listener.done(null);
                } else {
                    Log.e(TAG, "❌ 更新分类失败: " + e.getMessage());
                    listener.done(e);
                }
            }
        });
    }

    public void deleteCategory(String cloudId, UpdateListener listener) {
        if (cloudId == null) {
            listener.done(new BmobException(997, "cloudId为空，无法删除"));
            return;
        }

        CloudCategory cloud = new CloudCategory();
        cloud.setObjectId(cloudId);
        cloud.delete(new UpdateListener() {
            @Override
            public void done(BmobException e) {
                if (e == null) {
                    Log.d(TAG, "✅ 删除分类成功: " + cloudId);
                } else {
                    Log.e(TAG, "❌ 删除分类失败: " + e.getMessage());
                }
                listener.done(e);
            }
        });
    }

    public void fetchCategories(FindListener<CloudCategory> listener) {
        String userId = getCurrentUserId();
        if (userId == null) {
            listener.done(null, new BmobException(998, "用户未登录"));
            return;
        }

        BmobQuery<CloudCategory> query = new BmobQuery<>();
        query.addWhereEqualTo("ownerId", userId);
        query.order("order");
        query.findObjects(listener);
    }

    // ---------------------------------------------------------------------------------------------
    // SubCategory（二级分类）部分
    // ---------------------------------------------------------------------------------------------

    public void uploadSubCategory(SubCategory local, SaveListener<String> listener) {
        BmobUser user = getCurrentUser();
        if (user == null) {
            listener.done(null, new BmobException(998, "用户未登录"));
            return;
        }

        CloudSubCategory cloud = CloudSubCategory.fromLocalSubCategory(local, local.getParentCloudId());
        cloud.setOwnerId(user);

        // 设置父分类 Pointer
        if (local.getParentCloudId() != null) {
            CloudCategory parent = new CloudCategory();
            parent.setObjectId(local.getParentCloudId());
            cloud.setParentCategory(parent);
        }

        cloud.save(new SaveListener<String>() {
            @Override
            public void done(String objectId, BmobException e) {
                if (e == null) {
                    Log.d(TAG, "✅ 上传子分类成功: " + objectId);
                    local.setCloudId(objectId);
                    local.setSyncState(SyncState.SYNCED.getValue());
                    listener.done(objectId, null);
                } else {
                    Log.e(TAG, "❌ 上传子分类失败: " + e.getMessage());
                    listener.done(null, e);
                }
            }
        });
    }

    public void updateSubCategory(SubCategory local, UpdateListener listener) {
        BmobUser user = getCurrentUser();
        if (user == null) {
            listener.done(new BmobException(998, "用户未登录"));
            return;
        }
        if (local.getCloudId() == null) {
            listener.done(new BmobException(996, "cloudId为空，无法更新子分类"));
            return;
        }

        CloudSubCategory cloud = CloudSubCategory.fromLocalSubCategory(local, local.getParentCloudId());
        cloud.setOwnerId(user);

        if (local.getParentCloudId() != null) {
            CloudCategory parent = new CloudCategory();
            parent.setObjectId(local.getParentCloudId());
            cloud.setParentCategory(parent);
        }

        cloud.update(local.getCloudId(), new UpdateListener() {
            @Override
            public void done(BmobException e) {
                if (e == null) {
                    Log.d(TAG, "✅ 更新子分类成功: " + local.getName());
                    local.setSyncState(SyncState.SYNCED.getValue());
                    listener.done(null);
                } else {
                    Log.e(TAG, "❌ 更新子分类失败: " + e.getMessage());
                    listener.done(e);
                }
            }
        });
    }

    public void deleteSubCategory(String cloudId, UpdateListener listener) {
        if (cloudId == null) {
            listener.done(new BmobException(995, "cloudId为空，无法删除子分类"));
            return;
        }

        CloudSubCategory cloud = new CloudSubCategory();
        cloud.setObjectId(cloudId);

        cloud.delete(new UpdateListener() {
            @Override
            public void done(BmobException e) {
                if (e == null) {
                    Log.d(TAG, "✅ 删除子分类成功: " + cloudId);
                } else {
                    Log.e(TAG, "❌ 删除子分类失败: " + e.getMessage());
                }
                listener.done(e);
            }
        });
    }

    public void fetchSubCategories(FindListener<CloudSubCategory> listener) {
        String userId = getCurrentUserId();
        if (userId == null) {
            listener.done(null, new BmobException(998, "用户未登录"));
            return;
        }

        BmobQuery<CloudSubCategory> query = new BmobQuery<>();
        query.addWhereEqualTo("ownerId", userId);
        query.include("parentCategory");
        query.order("order");
        query.findObjects(listener);
    }

    // 查询云端是否有系统预设分类
    public boolean hasSystemPresetsOnCloud(String userId) {
        try {
            BmobQuery<CloudCategory> query = new BmobQuery<>();
            query.addWhereEqualTo("ownerId", userId);
            query.addWhereEqualTo("systemPreset", true);
            List<CloudCategory> result = query.findObjectsSync(CloudCategory.class);
            return result != null && !result.isEmpty();
        } catch (Exception e) {
            Log.e(TAG, "查询云端系统分类失败：" + e.getMessage());
            return false;
        }
    }
    // 同步拉取当前用户的所有分类（含系统预设 + 用户自定义）
    public List<CloudCategory> getAllCategoriesSync(String userId) throws Exception {
        if (userId == null) return null;

        BmobQuery<CloudCategory> query = new BmobQuery<>();
        query.addWhereEqualTo("ownerId", userId);
        query.order("order");

        // Bmob SDK 提供同步方法 findObjectsSync(Class<T> cls)
        return query.findObjectsSync(CloudCategory.class);
    }

    /**
     * 同步获取指定父分类的所有子分类
     */
    public List<CloudSubCategory> getSubCategoriesByParentSync(String parentCloudId) throws Exception {
        if (parentCloudId == null || parentCloudId.isEmpty()) {
            Log.w(TAG, "⚠️ parentCloudId 为空,无法查询子分类");
            return null;
        }

        Log.d(TAG, "🔍 开始查询父分类的子分类,parentCloudId: " + parentCloudId);

        try {
            String userId = getCurrentUserId();
            if (userId == null) {
                Log.e(TAG, "❌ 用户未登录,无法查询");
                return null;
            }

            // 🎯 核心修复: 使用 parentCloudId 字符串字段查询
            BmobQuery<CloudSubCategory> query = new BmobQuery<>();
            query.addWhereEqualTo("ownerId", userId);
            query.addWhereEqualTo("parentCloudId", parentCloudId); // ✅ 使用字符串字段
            query.include("parentCategory"); // 可选:展开父分类信息
            query.order("order");
            query.setLimit(500);

            List<CloudSubCategory> subList = query.findObjectsSync(CloudSubCategory.class);

            if (subList == null || subList.isEmpty()) {
                Log.w(TAG, "⚠️ 未找到匹配的子分类");
                return null;
            }

            Log.d(TAG, "✅ 找到 " + subList.size() + " 个匹配的子分类");

            // 🔍 验证日志
            for (CloudSubCategory sub : subList) {
//                Log.d(TAG, "  ├─ " + sub.getName() +
//                        " (cloudId: " + sub.getObjectId() +
//                        ", parentCloudId: " + sub.getParentCloudId() + ")");
            }

            return subList;

        } catch (Exception e) {
            Log.e(TAG, "❌ 查询子分类失败: " + e.getMessage(), e);
            throw e;
        }
    }



    // ------------------- 核心：同步上传系统预设分类 -------------------
    public boolean uploadCategorySync(Category localCat) {
        if (db == null) {
            Log.e(TAG, "uploadCategorySync: local db is null");
            return false;
        }

        try {
            CloudCategory cloud = CloudCategory.fromLocalCategory(localCat);
            cloud.setSystemPreset(localCat.isSystemPreset());
            cloud.setOwnerId(localCat.getOwnerId() != null ? localCat.getOwnerId() : getCurrentUserId());

            String parentCloudId = localCat.getCloudId();

            // ----------------- 上传 / 更新父分类 -----------------
            if (parentCloudId == null || parentCloudId.isEmpty()) {
                parentCloudId = cloud.saveSync();
                if (parentCloudId != null) {
                    localCat.setCloudId(parentCloudId);
                    localCat.setSyncState(SyncState.SYNCED.getValue());
                    db.categoryDao().update(localCat);
                    Log.d(TAG, "🆕 上传父分类成功: " + localCat.getName() + " (cloudId: " + parentCloudId + ")");
                }
            } else {
                cloud.update(parentCloudId, new UpdateListener() {
                    @Override
                    public void done(BmobException e) {
                        if (e == null) {
                            localCat.setSyncState(SyncState.SYNCED.getValue());
                            db.categoryDao().update(localCat);
                            Log.d(TAG, "🔄 更新父分类成功: " + localCat.getName());
                        } else {
                            Log.e(TAG, "❌ 更新父分类失败: " + e.getMessage());
                        }
                    }
                });
            }

            // ----------------- 上传 / 更新子分类 -----------------
            if (localCat.getSubCategories() != null && parentCloudId != null) {
                for (SubCategory sub : localCat.getSubCategories()) {
                    sub.setParentCloudId(parentCloudId);

                    CloudSubCategory cloudSub = new CloudSubCategory();
                    cloudSub.setName(sub.getName());
                    cloudSub.setIconUri(sub.getIconUri());
                    cloudSub.setOwnerId(BmobUser.getCurrentUser());
//                    cloudSub.setLocalId(sub.getId());
                    cloudSub.setParentCloudId(parentCloudId);
//                    cloudSub.setParentCategoryId(sub.getParentCategoryId());

                    // 设置 Pointer
                    CloudCategory parent = new CloudCategory();
                    parent.setObjectId(parentCloudId);
                    cloudSub.setParentCategory(parent);

                    // 设置其他字段
                    cloudSub.setColor(sub.getColor());
                    cloudSub.setOrder(sub.getOrder());

                    // 🔧 核心修复: 确保 cloudId 正确保存到本地
                    if (sub.getCloudId() == null || sub.getCloudId().isEmpty()) {
                        try {
                            String subCloudId = cloudSub.saveSync();
                            if (subCloudId != null && !subCloudId.isEmpty()) {
                                // ✅ 关键: 立即更新本地数据库
                                sub.setCloudId(subCloudId);
                                sub.setParentCloudId(parentCloudId); // 设置父分类云端id
                                sub.setSyncState(SyncState.SYNCED.getValue());

                                // 调用 update 而不是依赖外部更新
                                int updated = db.subCategoryDao().update(sub);

                                if (updated > 0) {
                                    Log.d(TAG, "🆕 上传子分类成功并已写入本地: " + sub.getName() +
                                            " (cloudId: " + subCloudId +
                                            ", parentCloudId: " + parentCloudId +
                                            ", 本地ID: " + sub.getId() + ")");
                                } else {
                                    Log.e(TAG, "⚠️ 上传成功但写入本地失败: " + sub.getName());
                                }

                                // 🔍 验证: 立即查询确认写入成功
                                SubCategory verify = db.subCategoryDao().getById(sub.getId());
                                if (verify != null && verify.getCloudId() != null) {
                                    Log.d(TAG, "  ✓ 验证成功: cloudId 已正确写入 (" + verify.getCloudId() + ")");
                                } else {
                                    Log.e(TAG, "  ✗ 验证失败: cloudId 未写入或为空!");
                                }
                            } else {
                                Log.e(TAG, "❌ 上传子分类返回 cloudId 为空: " + sub.getName());
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "❌ 上传子分类异常: " + sub.getName() + " - " + e.getMessage(), e);
                        }
                    } else {
                        // 已有 cloudId, 执行更新
                        cloudSub.update(sub.getCloudId(), new UpdateListener() {
                            @Override
                            public void done(BmobException e) {
                                if (e == null) {
                                    sub.setSyncState(SyncState.SYNCED.getValue());
                                    db.subCategoryDao().update(sub);
                                    Log.d(TAG, "🔄 更新子分类成功: " + sub.getName());
                                } else {
                                    Log.e(TAG, "❌ 更新子分类失败: " + sub.getName() + " - " + e.getMessage());
                                }
                            }
                        });
                    }
                }
            }

            return true;

        } catch (Exception e) {
            Log.e(TAG, "❌ 系统分类上传失败: " + e.getMessage(), e);
            return false;
        }
    }
}
