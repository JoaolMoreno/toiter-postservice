package com.toiter.postservice.model;


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

    private LocalDateTime createdAt;

    public PostData() {
    }

    public PostData(Long id, Long parentPostId, Long userId, Long repostParentId, String content, String mediaUrl) {
        this.id = id;
        this.parentPostId = parentPostId;
        this.repostParentId = repostParentId;
        this.isRepost = repostParentId != null;
        this.isReply = parentPostId != null;
        this.userId = userId;
        this.content = content;
        this.mediaUrl = mediaUrl;
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
    }

    public PostData(PostCreatedEvent event) {
        this.id = event.getPostId();
        this.parentPostId = event.getParentPostId();
        this.repostParentId = event.getRepostParentId();
        this.isRepost = event.getRepostParentId() != null;
        this.isReply = event.getParentPostId() != null;
        this.userId = event.getUserId();
        this.content = event.getContent();
    }

    public PostData(Long postId, Long userId, String content, Long parentPostId) {
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
}
