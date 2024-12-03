package com.toiter.postservice.model;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.toiter.postservice.entity.Post;

import java.time.LocalDateTime;

public class PostData {

    private Long id;

    private Long parentPostId;

    private Long repostParentId;

    private Boolean isRepost;

    private Boolean isReply;

    private Long userId;

    private String content;

    private String mediaUrl;

    private Integer likesCount = 0;

    private Integer repostsCount = 0;

    private Integer viewCount = 0;

    @JsonIgnore
    private boolean deleted;

    private LocalDateTime createdAt;

    public PostData() {
    }

    public PostData(Post post){
        this.id = post.getId();
        this.parentPostId = post.getParentPostId();
        this.repostParentId = post.getRepostParentId();
        this.isRepost = post.getRepostParentId() != null;
        this.isReply = post.getParentPostId() != null;
        this.userId = post.getUserId();
        this.content = post.getContent();
        this.mediaUrl = post.getMediaUrl();
        this.createdAt = post.getCreatedAt();
        this.deleted = post.isDeleted();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getParentPostId() {
        return parentPostId;
    }

    public void setParentPostId(Long parentPostId) {
        this.parentPostId = parentPostId;
    }

    public Long getRepostParentId() {
        return repostParentId;
    }

    public void setRepostParentId(Long repostParentId) {
        this.repostParentId = repostParentId;
    }

    public Boolean getRepost() {
        return isRepost;
    }

    public void setRepost(Boolean repost) {
        isRepost = repost;
    }

    public Boolean getReply() {
        return isReply;
    }

    public void setReply(Boolean reply) {
        isReply = reply;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }

    public Integer getLikesCount() {
        return likesCount;
    }

    public void setLikesCount(Integer likesCount) {
        this.likesCount = likesCount;
    }

    public Integer getRepostsCount() {
        return repostsCount;
    }

    public void setRepostsCount(Integer repostsCount) {
        this.repostsCount = repostsCount;
    }

    public Integer getViewCount() {
        return viewCount;
    }

    public void setViewCount(Integer viewCount) {
        this.viewCount = viewCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}
