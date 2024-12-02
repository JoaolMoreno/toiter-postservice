package com.toiter.postservice.model;

public record PostRequest(Long parentPostId, Long repostParentId, String content, String mediaUrl) {
}