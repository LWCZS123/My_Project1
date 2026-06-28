package com.example.my_project1.data.model.wish;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.example.my_project1.data.model.SyncState;

import java.util.Date;

/**
 * WishRecord - 愿望存钱记录实体（优化版）
 * -------------------------------------------------------
 * 新增字段：
 *  - linkedBillId：关联账单的本地 ID（-1 表示无关联）
 *    用于存钱记录删除时联动删除对应账单
 */
@Entity(
        tableName = "wish_records",
        foreignKeys = @ForeignKey(
                entity = Wish.class,
                parentColumns = "id",
                childColumns = "wish_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = @Index("wish_id")
)
public class WishRecord {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "object_id")
    private String objectId;           // Bmob 云端 ID

    @ColumnInfo(name = "wish_id")
    private long wishId;               // 关联本地愿望 ID

    @ColumnInfo(name = "wish_object_id")
    private String wishObjectId;       // 关联云端愿望 ID

    @ColumnInfo(name = "user_id")
    private String userId;

    private double amount;             // 本次存入金额

    private String note;               // 备注

    @ColumnInfo(name = "record_date")
    private Date recordDate;           // 存入日期

    @ColumnInfo(name = "sync_state")
    private SyncState syncState = SyncState.SYNCED;

    @ColumnInfo(name = "created_at")
    private Date createdAt;

    @ColumnInfo(name = "updated_at")
    private Date updatedAt;

    /**
     * 关联账单本地 ID
     * -1 表示无关联账单（老数据或手动无账户记录）
     * > 0 表示有关联账单，删除此记录时联动软删除该账单
     */
    @ColumnInfo(name = "linked_bill_id", defaultValue = "-1")
    private long linkedBillId = -1;

    // ================== 构造 ==================

    public WishRecord() {}

    @Ignore
    public WishRecord(long wishId, String wishObjectId, String userId,
                      double amount, String note, Date recordDate) {
        this.wishId       = wishId;
        this.wishObjectId = wishObjectId;
        this.userId       = userId;
        this.amount       = amount;
        this.note         = note;
        this.recordDate   = recordDate;
    }

    // ================== Getter / Setter ==================

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getObjectId() { return objectId; }
    public void setObjectId(String objectId) { this.objectId = objectId; }

    public long getWishId() { return wishId; }
    public void setWishId(long wishId) { this.wishId = wishId; }

    public String getWishObjectId() { return wishObjectId; }
    public void setWishObjectId(String wishObjectId) { this.wishObjectId = wishObjectId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Date getRecordDate() { return recordDate; }
    public void setRecordDate(Date recordDate) { this.recordDate = recordDate; }

    public SyncState getSyncState() { return syncState; }
    public void setSyncState(SyncState syncState) { this.syncState = syncState; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }

    public long getLinkedBillId() { return linkedBillId; }
    public void setLinkedBillId(long linkedBillId) { this.linkedBillId = linkedBillId; }
}