package com.toiter.postservice.controller;

import com.toiter.postservice.entity.Post;
import com.toiter.postservice.model.PostData;
import com.toiter.postservice.model.PostRequest;
import com.toiter.postservice.model.PostThread;
import com.toiter.postservice.service.JwtService;
import com.toiter.postservice.service.LikeService;
import com.toiter.postservice.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/posts")
public class PostController {

    private final PostService postService;
    private final LikeService likeService;
    private final JwtService jwtService;
    private static final Logger logger = LoggerFactory.getLogger(PostController.class);

    public PostController(PostService postService, LikeService likeService, JwtService jwtService) {
        this.postService = postService;
        this.likeService = likeService;
        this.jwtService = jwtService;
    }

    @Operation(summary = "Obter posts com paginação",
            security = {@SecurityRequirement(name = "bearerAuth")}
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Posts encontrados",
                    content = { @Content(mediaType = "application/json") }),
            @ApiResponse(responseCode = "403", description = "Acesso negado",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Posts não encontrados",
                    content = @Content)
    })
    @GetMapping
    public Map<String, Object> getPosts(
            @RequestParam @Parameter(description = "Número da página") int page,
            @RequestParam @Parameter(description = "Tamanho da página") int size,
            Authentication authentication) {
        logger.debug("getPosts called with page: {} and size: {}", page, size);
        Long userId = jwtService.getUserIdFromAuthentication(authentication);

        Pageable pageable = PageRequest.of(page, size);
        Page<PostData> posts = postService.getPosts(pageable, userId);
        return buildResponse(posts);
    }

    @Operation(summary = "Criar um novo post",
            security = {@SecurityRequirement(name = "bearerAuth")}
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Post criado",
                    content = { @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Post.class)) }),
            @ApiResponse(responseCode = "400", description = "Entrada inválida",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autorizado",
                    content = @Content)
    })
    @PostMapping
    public ResponseEntity<PostData> createPost(
            @Valid @RequestBody PostRequest post,
            Authentication authentication) {
        logger.debug("createPost called with post: {}", post);

        Long userId = jwtService.getUserIdFromAuthentication(authentication);

        PostData createdPost = postService.createPost(post, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdPost);
    }

    @Operation(summary = "Obter um post pelo ID",
            security = {@SecurityRequirement(name = "bearerAuth")}
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Post encontrado",
                    content = { @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Post.class)) }),
            @ApiResponse(responseCode = "404", description = "Post não encontrado",
                    content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<PostData> getPostById(@PathVariable Long id,
                                                Authentication authentication) {
        logger.debug("getPostById called with id: {}", id);
        Long userId = jwtService.getUserIdFromAuthentication(authentication);

        PostData post = postService.getPostById(id,0, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Post não encontrado com o id " + id));
        return ResponseEntity.ok(post);
    }

    @Operation(summary = "Obter posts pelo ID do post pai com paginação",
            security = {@SecurityRequirement(name = "bearerAuth")}
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Posts encontrados",
                    content = { @Content(mediaType = "application/json") }),
            @ApiResponse(responseCode = "404", description = "Posts não encontrados",
                    content = @Content)
    })
    @GetMapping("/parent/{parentPostId}")
    public Map<String, Object> getPostsByParentPostId(
            @PathVariable Long parentPostId,
            @RequestParam @Parameter(description = "Número da página") int page,
            @RequestParam @Parameter(description = "Tamanho da página") int size,
            Authentication authentication) {
        logger.debug("getPostsByParentPostId called with parentPostId: {}, page: {} and size: {}", parentPostId, page, size);
        Long userId = jwtService.getUserIdFromAuthentication(authentication);

        Pageable pageable = PageRequest.of(page, size);
        Page<PostData> posts = postService.getPostsByParentPostId(parentPostId, pageable, userId);
        return buildResponse(posts);
    }

    @Operation(summary = "Obter thread de posts pelo ID do post pai com paginação",
            security = {@SecurityRequirement(name = "bearerAuth")}
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Thread de posts encontrada",
                    content = { @Content(mediaType = "application/json") }),
            @ApiResponse(responseCode = "404", description = "Thread de posts não encontrada",
                    content = @Content)
    })
    @GetMapping("/thread/{parentPostId}")
    public PostThread getThreadByParentPostId(
            @PathVariable Long parentPostId,
            @RequestParam @NotNull @Parameter(description = "Número da página") int page,
            @RequestParam @NotNull @Parameter(description = "Tamanho da página") int size,
            Authentication authentication) {
        logger.debug("getThreadByParentPostId called with parentPostId: {}, page: {} and size: {}", parentPostId, page, size);
        Long userId = (authentication != null) ? jwtService.getUserIdFromAuthentication(authentication) : null;

        Pageable pageable = PageRequest.of(page, size);
        return postService.getPostThread(parentPostId, pageable, userId);
    }

    @Operation(summary = "Obter posts pelo ID do usuário com paginação",
            security = {@SecurityRequirement(name = "bearerAuth")}
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Posts encontrados",
                    content = { @Content(mediaType = "application/json") }),
            @ApiResponse(responseCode = "404", description = "Posts não encontrados",
                    content = @Content)
    })
    @GetMapping("/user/{username}")
    public Map<String, Object> getPostsByUser(
            @PathVariable String username,
            @RequestParam @Parameter(description = "Número da página") int page,
            @RequestParam @Parameter(description = "Tamanho da página") int size,
            Authentication authentication) {
        logger.debug("getPostsByUser called with username: {}, page: {} and size: {}", username, page, size);
        Pageable pageable = PageRequest.of(page, size);
        Long authenticatedUserId = jwtService.getUserIdFromAuthentication(authentication);
        Page<PostData> posts = postService.getPostsByUser(username, authenticatedUserId, pageable);
        return buildResponse(posts);
    }

    @Operation(summary = "Deletar um post pelo ID",
            security = {@SecurityRequirement(name = "bearerAuth")}
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Post deletado",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autorizado",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Post não encontrado",
                    content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(
            @PathVariable Long id,
            Authentication authentication) {
        logger.debug("deletePost called with id: {}", id);

        Long userId = jwtService.getUserIdFromAuthentication(authentication);

        PostData existingPost = postService.getPostById(id,0, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Post não encontrado com o id " + id));

        if(existingPost.isDeleted()) {
            return ResponseEntity.noContent().build();
        }

        postService.deletePost(id, userId);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> buildResponse(Page<PostData> posts) {
        Map<String, Object> response = new HashMap<>();
        response.put("content", posts.getContent());
        response.put("page", posts.getNumber());
        response.put("size", posts.getSize());
        response.put("totalElements", posts.getTotalElements());
        response.put("totalPages", posts.getTotalPages());
        return response;
    }

    @Operation(summary = "Curtir um post",
            security = {@SecurityRequirement(name = "bearerAuth")}
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Post curtido",
                    content = @Content),
            @ApiResponse(responseCode = "400", description = "Entrada inválida",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autorizado",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Post não encontrado",
                    content = @Content)
    })
    @PostMapping("/{id}/like")
    public ResponseEntity<Void> likePost(
            @PathVariable @NotNull(message = "Post ID cant be NULL") Long id,
            Authentication authentication) {
        logger.debug("likePost called with id: {}", id);

        Long userId = jwtService.getUserIdFromAuthentication(authentication);

        likeService.likePost(id, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Descurtir um post",
            security = {@SecurityRequirement(name = "bearerAuth")}
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Post descurtido",
                    content = @Content),
            @ApiResponse(responseCode = "400", description = "Entrada inválida",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autorizado",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Post não encontrado",
                    content = @Content)
    })
    @DeleteMapping("/{id}/like")
    public ResponseEntity<Void> unlikePost(
            @PathVariable @NotNull(message = "Post ID cant be NULL") Long id,
            Authentication authentication) {
        logger.debug("unlikePost called with id: {}", id);
        Long userId = jwtService.getUserIdFromAuthentication(authentication);

        likeService.unlikePost(id, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "registra a visualização de um post",
            security = {@SecurityRequirement(name = "bearerAuth")}
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Post visualizado",
                    content = @Content),
            @ApiResponse(responseCode = "400", description = "Entrada inválida",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autorizado",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Post não encontrado",
                    content = @Content)
    })
    @PostMapping("/{id}/view")
    public ResponseEntity<Void> viewPost(
            @PathVariable @NotNull(message = "Post ID cant be NULL") Long id,
            Authentication authentication) {
        logger.debug("viewPost called with id: {}", id);
        Long userId = jwtService.getUserIdFromAuthentication(authentication);

        postService.viewPost(id, userId);
        return ResponseEntity.noContent().build();
    }
}