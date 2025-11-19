package com.toiter.userservice.model;

/**
 * Jackson views used to control which fields are serialized in HTTP responses vs cache.
 * - Public: fields exposed in API responses
 * - Cache: fields stored in cache (includes Public)
 */
public class Views {
    public static class Public {}
    public static class Cache extends Public {}
}

