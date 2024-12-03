package com.toiter.postservice.model;

public class PostCreatedEvent {

    private Long postId;
    private Long userId;
    private String content;
    private Long parentPostId;
    private Long repostParentId;

    public PostCreatedEvent(Long postId, Long userId, String content, Long parentPostId, Long repostParentId) {
        this.postId = postId;
        this.userId = userId;
        this.content = content;
        this.parentPostId = parentPostId;
        this.repostParentId = repostParentId;
    }

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
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
}
