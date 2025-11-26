package com.toiter.postservice.repository;

import com.toiter.postservice.entity.Post;
import com.toiter.postservice.model.PostData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    @Query("SELECT p FROM Post p WHERE p.id = :postId and p.deleted = false")
    Page<Post> findByParentPostId(Long parentPostId, Pageable pageable);

    @Query("SELECT p.id FROM Post p WHERE p.parentPostId = :parentPostId ORDER BY p.createdAt DESC")
    Page<Long> findChildIdsByParentPostId(Long parentPostId, Pageable pageable);

    @Query("""
        SELECT new com.toiter.postservice.model.PostData(
            p.id,
            p.parentPostId,
            p.repostParentId,
            p.userId,
            p.content,
            p.mediaUrl,
            p.mediaWidth,
            p.mediaHeight,
            COUNT(DISTINCT l.id) as likesCount,
            COUNT(DISTINCT r.id) as repliesCount,
            COUNT(DISTINCT rp.id) as repostsCount,
            COUNT(DISTINCT v.id) as viewCount,
            p.createdAt
        )
        FROM Post p
        LEFT JOIN Like l ON l.post.id = p.id
        LEFT JOIN Post r ON r.parentPostId = p.id and r.deleted = false
        LEFT JOIN Post rp ON rp.repostParentId = p.id and rp.deleted = false
        LEFT JOIN View v ON v.post.id = p.id
        WHERE p.id = :postId and p.deleted = false
        GROUP BY p.id
    """)
    Optional<PostData> fetchPostData(Long postId);

    @Query("SELECT p.id FROM Post p WHERE p.userId = :userId and p.deleted = false and p.parentPostId is null ORDER BY p.createdAt DESC")
    Page<Long> fetchIdsByUserId(Long userId, Pageable pageable);

    @Query("SELECT p.id FROM Post p WHERE p.deleted = false and p.parentPostId is null ORDER BY p.createdAt DESC")
    Page<Long> fetchAllPostIds(Pageable pageable);

    @Query("SELECT COUNT(p.id) FROM Post p WHERE p.userId = :userId and p.deleted = false and p.parentPostId is null and p.repostParentId is null")
    Integer countByUserId(Long userId);

    @Query("SELECT p FROM Post p WHERE p.repostParentId = :repostParentId and p.deleted = false")
    java.util.List<Post> findRepostsByRepostParentId(Long repostParentId);
}
