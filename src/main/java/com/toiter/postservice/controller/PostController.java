package com.toiter.postservice.controller;

import com.toiter.postservice.entity.Post;
import com.toiter.postservice.model.PostRequest;
import com.toiter.postservice.service.JwtService;
import com.toiter.postservice.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
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
    private final JwtService jwtService;

    public PostController(PostService postService, JwtService jwtService) {
        this.postService = postService;
        this.jwtService = jwtService;
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
    public ResponseEntity<Post> createPost(
            @Valid @RequestBody PostRequest post,
            Authentication authentication) {

        Long userId = jwtService.getUserIdFromAuthentication(authentication);

        Post createdPost = postService.createPost(post, userId);
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
    public ResponseEntity<Post> getPostById(@PathVariable Long id) {
        Post post = postService.getPostById(id)
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
            @RequestParam @Parameter(description = "Tamanho da página") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Post> posts = postService.getPostsByParentPostId(parentPostId, pageable);
        return buildResponse(posts);
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
            @RequestParam @Parameter(description = "Tamanho da página") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Post> posts = postService.getPostsByUser(username, pageable);
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

        Long userId = jwtService.getUserIdFromAuthentication(authentication);

        Post existingPost = postService.getPostById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post não encontrado com o id " + id));

        if (!existingPost.getUserId().equals(userId)) {
            throw new RuntimeException("Você não tem permissão para deletar este post");
        }

        postService.deletePost(id);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> buildResponse(Page<Post> posts) {
        Map<String, Object> response = new HashMap<>();
        response.put("content", posts.getContent());
        response.put("page", posts.getNumber());
        response.put("size", posts.getSize());
        response.put("totalElements", posts.getTotalElements());
        response.put("totalPages", posts.getTotalPages());
        return response;
    }
}