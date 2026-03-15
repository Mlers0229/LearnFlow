package com.learnflow.dto;

/**
 * 登录 / 注册成功后返回给前端的简要用户信息。
 *
 * 为了简单起见，这里暂时不返回 token，只返回基本信息，
 * 前端可以将其保存在 localStorage 中，用于展示用户名和角色。
 */
public class AuthResponse {

    private Long id;

    private String username;

    private String role;

    private String level;

    private String status;
    private String email;

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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}



