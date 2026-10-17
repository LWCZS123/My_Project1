package com.example.my_project1.data.model.tag;

import androidx.room.Embedded;
import androidx.room.Relation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class TagGroupWithTags implements Serializable {

    @Embedded
    private TagGroupEntity tagGroup;

    @Relation(
            parentColumn = "id",
            entityColumn = "group_id"
    )
    private List<TagEntity> tags = new ArrayList<>();

    public TagGroupWithTags() {}

    public TagGroupEntity getTagGroup() {
        return tagGroup;
    }

    public void setTagGroup(TagGroupEntity tagGroup) {
        this.tagGroup = tagGroup;
    }

    public List<TagEntity> getTags() {
        return tags;
    }

    public void setTags(List<TagEntity> tags) {
        this.tags = tags;
    }
}
