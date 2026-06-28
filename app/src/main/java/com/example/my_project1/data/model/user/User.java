package com.example.my_project1.data.model.user;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Date;

import io.reactivex.annotations.NonNull;

@Entity(tableName = "users")
public class User {

    @PrimaryKey
    @NonNull
    private String objectId; // 对应 Bmob 用户ID

    private String email;
    private String username;
    private Date createdAt;
    private Date updatedAt;

    // getter & setter
    @NonNull
    public String getObjectId() { return objectId; }
    public void setObjectId(@NonNull String objectId) { this.objectId = objectId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
