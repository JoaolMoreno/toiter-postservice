package com.toiter.postservice.repository;

import com.toiter.postservice.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    Page<Post> findByUserId(Long userId, Pageable pageable);

    Page<Post> findByParentPostId(Long parentPostId, Pageable pageable);

    @Query("SELECT p.id FROM Post p WHERE p.parentPostId = :parentPostId ORDER BY p.createdAt DESC limit 3")
    List<Long> findChildIdsByParentPostId(Long parentPostId, Pageable pageable);
}
