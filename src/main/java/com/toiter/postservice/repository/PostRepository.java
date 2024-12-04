package com.toiter.postservice.repository;

import com.toiter.postservice.entity.Post;
import com.toiter.postservice.model.PostData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    Page<Post> findByUserId(Long userId, Pageable pageable);

    Page<Post> findByParentPostId(Long parentPostId, Pageable pageable);

    @Query("SELECT p.id FROM Post p WHERE p.parentPostId = :parentPostId ORDER BY p.createdAt DESC limit 3")
    List<Long> findChildIdsByParentPostId(Long parentPostId, Pageable pageable);

    @Query("""
        SELECT new com.toiter.postservice.model.PostData(
            p.id,
            p.parentPostId,
            p.repostParentId,
            p.userId,
            p.content,
            p.mediaUrl,
            COUNT(DISTINCT l.id) as likesCount,
            COUNT(DISTINCT r.id) as repliesCount,
            COUNT(DISTINCT rp.id) as repostsCount,
            COUNT(DISTINCT v.id) as viewCount,
            p.createdAt
        )
        FROM Post p
        LEFT JOIN Like l ON l.post.id = p.id
        LEFT JOIN Post r ON r.parentPostId = p.id
        LEFT JOIN Post rp ON rp.repostParentId = p.id
        LEFT JOIN View v ON v.post.id = p.id
        WHERE p.id = :postId
        GROUP BY p.id
    """)
    Optional<PostData> fetchPostData(Long postId);

    @Query("""
        SELECT new com.toiter.postservice.model.PostData(
            p.id,
            p.parentPostId,
            p.repostParentId,
            p.userId,
            p.content,
            p.mediaUrl,
            COUNT(DISTINCT l.id) as likesCount,
            COUNT(DISTINCT r.id) as repliesCount,
            COUNT(DISTINCT rp.id) as repostsCount,
            COUNT(DISTINCT v.id) as viewCount,
            p.createdAt
        )
        FROM Post p
        LEFT JOIN Like l ON l.post.id = p.id
        LEFT JOIN Post r ON r.parentPostId = p.id
        LEFT JOIN Post rp ON rp.repostParentId = p.id
        LEFT JOIN View v ON v.post.id = p.id
        WHERE p.parentPostId = :parentPostId
        GROUP BY p.id
    """)
    Page<PostData> fetchChildPostsData(Long parentPostId, Pageable pageable);

}
