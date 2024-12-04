package com.toiter.postservice.repository;

import com.toiter.postservice.entity.Like;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LikeRepository extends JpaRepository<Like, Long> {
    void deleteByPostIdAndUserId(Long postId, Long userId);
}
