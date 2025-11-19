## **Toiter - Post Service**

O **Post Service** é um dos microsserviços do ecossistema **Toiter**, responsável pelo gerenciamento de postagens, respostas e repostagens. Ele fornece funcionalidades para criar, editar, listar e excluir posts, além de gerenciar interações como curtidas e repostagens. Este serviço também emite eventos para sincronização com outros serviços, como o **Feed Service** e o **Notification Service**.

---

### **🔒 Atualizações Recentes - Segurança**

**Refatoração de Autenticação (2025)**
- ✅ Implementado suporte a **cookies HttpOnly** para proteger JWT contra XSS
- ✅ Mantido suporte a **header Authorization** como fallback para clientes não-browser
- ✅ **15 testes automatizados** cobrindo todos os fluxos de autenticação
- ✅ **0 vulnerabilidades** detectadas no CodeQL
- ✅ Logs sanitizados (nunca expõem o conteúdo do JWT)
- ✅ Documentação completa em [SECURITY.md](SECURITY.md)

**Benefícios:**
- Maior segurança para aplicações web (proteção contra XSS)
- Compatibilidade com múltiplos tipos de clientes (browser, mobile, CLI)
- Comunicação segura entre microsserviços

---

### **Funcionalidades Principais**

#### **1. Gerenciamento de Postagens**
- Criação, edição e exclusão de postagens.
- Suporte a respostas (threads) e repostagens.
- Estrutura flexível onde todas as interações são tratadas como posts, com relações hierárquicas.

#### **2. Integração com Kafka**
- **Emissão de Eventos**:
    - `PostCreatedEvent`: Emitido ao criar um novo post (original, resposta ou repostagem).
    - `PostDeletedEvent`: Emitido ao excluir um post.
- **Consumo de Eventos**:
    - Pode consumir eventos para integrar com outros serviços (e.g., atualizações de usuários).

#### **3. Endpoints Internos**
- Endpoints `/internal/**` para consultas internas, acessíveis apenas por outros microsserviços autorizados.

#### **4. Threads e Hierarquia**
- Suporte à criação de threads hierárquicas:
    - **Posts Originais**: Raiz de uma thread.
    - **Respostas**: Relacionadas ao `parent_post_id`.
    - **Repostagens**: Relacionadas ao `repost_id`.

#### **5. Autenticação e Autorização**

O Post Service implementa um modelo de autenticação seguro baseado em cookies HttpOnly:

> 📖 **Documentação Completa:** Consulte [SECURITY.md](SECURITY.md) para detalhes completos sobre o modelo de segurança, fluxos de autenticação e troubleshooting.

##### **Autenticação para Clientes Browser (Frontend Web)**
- Utiliza **cookies HttpOnly** para armazenar o JWT (`accessToken`)
- O token **nunca** é exposto ao JavaScript do navegador
- Proteção contra ataques XSS (Cross-Site Scripting)
- O cookie é enviado automaticamente pelo navegador em cada requisição
- CORS configurado com `credentials: true` para permitir envio de cookies

##### **Autenticação para Clientes Não-Browser**
- Clientes móveis, CLI e outros serviços podem usar o header `Authorization: Bearer <token>`
- Funciona como fallback quando cookies não estão disponíveis
- Útil para testes e integrações

##### **Endpoints Internos (`/internal/**`)**
- Protegidos com token compartilhado (`shared-key`)
- Usados para comunicação serviço-a-serviço
- Não dependem de cookies de usuário
- Exemplo: `/api/internal/posts/count`

##### **Fluxo de Autenticação**
1. **Para rotas públicas não-autenticadas**: O filtro permite acesso direto (ex: `/swagger-ui`, `/api/posts/thread/{id}`)
2. **Para rotas internas**: Valida o token compartilhado no header `Authorization`
3. **Para rotas autenticadas**:
   - Primeiro tenta ler o JWT do cookie HttpOnly `accessToken`
   - Se não encontrar cookie, tenta o header `Authorization` como fallback
   - Valida o token e preenche o contexto de segurança do Spring
   - Em caso de token inválido/expirado, retorna `401 Unauthorized`

##### **Segurança Implementada**
- **Nunca loga o conteúdo do JWT** - apenas userId e mensagens genéricas
- **Mensagens de erro genéricas** - não expõe detalhes do token
- **Cookie HttpOnly** - previne acesso via JavaScript
- **Validação rigorosa** - expira tokens e rejeita tokens malformados
- **Separação clara** - rotas públicas, autenticadas e internas têm tratamentos distintos

---

### **Endpoints Disponíveis**

> **Nota:** Todos os endpoints possuem o prefixo `/api` (contexto da aplicação).

#### **1. Postagens**
| Método   | Endpoint                    | Descrição                                 | Autenticação |
|----------|-----------------------------|-------------------------------------------|--------------|
| `POST`   | `/api/posts`                | Cria uma nova postagem.                   | JWT (Cookie ou Header) |
| `GET`    | `/api/posts?page=0&size=10` | Lista postagens com paginação.            | JWT (Cookie ou Header) |
| `GET`    | `/api/posts/{id}`           | Retorna os detalhes de uma postagem.      | JWT (Cookie ou Header) |
| `GET`    | `/api/posts/user/{username}`| Lista as postagens de um usuário.         | JWT (Cookie ou Header) |
| `GET`    | `/api/posts/parent/{id}`    | Lista respostas de uma postagem.          | JWT (Cookie ou Header) |
| `GET`    | `/api/posts/thread/{id}`    | Visualiza thread completa (público).      | Não requerida |
| `DELETE` | `/api/posts/{id}`           | Exclui uma postagem.                      | JWT (Cookie ou Header) |
| `POST`   | `/api/posts/{id}/like`      | Curte uma postagem.                       | JWT (Cookie ou Header) |
| `DELETE` | `/api/posts/{id}/like`      | Remove curtida de uma postagem.           | JWT (Cookie ou Header) |
| `POST`   | `/api/posts/{id}/view`      | Registra visualização de uma postagem.    | JWT (Cookie ou Header) |

#### **2. Endpoints Internos (Serviço-a-Serviço)**
| Método   | Endpoint                           | Descrição                          | Autenticação |
|----------|------------------------------------|------------------------------------|--------------|
| `GET`    | `/api/internal/posts/count`        | Retorna contagem de posts do usuário | Shared Key   |

#### **3. Documentação**
| Método   | Endpoint                    | Descrição                                 |
|----------|-----------------------------|-------------------------------------------|
| `GET`    | `/api/api-docs`             | Documentação Swagger UI                   |
| `GET`    | `/api/v3/api-docs`          | Especificação OpenAPI JSON                |

---

### **Consumo e Emissão de Eventos Kafka**

#### **1. Eventos Emitidos**
##### **`PostCreatedEvent`**
- Emitido ao criar uma postagem (original, resposta ou repostagem).
- Campos:
    - `postId`: ID do post criado.
    - `userId`: ID do usuário que criou o post.
    - `content`: Conteúdo do post.
    - `parentPostId`: ID do post pai (em caso de resposta).
    - `repostId`: ID do post original (em caso de repostagem).

##### **`PostDeletedEvent`**
- Emitido ao excluir uma postagem.
- Campos:
    - `postId`

---

### **Arquitetura e Tecnologias**

#### **1. Banco de Dados**
- **PostgreSQL**:
    - Tabela `posts`:
        ```sql
        CREATE TABLE posts (
            id SERIAL PRIMARY KEY,
            parent_post_id INTEGER REFERENCES posts (id),
            repost_id INTEGER REFERENCES posts (id),
            user_id INTEGER NOT NULL,
            content TEXT NOT NULL,
            media_url TEXT,
            created_at TIMESTAMP DEFAULT NOW(),
            updated_at TIMESTAMP DEFAULT NOW()
        );
        ```
    - Índices:
        - `parent_post_id` e `user_id` para consultas rápidas.

#### **2. Mensageria**
- **Apache Kafka**:
    - Tópicos:
        - `post-created-topic`
        - `post-deleted-topic`

#### **3. Segurança**
- **Spring Security com JWT e HttpOnly Cookies**:
    - Autenticação principal via cookies HttpOnly para clientes browser
    - Fallback para header `Authorization` para clientes não-browser
    - Proteção contra XSS mantendo JWT fora do alcance do JavaScript
    - CORS configurado com `credentials: true` para suportar cookies

- **Token Compartilhado para Endpoints Internos**:
    - Acesso restrito aos endpoints `/internal/**` via token compartilhado (`shared-key`)
    - Usado para comunicação segura entre microsserviços

- **Configurações de Segurança**:
    - `JwtAuthenticationFilter`: Extrai e valida JWT de cookies ou headers
    - `SecurityConfig`: Define regras de autorização e configuração CORS
    - Logs seguros: nunca expõe conteúdo do JWT

---

### **Como Executar**

#### **Requisitos**
- Java 21 ou superior
- PostgreSQL
- Redis
- Apache Kafka
- Gradle (incluído via wrapper)

1. **Clone o repositório:**
   ```bash
   git clone https://github.com/JoaolMoreno/toiter-postservice.git
   cd toiter-postservice
   ```

2. **Configure as variáveis de ambiente:**
   
   Copie o arquivo `.env.example` e configure as variáveis:
   ```bash
   cp .env.example .env
   ```
   
   Edite o arquivo `.env` com suas configurações:
   ```properties
   # Banco de dados PostgreSQL
   POSTGRES_USER=postgres
   POSTGRES_PASSWORD=sua-senha
   SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/toiter
   SPRING_DATASOURCE_USERNAME=postgres
   SPRING_DATASOURCE_PASSWORD=sua-senha
   
   # Redis
   SPRING_DATA_REDIS_HOST=localhost
   SPRING_DATA_REDIS_PORT=6379
   SPRING_DATA_REDIS_PASSWORD=
   
   # Kafka
   SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092
   
   # JWT - Deve ser o mesmo usado no User Service
   JWT_SECRET=sua-chave-secreta-jwt
   JWT_ACCESS_TOKEN_EXPIRATION=3600000
   JWT_REFRESH_TOKEN_EXPIRATION=86400000
   
   # Integração com outros serviços
   SERVICE_USER_URL=http://localhost:9990/api/internal
   SERVICE_SHARED_KEY=T0iter
   
   # Servidor
   SERVER_URL=http://localhost:9991
   
   # Hibernate
   SPRING_JPA_HIBERNATE_DDL-AUTO=update
   SPRING_JPA_DATABASE-PLATFORM=org.hibernate.dialect.PostgreSQLDialect
   SPRING_JPA_PROPERTIES_HIBERNATE_DEFAULT_SCHEMA=public
   ```

3. **Suba os serviços necessários:**
   
   Use o Docker Compose para subir PostgreSQL, Redis e Kafka:
   ```bash
   docker-compose up -d
   ```

4. **Execute o microsserviço:**
   ```bash
   ./gradlew bootRun
   ```
   
   Ou para gerar o JAR e executar:
   ```bash
   ./gradlew clean build
   java -jar build/libs/app.jar
   ```

5. **Acesse a API:**
   
   A API estará disponível em `http://localhost:9991/api`
   
   Documentação Swagger: `http://localhost:9991/api/api-docs`

6. **Testando Autenticação:**
   
   > **Nota:** O contexto da aplicação é `/api`, então todos os endpoints devem incluir este prefixo.
   
   **Com Cookie HttpOnly (simulando browser):**
   ```bash
   # O cookie é definido automaticamente pelo User Service após login
   # Para testar manualmente:
   curl -X GET "http://localhost:9991/api/posts?page=0&size=10" \
     -H "Cookie: accessToken=seu-jwt-token-aqui"
   ```
   
   **Com Header Authorization (cliente não-browser):**
   ```bash
   curl -X GET "http://localhost:9991/api/posts?page=0&size=10" \
     -H "Authorization: Bearer seu-jwt-token-aqui"
   ```
   
   **Endpoint Interno (serviço-a-serviço):**
   ```bash
   curl -X GET "http://localhost:9991/api/internal/posts/count?userId=123" \
     -H "Authorization: Bearer T0iter"
   ```

7. **Executar Testes:**
   ```bash
   ./gradlew test
   ```
   
   Para ver o relatório de testes:
   ```bash
   ./gradlew test --info
   # Relatório HTML em: build/reports/tests/test/index.html
   ```

---

### **Estrutura do Projeto**

```
toiter-postservice/
├── src/
│   ├── main/
│   │   ├── java/com/toiter/postservice/
│   │   │   ├── controller/       # Controladores REST
│   │   │   │   ├── PostController.java
│   │   │   │   └── InternalPostController.java
│   │   │   ├── service/          # Lógica de negócio
│   │   │   │   ├── PostService.java
│   │   │   │   ├── LikeService.java
│   │   │   │   ├── JwtService.java
│   │   │   │   ├── UserClientService.java
│   │   │   │   └── CacheService.java
│   │   │   ├── repository/       # Acesso ao banco de dados
│   │   │   ├── model/            # DTOs e eventos Kafka
│   │   │   ├── entity/           # Entidades JPA
│   │   │   ├── config/           # Configurações do Spring e Kafka
│   │   │   │   ├── JwtAuthenticationFilter.java
│   │   │   │   └── SecurityConfig.java
│   │   │   ├── producer/         # Emissão de eventos Kafka
│   │   │   └── consumer/         # Consumo de eventos Kafka
│   │   └── resources/
│   │       └── application.properties    # Configurações (usa variáveis de ambiente)
│   └── test/
│       └── java/com/toiter/postservice/
│           └── config/
│               └── JwtAuthenticationFilterTest.java
├── build.gradle                  # Configuração do Gradle
├── gradlew                       # Gradle Wrapper (Unix)
├── gradlew.bat                   # Gradle Wrapper (Windows)
├── docker-compose.yml            # Configuração para serviços de infraestrutura
├── .env.example                  # Exemplo de variáveis de ambiente
├── README.md                     # Este arquivo
└── SECURITY.md                   # Documentação detalhada de segurança
```

---

### **Testes**

O projeto inclui testes automatizados para garantir a qualidade e segurança do código:

#### **Testes de Autenticação**
- **JwtAuthenticationFilterTest**: 15 testes cobrindo todos os cenários de autenticação
  - Autenticação via cookie HttpOnly
  - Fallback para header Authorization
  - Precedência de cookie sobre header
  - Validação de rotas internas com shared key
  - Tratamento de tokens inválidos/expirados
  - Acesso a rotas públicas

#### **Executar os Testes**
```bash
# Executar todos os testes
./gradlew test

# Executar testes específicos
./gradlew test --tests JwtAuthenticationFilterTest

# Gerar relatório de cobertura
./gradlew test jacocoTestReport
```

Os relatórios de teste são gerados em `build/reports/tests/test/index.html`

---

### **Melhorias Futuras**
1. **Cache de Posts**:
    - Integração com Redis para armazenar dados de posts populares e threads.

2. **Suporte a Mídia**:
    - Migração do armazenamento de mídia para um serviço externo (e.g., AWS S3).

3. **Monitoramento e Logs**:
    - Integração com Prometheus, Grafana e ELK Stack para monitoramento e observabilidade.

---

### **Licença**
Este projeto é livre para uso sob a licença [MIT](LICENSE).

---