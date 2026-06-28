package com.example.my_project1.data.remote.model.clouduser;

import cn.bmob.v3.BmobUser;

/**
 * 云端用户模型（对应 Bmob 的 _User 表）
 * 可扩展头像、昵称等字段。
 */
public class CloudUser extends BmobUser {

    private String avatarUrl;
    private String nickname;

    /** ✅ 无参构造函数（Bmob SDK 反射时需要） */
    public CloudUser() {}

    /** ✅ 便捷构造函数，用于创建仅含 objectId 的用户对象（常用于 Pointer） */
    public CloudUser(String objectId) {
        setObjectId(objectId);
    }

    // Getter & Setter
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
}
