package com.example.my_project1.ui.viewmodel.tag;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.my_project1.data.model.tag.TagEntity;
import com.example.my_project1.data.model.tag.TagGroupEntity;
import com.example.my_project1.data.model.tag.TagGroupWithTags;
import com.example.my_project1.data.repository.tag.TagRepository;

import java.util.List;

import cn.bmob.v3.BmobUser;

public class TagViewModel extends AndroidViewModel {

    private final TagRepository tagRepository;
    private String currentUserId;

    private final MutableLiveData<String> toastMessage = new MutableLiveData<>();

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
        String userId = getCurrentUserId();
        tagRepository.createGroup(userId, groupName, (success, message) -> {
            toastMessage.postValue(message);
        });
    }

    public void updateGroupName(TagGroupEntity group, String newName) {
        tagRepository.updateGroupName(group, newName, (success, message) -> {
            toastMessage.postValue(message);
        });
    }

    public void updateGroupFoldState(long groupId, boolean isFolded) {
        tagRepository.updateGroupFoldState(groupId, isFolded);
    }

    public void deleteGroup(TagGroupEntity group) {
        tagRepository.deleteGroup(group, (success, message) -> {
            toastMessage.postValue(message);
        });
    }

    public void saveTag(TagEntity tag) {
        String userId = getCurrentUserId();
        if (tag.getUserId() == null || tag.getUserId().isEmpty()) {
            tag.setUserId(userId);
        }
        tagRepository.saveTag(tag, (success, message) -> {
            toastMessage.postValue(message);
        });
    }

    public void moveTagGroup(long tagId, long newGroupId) {
        String userId = getCurrentUserId();
        tagRepository.moveTagGroup(tagId, newGroupId, userId, (success, message) -> {
            toastMessage.postValue(message);
        });
    }

    public void sortTagsInGroup(long groupId) {
        String userId = getCurrentUserId();
        tagRepository.sortTagsInGroup(groupId, userId, (success, message) -> {
            toastMessage.postValue(message);
        });
    }

    public void toggleTagArchive(long tagId, boolean archive) {
        String userId = getCurrentUserId();
        tagRepository.toggleTagArchive(tagId, archive, userId, (success, message) -> {
            toastMessage.postValue(message);
        });
    }

    public void deleteTag(long tagId) {
        String userId = getCurrentUserId();
        tagRepository.deleteTag(tagId, userId, (success, message) -> {
            toastMessage.postValue(message);
        });
    }
}
