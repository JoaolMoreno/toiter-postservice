package com.toiter.userservice.model;

import com.fasterxml.jackson.annotation.JsonView;

public class UserPublicData {
    @JsonView(Views.Cache.class)
    private Long userId;

    @JsonView(Views.Public.class)
    private String username;

    @JsonView(Views.Public.class)
    private String displayName;

    @JsonView(Views.Public.class)
    private String bio;

    @JsonView(Views.Public.class)
    private Long profileImageId;

    @JsonView(Views.Public.class)
    private Long headerImageId;

    @JsonView(Views.Public.class)
    private Integer followersCount;

    @JsonView(Views.Public.class)
    private Integer followingCount;

    @JsonView(Views.Public.class)
    private Boolean isFollowing;

    @JsonView(Views.Public.class)
    private Boolean isFollowingMe;

    @JsonView(Views.Public.class)
    private Integer postsCount;

    public UserPublicData() {
    }

    public UserPublicData(Long userId, String username, String displayName, String bio, Long profileImageId, Long headerImageId, Integer followersCount, Integer followingCount, Boolean isFollowing, Boolean isFollowingMe, Integer postsCount) {
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.bio = bio;
        this.profileImageId = profileImageId;
        this.headerImageId = headerImageId;
        this.followersCount = followersCount;
        this.followingCount = followingCount;
        this.isFollowing = isFollowing;
        this.isFollowingMe = isFollowingMe;
        this.postsCount = postsCount;
    }

    public UserPublicData(Long userId, String username, String displayName, String bio, Long profileImageId, Long headerImageId, Integer followersCount, Integer followingCount) {
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.bio = bio;
        this.profileImageId = profileImageId;
        this.headerImageId = headerImageId;
        this.followersCount = followersCount;
        this.followingCount = followingCount;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Boolean getFollowingMe() {
        return isFollowingMe;
    }

    public void setFollowingMe(Boolean followingMe) {
        isFollowingMe = followingMe;
    }

    public Integer getPostsCount() {
        return postsCount;
    }

    public void setPostsCount(Integer postsCount) {
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

    public Boolean getFollowing() {
        return isFollowing;
    }

    public void setFollowing(Boolean following) {
        isFollowing = following;
    }

    public Boolean getIsFollowingMe() {
        return isFollowingMe;
    }

    public void setIsFollowingMe(Boolean isFollowingMe) {
        this.isFollowingMe = isFollowingMe;
    }
}
