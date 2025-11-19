## **Toiter - Post Service**

O **Post Service** é um dos microsserviços do ecossistema **Toiter**, responsável pelo gerenciamento de postagens, respostas e repostagens. Ele fornece funcionalidades para criar, editar, listar e excluir posts, além de gerenciar interações como curtidas e repostagens. Este serviço também emite eventos para sincronização com outros serviços, como o **Feed Service** e o **Notification Service**.

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
- Exemplo: `/internal/posts/count`

##### **Fluxo de Autenticação**
1. **Para rotas públicas não-autenticadas**: O filtro permite acesso direto (ex: `/swagger-ui`, `/posts/thread/{id}`)
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

#### **1. Postagens**
| Método   | Endpoint              | Descrição                                 |
|----------|-----------------------|-------------------------------------------|
| `POST`   | `/posts`              | Cria uma nova postagem.                   |
| `GET`    | `/posts/{id}`         | Retorna os detalhes de uma postagem.      |
| `GET`    | `/posts/user/{userId}`| Lista as postagens de um usuário.         |
| `DELETE` | `/posts/{id}`         | Exclui uma postagem.                      |

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

1. **Clone o repositório:**
   ```bash
   git clone https://github.com/JoaolMoreno/toiter-postservice.git
   cd toiter-post-service
   ```

2. **Configure o arquivo `application.properties`:**
    ```properties
    server.port=9991

    # Banco de dados
    spring.datasource.url=jdbc:postgresql://localhost:5432/toiter
    spring.datasource.username=postgres
    spring.datasource.password=postgres
    spring.jpa.hibernate.ddl-auto=update

    # Mensageria Kafka
    spring.kafka.bootstrap-servers=localhost:9092

    # Token compartilhado para endpoints internos
    service.shared-key=shared-secret-key

    # JWT
    jwt.secret-key=jwt-secret-key
    ```

3. **Suba os serviços necessários:**
    - PostgreSQL, Kafka e o serviço atual.
    - Use o Docker Compose:
      ```bash
      docker-compose up
      ```

4. **Execute o microsserviço:**
   ```bash
   ./mvnw spring-boot:run
   ```

5. **Acesse a API:**
    - Teste os endpoints usando `curl`, Postman ou outra ferramenta.

6. **Testando Autenticação:**
    
    **Com Cookie HttpOnly (simulando browser):**
    ```bash
    # O cookie é normalmente definido pelo serviço de autenticação
    # Para testar manualmente, você pode fazer:
    curl -X GET http://localhost:9991/posts?page=0&size=10 \
      -H "Cookie: accessToken=seu-jwt-token-aqui"
    ```
    
    **Com Header Authorization (cliente não-browser):**
    ```bash
    curl -X GET http://localhost:9991/posts?page=0&size=10 \
      -H "Authorization: Bearer seu-jwt-token-aqui"
    ```
    
    **Endpoint Interno (serviço-a-serviço):**
    ```bash
    curl -X GET http://localhost:9991/internal/posts/count?userId=123 \
      -H "Authorization: Bearer shared-secret-key"
    ```

---

### **Estrutura do Projeto**

```
toiter-post-service/
├── src/
│   ├── main/
│   │   ├── java/com/toiter/
│   │   │   ├── postservice/
│   │   │   │   ├── controller/       # Controladores REST
│   │   │   │   ├── service/          # Lógica de negócio
│   │   │   │   ├── repository/       # Acesso ao banco de dados
│   │   │   │   ├── model/            # DTOs e eventos Kafka
│   │   │   │   ├── entity/           # Entidades JPA
│   │   │   │   ├── config/           # Configurações do Spring e Kafka
│   │   │   │   ├── producer/         # Emissão de eventos Kafka
│   │   │   │   ├── consumer/         # Consumo de eventos Kafka
│   │   │   │   └── exception/        # Tratamento de exceções globais
│   │   └── resources/
│   │       ├── application.properties    # Configurações da aplicação
└── docker-compose.yml                # Configuração para subir serviços
```

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