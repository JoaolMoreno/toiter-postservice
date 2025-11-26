package com.toiter.postservice.model;

import com.fasterxml.jackson.annotation.JsonView;
import com.toiter.userservice.model.Views;

import java.util.List;

public class PostThread {
    @JsonView(Views.Public.class)
    private PostData parentPost;
    @JsonView(Views.Public.class)
    private List<ChildPost> childPosts;
    @JsonView(Views.Public.class)
    private boolean hasNext;
    @JsonView(Views.Public.class)
    private long totalElements;
    @JsonView(Views.Public.class)
    private int totalPages;
    @JsonView(Views.Public.class)
    private int pageSize;
    @JsonView(Views.Public.class)
    private int currentPage;

    public PostThread() {
    }

    public PostThread(PostData parentPost, List<ChildPost> childPosts, boolean hasNext, long totalElements, int totalPages, int pageSize, int currentPage) {
        this.parentPost = parentPost;
        this.childPosts = childPosts;
        this.hasNext = hasNext;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.pageSize = pageSize;
        this.currentPage = currentPage;
    }

    public boolean isHasNext() {
        return hasNext;
    }

    public void setHasNext(boolean hasNext) {
        this.hasNext = hasNext;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }

    public PostData getParentPost() {
        return parentPost;
    }

    public void setParentPost(PostData parentPost) {
        this.parentPost = parentPost;
    }

    public List<ChildPost> getChildPosts() {
        return childPosts;
    }

    public void setChildPosts(List<ChildPost> childPosts) {
        this.childPosts = childPosts;
    }

    public static class ChildPost {
        @JsonView(Views.Public.class)
        private PostData post;
        @JsonView(Views.Public.class)
        private List<PostData> childPosts;

        public ChildPost() {
        }

        public ChildPost(PostData post, List<PostData> childPosts) {
            this.post = post;
            this.childPosts = childPosts;
        }

        public PostData getPost() {
            return post;
        }

        public void setPost(PostData post) {
            this.post = post;
        }

        public List<PostData> getChildPosts() {
            return childPosts;
        }

        public void setChildPosts(List<PostData> childPosts) {
            this.childPosts = childPosts;
        }
    }
}
