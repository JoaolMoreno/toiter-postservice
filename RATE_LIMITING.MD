# Rate Limiting Implementation

## Overview
This document describes the rate limiting implementation for the Toiter Post Service.

## Implementation Details

### Components

1. **RateLimitService** (`src/main/java/com/toiter/postservice/service/RateLimitService.java`)
    - Manages rate limit logic using Redis
    - Implements sliding window algorithm
    - Tracks requests per user/IP address
    - Configurable limits via application.properties

2. **RateLimitFilter** (`src/main/java/com/toiter/postservice/config/RateLimitFilter.java`)
    - Spring filter that runs before authentication (@Order(1))
    - Intercepts all requests except excluded paths
    - Extracts user ID from SecurityContext or IP address for unauthenticated users
    - Returns HTTP 429 when rate limit exceeded

### Rate Limits

| Request Type | Limit | Window | Use Case |
|-------------|-------|--------|----------|
| GET | 100 requests | 60 seconds | Read operations (higher limit) |
| POST/PUT/DELETE | 30 requests | 60 seconds | Write operations (more restrictive) |

### Configuration

```properties
# Rate Limiting Configuration
rate-limit.get.requests=100
rate-limit.get.window-seconds=60
rate-limit.other.requests=30
rate-limit.other.window-seconds=60
```

### Rate Limit Keys in Redis

- Authenticated users: `rate_limit:user:{userId}:{requestType}`
- Unauthenticated users: `rate_limit:ip:{ipAddress}:{requestType}`

### Response Headers

When rate limiting is active, the following headers are included in responses:

- `X-RateLimit-Limit`: Total number of requests allowed in the window
- `X-RateLimit-Remaining`: Number of requests remaining
- `X-RateLimit-Reset`: Unix timestamp when the limit resets

### HTTP 429 Response

When rate limit is exceeded:

```json
{
  "error": "Rate limit exceeded",
  "message": "Too many requests. Please try again in 45 seconds."
}
```

Headers:
- Status: 429 Too Many Requests
- `Retry-After`: Seconds until limit resets

### Excluded Paths

The following paths are excluded from rate limiting:
- `/v3/api-docs/**` (API documentation)
- `/swagger-ui/**` (Swagger UI)
- `/images/**` (Static image resources)
- `/internal/**` (Internal service endpoints)

### IP Address Extraction

The filter supports extraction of real client IP from proxy headers:
1. `X-Forwarded-For` header (takes first IP if multiple)
2. `X-Real-IP` header
3. `request.getRemoteAddr()` as fallback

### Testing

Unit tests are available in `src/test/java/com/toiter/postservice/service/RateLimitServiceTest.java`

Run tests:
```bash
./gradlew test --tests RateLimitServiceTest
```

### Security Considerations

1. **IP-based Limiting**: Unauthenticated users tracked by IP address
2. **Per-user Isolation**: Each authenticated user has independent rate limits
3. **Distributed**: Redis-based implementation supports multiple service instances

### Future Improvements

Potential enhancements:
- Dynamic rate limits based on user tier/subscription
- Whitelist for trusted IPs
- Rate limit bypass for admin users
- Metrics and monitoring integration
