package com.example.my_project1.data.model.tag;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;

import com.example.my_project1.data.model.SyncState;

import java.io.Serializable;
import java.util.Date;

/**
 * 账单与标签多对多关联实体类（对应 bill_tag_cross_ref 交叉表）
 */
@Entity(
        tableName = "bill_tag_cross_ref",
        primaryKeys = {"bill_id", "tag_id"},
        indices = {
                @Index(value = {"bill_id"}),
                @Index(value = {"tag_id"})
        }
)
public class BillTagCrossRef implements Serializable {

    /** 关联账单的本地数据库主键ID（联合主键之一） */
    @ColumnInfo(name = "bill_id")
    private long billId;

    /** 关联标签的本地数据库主键ID（联合主键之二） */
    @ColumnInfo(name = "tag_id")
    private long tagId;

    /** 所属用户的唯一标识ID */
    @ColumnInfo(name = "user_id")
    private String userId;

    /** 快照或冗余存储的标签名称（方便直接读取而无需频繁连表） */
    @ColumnInfo(name = "tag_name")
    private String tagName;

    /** 标签背景颜色的十六进制字符串 */
    @ColumnInfo(name = "tag_bg_color")
    private String tagBgColor;

    /** 标签文字颜色的十六进制字符串 */
    @ColumnInfo(name = "tag_text_color")
    private String tagTextColor;

    /** 逻辑删除标记，true表示该关联关系已被移除 */
    @ColumnInfo(name = "is_deleted")
    private boolean isDeleted;

    /** 记录更新时间 */
    @ColumnInfo(name = "updated_at")
    private Date updatedAt;

    /** 本地与云端同步状态标记（已同步/待创建/待删除） */
    @ColumnInfo(name = "sync_state")
    private SyncState syncState = SyncState.SYNCED;

    /** 无参构造函数，Room 反序列化映射使用 */
    public BillTagCrossRef() {}

    /**
     * 快捷构造函数，用于创建新的账单标签关联
     *
     * @param billId       账单ID
     * @param tagId        标签ID
     * @param userId       用户ID
     * @param tagName      标签名称
     * @param tagBgColor   标签背景颜色
     * @param tagTextColor 标签文字颜色
     */
    @Ignore
    public BillTagCrossRef(long billId, long tagId, String userId, String tagName, String tagBgColor, String tagTextColor) {
        this.billId = billId;
        this.tagId = tagId;
        this.userId = userId;
        this.tagName = tagName;
        this.tagBgColor = tagBgColor;
        this.tagTextColor = tagTextColor;
        this.isDeleted = false;
        this.updatedAt = new Date();
        this.syncState = SyncState.TO_CREATE;
    }

    public long getBillId() {
        return billId;
    }

    public void setBillId(long billId) {
        this.billId = billId;
    }

    public long getTagId() {
        return tagId;
    }

    public void setTagId(long tagId) {
        this.tagId = tagId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    public String getTagBgColor() {
        return tagBgColor;
    }

    public void setTagBgColor(String tagBgColor) {
        this.tagBgColor = tagBgColor;
    }

    public String getTagTextColor() {
        return tagTextColor;
    }

    public void setTagTextColor(String tagTextColor) {
        this.tagTextColor = tagTextColor;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(boolean deleted) {
        isDeleted = deleted;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    public SyncState getSyncState() {
        return syncState;
    }

    public void setSyncState(SyncState syncState) {
        this.syncState = syncState;
    }
}
