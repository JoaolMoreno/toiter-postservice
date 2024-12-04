package com.toiter.postservice.model;

import com.toiter.postservice.entity.Post;

public interface PostEvent {
    Post getPost();

    String toString();
}