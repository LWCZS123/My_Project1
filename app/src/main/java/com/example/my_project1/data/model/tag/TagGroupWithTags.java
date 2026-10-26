package com.example.my_project1.data.model.tag;

import androidx.room.Embedded;
import androidx.room.Relation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 标签分组与包含的标签关系数据包装类（用于 Room 一对多嵌套查询）
 */
public class TagGroupWithTags implements Serializable {

    /** 嵌套包含的标签分组实体对象 */
    @Embedded
    private TagGroupEntity tagGroup;

    /** 该分组下关联包含的所有标签实体列表（一对多关联关系） */
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
