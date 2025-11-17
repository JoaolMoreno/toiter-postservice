package com.toiter.userservice.model;

public class UserPublicData {
    private String username;
    private String displayName;
    private String bio;
    private Long profileImageId;
    private Long headerImageId;
    private Integer followersCount;
    private Integer followingCount;
    private Boolean isFollowing;
    private Boolean isFollowingMe;
    private Boolean followingMe;
    private Boolean following;
    private Integer postsCount;

    public UserPublicData() {}

    public UserPublicData(String username, String displayName, String bio, Long profileImageId, Long headerImageId,
                          Integer followersCount, Integer followingCount, Boolean isFollowing, Boolean isFollowingMe,
                          Boolean followingMe, Boolean following, Integer postsCount) {
        this.username = username;
        this.displayName = displayName;
        this.bio = bio;
        this.profileImageId = profileImageId;
        this.headerImageId = headerImageId;
        this.followersCount = followersCount;
        this.followingCount = followingCount;
        this.isFollowing = isFollowing;
        this.isFollowingMe = isFollowingMe;
        this.followingMe = followingMe;
        this.following = following;
        this.postsCount = postsCount;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public Long getProfileImageId() {
        return profileImageId;
    }

    public void setProfileImageId(Long profileImageId) {
        this.profileImageId = profileImageId;
    }

    public Long getHeaderImageId() {
        return headerImageId;
    }

    public void setHeaderImageId(Long headerImageId) {
        this.headerImageId = headerImageId;
    }

    public Integer getFollowersCount() {
        return followersCount;
    }

    public void setFollowersCount(Integer followersCount) {
        this.followersCount = followersCount;
    }

    public Integer getFollowingCount() {
        return followingCount;
    }

    public void setFollowingCount(Integer followingCount) {
        this.followingCount = followingCount;
    }

    public Boolean getIsFollowing() {
        return isFollowing;
    }

    public void setIsFollowing(Boolean isFollowing) {
        this.isFollowing = isFollowing;
    }

    public Boolean getIsFollowingMe() {
        return isFollowingMe;
    }

    public void setIsFollowingMe(Boolean isFollowingMe) {
        this.isFollowingMe = isFollowingMe;
    }

    public Boolean getFollowingMe() {
        return followingMe;
    }

    public void setFollowingMe(Boolean followingMe) {
        this.followingMe = followingMe;
    }

    public Boolean getFollowing() {
        return following;
    }

    public void setFollowing(Boolean following) {
        this.following = following;
    }

    public Integer getPostsCount() {
        return postsCount;
    }

    public void setPostsCount(Integer postsCount) {
        this.postsCount = postsCount;
    }
}
