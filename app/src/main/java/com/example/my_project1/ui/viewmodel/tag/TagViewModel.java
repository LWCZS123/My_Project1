package com.example.my_project1.ui.viewmodel.tag;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.my_project1.data.model.common.ApiResponse;
import com.example.my_project1.data.model.tag.TagEntity;
import com.example.my_project1.data.model.tag.TagGroupEntity;
import com.example.my_project1.data.model.tag.TagGroupWithTags;
import com.example.my_project1.data.repository.tag.TagRepository;

import java.util.List;

import cn.bmob.v3.BmobUser;

/**
 * TagViewModel
 * -------------------------------------------------------
 * 标签及标签组 ViewModel，负责 UI 与 Repository 之间交互
 */
public class TagViewModel extends AndroidViewModel {

    private final TagRepository tagRepository;
    private String currentUserId;

    private final MutableLiveData<String> toastMessage = new MutableLiveData<>();
    private final MutableLiveData<ApiResponse<Void>> operationState = new MutableLiveData<>();

    public TagViewModel(@NonNull Application application) {
        super(application);
        this.tagRepository = new TagRepository(application);
        BmobUser user = BmobUser.getCurrentUser();
        this.currentUserId = user != null ? user.getObjectId() : null;
    }

    public String getCurrentUserId() {
        if (currentUserId != null && !currentUserId.isEmpty()) {
            return currentUserId;
        }
        BmobUser user = BmobUser.getCurrentUser();
        if (user != null && user.getObjectId() != null && !user.getObjectId().isEmpty()) {
            this.currentUserId = user.getObjectId();
            return this.currentUserId;
        }
        return "default_user";
    }

    public LiveData<String> getToastMessage() {
        return toastMessage;
    }

    public LiveData<ApiResponse<Void>> getOperationState() {
        return operationState;
    }

    public LiveData<List<TagGroupWithTags>> getGroupsWithTags() {
        String userId = getCurrentUserId();
        return tagRepository.getGroupsWithTagsLiveData(userId);
    }

    public LiveData<List<TagGroupEntity>> getActiveGroups() {
        String userId = getCurrentUserId();
        return tagRepository.getActiveGroupsLiveData(userId);
    }

    public LiveData<List<TagEntity>> getAllActiveTags() {
        String userId = getCurrentUserId();
        return tagRepository.getAllActiveTagsLiveData(userId);
    }

    public LiveData<List<TagEntity>> getArchivedTags() {
        String userId = getCurrentUserId();
        return tagRepository.getArchivedTagsLiveData(userId);
    }

    public void createGroup(String groupName) {
        createGroup(groupName, null);
    }

    public void createGroup(String groupName, @Nullable ApiResponse.Callback<Long> callback) {
        String userId = getCurrentUserId();
        tagRepository.createGroup(userId, groupName, response -> {
            if (response.getMessage() != null) {
                toastMessage.setValue(response.getMessage());
            }
            operationState.setValue(response.mapMessage());
            if (callback != null) {
                callback.onComplete(response);
            }
        });
    }

    public void updateGroupName(TagGroupEntity group, String newName) {
        updateGroupName(group, newName, null);
    }

    public void updateGroupName(TagGroupEntity group, String newName, @Nullable ApiResponse.Callback<Void> callback) {
        tagRepository.updateGroupName(group, newName, response -> {
            if (response.getMessage() != null) {
                toastMessage.setValue(response.getMessage());
            }
            operationState.setValue(response);
            if (callback != null) {
                callback.onComplete(response);
            }
        });
    }

    public void updateGroupFoldState(long groupId, boolean isFolded) {
        tagRepository.updateGroupFoldState(groupId, isFolded);
    }

    public void deleteGroup(TagGroupEntity group) {
        deleteGroup(group, null);
    }

    public void deleteGroup(TagGroupEntity group, @Nullable ApiResponse.Callback<Void> callback) {
        tagRepository.deleteGroup(group, response -> {
            if (response.getMessage() != null) {
                toastMessage.setValue(response.getMessage());
            }
            operationState.setValue(response);
            if (callback != null) {
                callback.onComplete(response);
            }
        });
    }

    public void saveTag(TagEntity tag) {
        saveTag(tag, null);
    }

    public void saveTag(TagEntity tag, @Nullable ApiResponse.Callback<Long> callback) {
        String userId = getCurrentUserId();
        if (tag.getUserId() == null || tag.getUserId().isEmpty()) {
            tag.setUserId(userId);
        }
        tagRepository.saveTag(tag, response -> {
            if (response.getMessage() != null) {
                toastMessage.setValue(response.getMessage());
            }
            operationState.setValue(response.mapMessage());
            if (callback != null) {
                callback.onComplete(response);
            }
        });
    }

    public void moveTagGroup(long tagId, long newGroupId) {
        moveTagGroup(tagId, newGroupId, null);
    }

    public void moveTagGroup(long tagId, long newGroupId, @Nullable ApiResponse.Callback<Void> callback) {
        String userId = getCurrentUserId();
        tagRepository.moveTagGroup(tagId, newGroupId, userId, response -> {
            if (response.getMessage() != null) {
                toastMessage.setValue(response.getMessage());
            }
            operationState.setValue(response);
            if (callback != null) {
                callback.onComplete(response);
            }
        });
    }

    public void sortTagsInGroup(long groupId) {
        sortTagsInGroup(groupId, null);
    }

    public void sortTagsInGroup(long groupId, @Nullable ApiResponse.Callback<Void> callback) {
        String userId = getCurrentUserId();
        tagRepository.sortTagsInGroup(groupId, userId, response -> {
            if (response.getMessage() != null) {
                toastMessage.setValue(response.getMessage());
            }
            operationState.setValue(response);
            if (callback != null) {
                callback.onComplete(response);
            }
        });
    }

    public void toggleTagArchive(long tagId, boolean archive) {
        toggleTagArchive(tagId, archive, null);
    }

    public void toggleTagArchive(long tagId, boolean archive, @Nullable ApiResponse.Callback<Void> callback) {
        String userId = getCurrentUserId();
        tagRepository.toggleTagArchive(tagId, archive, userId, response -> {
            if (response.getMessage() != null) {
                toastMessage.setValue(response.getMessage());
            }
            operationState.setValue(response);
            if (callback != null) {
                callback.onComplete(response);
            }
        });
    }

    public void deleteTag(long tagId) {
        deleteTag(tagId, null);
    }

    public void deleteTag(long tagId, @Nullable ApiResponse.Callback<Void> callback) {
        String userId = getCurrentUserId();
        tagRepository.deleteTag(tagId, userId, response -> {
            if (response.getMessage() != null) {
                toastMessage.setValue(response.getMessage());
            }
            operationState.setValue(response);
            if (callback != null) {
                callback.onComplete(response);
            }
        });
    }
}
