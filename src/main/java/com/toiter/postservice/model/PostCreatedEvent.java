package com.toiter.postservice.model;

import com.toiter.postservice.entity.Post;

public class PostCreatedEvent implements PostEvent {

    private Post post;

    public PostCreatedEvent(Post post) {
        this.post = post;
    }

    public PostCreatedEvent() {
    }

    @Override
    public Post getPost() {
        return post;
    }

    public void setPost(Post post) {
        this.post = post;
    }
}