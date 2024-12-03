package com.toiter.postservice.model;

import com.toiter.postservice.entity.Post;

public class PostDeletedEvent implements PostEvent {

    private Post post;

    public PostDeletedEvent(Post post) {
        this.post = post;
    }

    public PostDeletedEvent() {
    }

    @Override
    public Post getPost() {
        return post;
    }

    public void setPost(Post post) {
        this.post = post;
    }
}