# Modelo de Segurança - Toiter Post Service

## Visão Geral

O Post Service implementa um modelo de autenticação robusto baseado em **cookies HttpOnly** para clientes browser e **Authorization headers** para clientes não-browser. Este documento descreve o modelo de segurança, fluxo de autenticação e melhores práticas.

## Princípios de Segurança

### 1. JWT Nunca Exposto ao JavaScript
- O token JWT é armazenado em cookies **HttpOnly** 
- O JavaScript do browser **nunca** tem acesso ao valor do token
- Proteção contra ataques XSS (Cross-Site Scripting)
- O navegador envia o cookie automaticamente em cada requisição

### 2. Defesa em Profundidade
- Múltiplas camadas de segurança
- Validação de token em cada requisição
- Mensagens de erro genéricas (não expõem detalhes do token)
- Logs seguros (nunca incluem o conteúdo do JWT)

### 3. Separação de Responsabilidades
- **Autenticação**: Gerenciada pelo User Service
- **Autorização**: Validada pelo Post Service
- **Tokens de Serviço**: Compartilhados entre microsserviços

## Tipos de Autenticação

### 1. Cookie HttpOnly (Browser Clients)

**Quando usar:**
- Aplicações web rodando no navegador
- Chamadas AJAX/Fetch do frontend

**Como funciona:**
1. User Service cria o JWT após login bem-sucedido
2. User Service define cookie HttpOnly `accessToken` na resposta
3. Browser armazena o cookie automaticamente
4. Browser envia o cookie em todas as requisições subsequentes
5. Post Service extrai o JWT do cookie e valida

**Vantagens:**
- Mais seguro contra XSS
- Gerenciamento automático pelo browser
- Não requer código JavaScript para incluir token

**Exemplo de requisição (automática pelo browser):**
```javascript
// Frontend (React/Vue/Angular)
fetch('http://localhost:9991/posts?page=0&size=10', {
  method: 'GET',
  credentials: 'include'  // Importante: envia cookies
})
```

### 2. Authorization Header (Non-Browser Clients)

**Quando usar:**
- Aplicativos móveis (iOS, Android)
- CLIs e ferramentas de linha de comando
- Testes automatizados
- Serviços backend

**Como funciona:**
1. Cliente obtém JWT do User Service
2. Cliente armazena JWT de forma segura (Keychain, SecureStorage, etc.)
3. Cliente inclui JWT no header `Authorization: Bearer <token>` em cada requisição
4. Post Service extrai e valida o JWT

**Exemplo:**
```bash
curl -X GET http://localhost:9991/posts?page=0&size=10 \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

### 3. Shared Key (Service-to-Service)

**Quando usar:**
- Comunicação entre microsserviços
- Endpoints `/internal/**`
- Operações administrativas

**Como funciona:**
1. Configurar `service.shared-key` em ambos os serviços
2. Serviço chamador inclui `Authorization: Bearer <shared-key>`
3. Post Service valida que o token corresponde à shared key

**Exemplo:**
```bash
curl -X GET http://localhost:9991/internal/posts/count?userId=123 \
  -H "Authorization: Bearer my-shared-secret-key"
```

## Fluxo de Autenticação

### Diagrama de Fluxo

```
┌─────────────────┐
│ Requisição HTTP │
└────────┬────────┘
         │
         ▼
┌────────────────────────┐
│ É rota pública?        │ ──Yes──> [Permitir]
│ (/swagger-ui, etc)     │
└────────┬───────────────┘
         │ No
         ▼
┌────────────────────────┐
│ É rota /internal/**?   │ ──Yes──> [Validar Shared Key] ──> [Permitir/Negar]
└────────┬───────────────┘
         │ No
         ▼
┌────────────────────────┐
│ Extrair JWT do Cookie  │
│ 'accessToken'          │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│ JWT encontrado?        │ ──No──> [Tentar Header Authorization]
└────────┬───────────────┘                    │
         │ Yes                                │
         │                                    ▼
         │                    ┌──────────────────────────┐
         │                    │ JWT no Header?           │ ──No──> [Continuar sem auth]
         │                    └────────┬─────────────────┘
         │                             │ Yes
         │                             │
         └─────────────┬───────────────┘
                       │
                       ▼
         ┌─────────────────────────┐
         │ Validar JWT             │
         │ - Verificar assinatura  │
         │ - Verificar expiração   │
         │ - Extrair userId        │
         └─────────┬───────────────┘
                   │
                   ▼
         ┌─────────────────────────┐
         │ Token válido?           │ ──No──> [Retornar 401 Unauthorized]
         └─────────┬───────────────┘
                   │ Yes
                   ▼
         ┌─────────────────────────┐
         │ Criar Authentication    │
         │ com userId + ROLE_USER  │
         └─────────┬───────────────┘
                   │
                   ▼
         ┌─────────────────────────┐
         │ Definir no              │
         │ SecurityContext         │
         └─────────┬───────────────┘
                   │
                   ▼
              [Permitir]
```

## Implementação

### JwtAuthenticationFilter

Classe: `com.toiter.postservice.config.JwtAuthenticationFilter`

**Responsabilidades:**
1. Interceptar todas as requisições HTTP
2. Identificar tipo de rota (pública, interna, autenticada)
3. Extrair JWT (cookie ou header)
4. Validar token
5. Preencher SecurityContext

**Métodos principais:**
```java
// Extrai JWT do cookie HttpOnly
private String extractJwtFromCookie(HttpServletRequest request)

// Processa requisição e valida autenticação
protected void doFilterInternal(HttpServletRequest request, 
                               HttpServletResponse response, 
                               FilterChain filterChain)
```

### SecurityConfig

Classe: `com.toiter.postservice.config.SecurityConfig`

**Configurações:**
- Define rotas públicas que não requerem autenticação
- Configura CORS com suporte a cookies (`credentials: true`)
- Registra `JwtAuthenticationFilter` na cadeia de filtros
- Define permissões de acesso por padrão de URL

### JwtService

Classe: `com.toiter.postservice.service.JwtService`

**Operações:**
- `extractUsername(token)`: Extrai username do JWT
- `extractUserId(token)`: Extrai userId do JWT
- `isTokenValid(token)`: Valida assinatura e expiração
- `getUserIdFromAuthentication(auth)`: Obtém userId do contexto Spring Security

## Rotas e Permissões

### Rotas Públicas (permitAll)
- `/v3/api-docs/**` - Documentação OpenAPI
- `/swagger-ui/**` - Interface Swagger
- `/posts/thread/**` - Visualização pública de threads

### Rotas Internas (shared key)
- `/internal/**` - Comunicação serviço-a-serviço
- `/api/internal/**` - Variante com prefixo `/api`

### Rotas Autenticadas (authenticated)
- Todas as outras rotas requerem JWT válido
- Exemplos: `/posts`, `/posts/{id}`, `/posts/{id}/like`

## Configuração CORS

```java
// Origens permitidas
- http://localhost:3000
- https://localhost:3000
- https://toiter.joaoplmoreno.com

// Métodos permitidos
- GET, POST, PUT, DELETE, OPTIONS

// Headers
- Todos os headers permitidos (*)

// Credentials
- allowCredentials: true (necessário para cookies)
```

## Tratamento de Erros

### Token Inválido/Malformado
```
HTTP 401 Unauthorized
Body: "Token inválido ou malformado"
```

### Token Expirado
```
HTTP 401 Unauthorized
Body: "Token inválido ou expirado"
```

### Acesso Interno Não Autorizado
```
HTTP 401 Unauthorized
Body: "Acesso não autorizado"
```

### Sem Token (rota protegida)
- Continua a requisição sem autenticação
- Spring Security bloqueia com 403 Forbidden

## Logs de Segurança

### O que É Logado
✅ Tipo de requisição (método + path)  
✅ UserId extraído (após validação bem-sucedida)  
✅ Mensagens de erro genéricas  
✅ Tentativas de acesso não autorizado  

### O que NÃO É Logado
❌ Conteúdo do JWT  
❌ Valor do token  
❌ Detalhes internos do token  
❌ Informações que possam expor dados sensíveis  

**Exemplo de log seguro:**
```
INFO  Request: GET /posts
DEBUG JWT extraído do cookie HttpOnly 'accessToken'
DEBUG Usuário autenticado com sucesso - userId: 123
```

## Melhores Práticas

### Para Frontend (Browser)
1. **Use `credentials: 'include'` em todas as requisições:**
   ```javascript
   fetch(url, { credentials: 'include' })
   ```

2. **Não tente ler ou manipular o cookie:**
   - O cookie é HttpOnly, JavaScript não pode acessá-lo
   - Confie no navegador para gerenciar cookies

3. **Trate erros 401 adequadamente:**
   - Redirecione para login
   - Limpe estado da aplicação

### Para Mobile/CLI
1. **Armazene JWT de forma segura:**
   - iOS: Keychain
   - Android: EncryptedSharedPreferences
   - Desktop: Sistema de credenciais do SO

2. **Inclua token em cada requisição:**
   ```
   Authorization: Bearer <token>
   ```

3. **Implemente refresh token:**
   - Solicite novo token antes da expiração
   - Trate 401 obtendo novo token

### Para Microsserviços
1. **Use shared key para comunicação interna:**
   - Configure `service.shared-key` em ambos os serviços
   - Use endpoints `/internal/**`

2. **Não misture autenticação de usuário com serviço:**
   - Endpoints internos não precisam de JWT de usuário
   - Shared key é suficiente

## Testes

### Testes Unitários
A classe `JwtAuthenticationFilterTest` contém 15 testes cobrindo:
- Rotas públicas
- Autenticação via cookie
- Autenticação via header (fallback)
- Precedência de cookie sobre header
- Rotas internas com shared key
- Tokens inválidos/expirados
- Múltiplos cookies

### Executar Testes
```bash
./gradlew test --tests JwtAuthenticationFilterTest
```

## Troubleshooting

### Cookie não está sendo enviado
- Verifique que `credentials: 'include'` está configurado
- Confirme que CORS permite a origem
- Verifique se `allowCredentials: true` está configurado

### 401 em rotas autenticadas
- Verifique se o cookie `accessToken` está presente
- Confirme que o token não expirou
- Teste com header Authorization como fallback

### Endpoint interno retorna 401
- Verifique se `service.shared-key` está configurado
- Confirme que o header Authorization está correto
- Use formato: `Bearer <shared-key>`

## Segurança Adicional

### Recomendações de Produção
1. **HTTPS obrigatório:**
   - Cookies HttpOnly devem usar `Secure` flag
   - Previne interceptação man-in-the-middle

2. **SameSite Cookie:**
   - Configure `SameSite=Strict` ou `SameSite=Lax`
   - Proteção adicional contra CSRF

3. **Rotação de Secrets:**
   - Altere `jwt.secret` periodicamente
   - Altere `service.shared-key` periodicamente

4. **Monitoramento:**
   - Monitore tentativas de acesso não autorizado
   - Configure alertas para padrões suspeitos

5. **Rate Limiting:**
   - Implemente limite de requisições por IP
   - Previne ataques de força bruta

## Referências

- [OWASP JWT Best Practices](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html)
- [Spring Security Documentation](https://docs.spring.io/spring-security/reference/index.html)
- [HttpOnly Cookies](https://owasp.org/www-community/HttpOnly)
