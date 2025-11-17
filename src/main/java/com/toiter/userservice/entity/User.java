package com.toiter.userservice.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Username is required")
    @Size(max = 255, message = "Username must be less than 255 characters")
    private String username;

    @NotBlank(message = "Display name is required")
    @Size(min = 4, max = 30, message = "Display name must be between 4 and 30 characters")
    private String displayName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters long")
    private String password;

    private String bio;

    private Long profileImageId;

    private Long headerImageId;

    private LocalDateTime creationDate = LocalDateTime.now();

    public User() {}

    public User(Long id, String username, String displayName, String email, String password, String bio,
                Long profileImageId, Long headerImageId, LocalDateTime creationDate) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.password = password;
        this.bio = bio;
        this.profileImageId = profileImageId;
        this.headerImageId = headerImageId;
        this.creationDate = creationDate;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public Long getProfileImageId() {
        return profileImageId;
    }

    public void setProfileImageId(Long profileImageId) {
        this.profileImageId = profileImageId;
    }

    public Long getHeaderImageId() {
        return headerImageId;
    }

    public void setHeaderImageId(Long headerImageId) {
        this.headerImageId = headerImageId;
    }

    public LocalDateTime getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(LocalDateTime creationDate) {
        this.creationDate = creationDate;
    }
}
