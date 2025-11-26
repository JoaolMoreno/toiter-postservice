package com.toiter.postservice.model;

public class ImageUploadResult {
    private String key;
    private Integer width;
    private Integer height;

    public ImageUploadResult(String key, Integer width, Integer height) {
        this.key = key;
        this.width = width;
        this.height = height;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }
}

