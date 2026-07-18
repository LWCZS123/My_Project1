package com.example.my_project1.data.model.bill;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.example.my_project1.data.model.SyncState;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Entity(tableName = "bills")
public class Bill implements Serializable {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "object_id")
    private String objectId;      // Bmob唯一ID

    @ColumnInfo(name = "user_id")
    private String userId;        // 所属用户

    @ColumnInfo(name = "book_id")
    private String bookId;        // 归属账本

    @ColumnInfo(name = "account_id")
    private String accountId;     // 使用账户

    @ColumnInfo(name = "local_account_id", defaultValue = "-1")
    private long localAccountId = -1; // 使用账户本地ID

    @ColumnInfo(name = "category_id")
    private String categoryId;    // 分类ID（可一级/二级）

    @ColumnInfo(name = "category_name")
    private String categoryName;  // 分类名称冗余字段

    @ColumnInfo(name = "category_icon")
    private String categoryIconUrl; // 分类图标（UI展示无需查表）

    private double amount;        // 金额
    private int type;             // 0支出 1收入

    private boolean excludeBudget;   // 是否不计入预算


    private String remark;        // 备注
    private Date billTime;        // 账单时间

    @ColumnInfo(name = "image_urls")
    private List<String> imageUrls;      // 用户上传图片

    @ColumnInfo(name = "location")
    private String location;      // 📍地点（新增字段）

    // Bill.java 新增字段（+对应 Room 列）
    @ColumnInfo(name = "source_wish_id", defaultValue = "-1")
    private long sourceWishId = -1;


    @ColumnInfo(name = "sync_state")
    private SyncState syncState = SyncState.SYNCED;

    private Date createdAt;
    private Date updatedAt;



    //================ 构造 ================//

    public Bill() {}

    @Ignore
    public Bill(String objectId, String userId, String bookId, String accountId,
                String categoryId, String categoryName, String categoryIconUrl,
                double amount, int type,Boolean excludeBudget, String remark, Date billTime,
                List<String> imageUrls, String location) {

        this.objectId = objectId;
        this.userId = userId;
        this.bookId = bookId;
        this.accountId = accountId;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.categoryIconUrl = categoryIconUrl;
        this.amount = amount;
        this.type = type;
        this.excludeBudget = excludeBudget;
        this.remark = remark;
        this.billTime = billTime;
        this.imageUrls = imageUrls;
        this.location = location;
    }


    //================ Getter / Setter ================//

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getObjectId() { return objectId; }
    public void setObjectId(String objectId) { this.objectId = objectId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getBookId() { return bookId; }
    public void setBookId(String bookId) { this.bookId = bookId; }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public long getLocalAccountId() { return localAccountId; }
    public void setLocalAccountId(long localAccountId) { this.localAccountId = localAccountId; }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getCategoryIconUrl() { return categoryIconUrl; }
    public void setCategoryIconUrl(String categoryIconUrl) { this.categoryIconUrl = categoryIconUrl; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public int getType() { return type; }
    public void setType(int type) { this.type = type; }

    public boolean isExcludeBudget() {
        return excludeBudget;
    }

    public void setExcludeBudget(boolean excludeBudget) {
        this.excludeBudget = excludeBudget;
    }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Date getBillTime() { return billTime; }
    public void setBillTime(Date billTime) { this.billTime = billTime; }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public SyncState getSyncState() { return syncState; }
    public void setSyncState(SyncState syncState) { this.syncState = syncState; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }


    public long getSourceWishId() { return sourceWishId; }
    public void setSourceWishId(long sourceWishId) { this.sourceWishId = sourceWishId; }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Bill bill = (Bill) o;

        if (id != bill.id) return false;
        if (Double.compare(bill.amount, amount) != 0) return false;
        if (type != bill.type) return false;
        if (excludeBudget != bill.excludeBudget) return false;
        if (sourceWishId != bill.sourceWishId) return false;
        if (objectId != null ? !objectId.equals(bill.objectId) : bill.objectId != null)
            return false;
        if (userId != null ? !userId.equals(bill.userId) : bill.userId != null) return false;
        if (bookId != null ? !bookId.equals(bill.bookId) : bill.bookId != null) return false;
        if (accountId != null ? !accountId.equals(bill.accountId) : bill.accountId != null)
            return false;
        if (categoryId != null ? !categoryId.equals(bill.categoryId) : bill.categoryId != null)
            return false;
        if (categoryName != null ? !categoryName.equals(bill.categoryName) : bill.categoryName != null)
            return false;
        if (categoryIconUrl != null ? !categoryIconUrl.equals(bill.categoryIconUrl) : bill.categoryIconUrl != null)
            return false;
        if (remark != null ? !remark.equals(bill.remark) : bill.remark != null) return false;
        if (billTime != null ? !billTime.equals(bill.billTime) : bill.billTime != null) return false;
        if (imageUrls != null ? !imageUrls.equals(bill.imageUrls) : bill.imageUrls != null)
            return false;
        if (location != null ? !location.equals(bill.location) : bill.location != null) return false;
        if (syncState != bill.syncState) return false;
        if (createdAt != null ? !createdAt.equals(bill.createdAt) : bill.createdAt != null)
            return false;
        return updatedAt != null ? updatedAt.equals(bill.updatedAt) : bill.updatedAt == null;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = (int) (id ^ (id >>> 32));
        result = 31 * result + (objectId != null ? objectId.hashCode() : 0);
        result = 31 * result + (userId != null ? userId.hashCode() : 0);
        result = 31 * result + (bookId != null ? bookId.hashCode() : 0);
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        result = 31 * result + (categoryId != null ? categoryId.hashCode() : 0);
        result = 31 * result + (categoryName != null ? categoryName.hashCode() : 0);
        result = 31 * result + (categoryIconUrl != null ? categoryIconUrl.hashCode() : 0);
        temp = Double.doubleToLongBits(amount);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + type;
        result = 31 * result + (excludeBudget ? 1 : 0);
        result = 31 * result + (remark != null ? remark.hashCode() : 0);
        result = 31 * result + (billTime != null ? billTime.hashCode() : 0);
        result = 31 * result + (imageUrls != null ? imageUrls.hashCode() : 0);
        result = 31 * result + (location != null ? location.hashCode() : 0);
        result = 31 * result + (int) (sourceWishId ^ (sourceWishId >>> 32));
        result = 31 * result + (syncState != null ? syncState.hashCode() : 0);
        result = 31 * result + (createdAt != null ? createdAt.hashCode() : 0);
        result = 31 * result + (updatedAt != null ? updatedAt.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Bill{" +
                "id=" + id +
                ", objectId='" + objectId + '\'' +
                ", userId='" + userId + '\'' +
                ", bookId='" + bookId + '\'' +
                ", accountId='" + accountId + '\'' +
                ", categoryId='" + categoryId + '\'' +
                ", categoryName='" + categoryName + '\'' +
                ", categoryIconUrl='" + categoryIconUrl + '\'' +
                ", amount=" + amount +
                ", type=" + type +
                ", excludeBudget=" + excludeBudget +
                ", remark='" + remark + '\'' +
                ", billTime=" + billTime +
                ", imageUrl='" + imageUrls + '\'' +
                ", location='" + location + '\'' +
                ", syncState=" + syncState +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
