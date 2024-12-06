package com.toiter.postservice.entity;

import jakarta.persistence.PrePersist;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "posts", schema = "pst")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_post_id")
    private Long parentPostId;

    @Column(name = "repost_parent_post_id")
    private Long repostParentId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false, referencedColumnName = "id")
    private User user;

    private String content;

    @Column(name = "media_url")
    private String mediaUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted")
    private boolean deleted;


    public Post() {
    }

    public Post(Long parentPostId, Long repostParentId, Long userId, String content, String mediaUrl) {
        this.parentPostId = parentPostId;
        this.repostParentId = repostParentId;
        this.userId = userId;
        this.content = content;
        this.mediaUrl = mediaUrl;
    }

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRepostParentId() {
        return repostParentId;
    }

    public void setRepostParentId(Long repostParentId) {
        this.repostParentId = repostParentId;
    }

    public Long getParentPostId() {
        return parentPostId;
    }

    public void setParentPostId(Long parentPostId) {
        this.parentPostId = parentPostId;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    @Override
    public String toString() {
        return "Post{" +
                "id=" + id +
                ", parentPostId=" + parentPostId +
                ", repostParentId=" + repostParentId +
                ", userId=" + userId +
                ", content='" + content + '\'' +
                ", mediaUrl='" + mediaUrl + '\'' +
                ", createdAt=" + createdAt +
                ", deletedAt=" + deletedAt +
                ", deleted=" + deleted +
                '}';
    }
}
