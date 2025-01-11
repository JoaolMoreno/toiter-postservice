-- Este script cria o schema 'pst' e as tabelas 'posts' no banco de dados.
-- O schema 'pst' é criado para separar as tabelas do banco de dados de outras tabelas que podem existir no banco de dados.
-- Esse script deve ser executado ao configurar o banco de dados pela primeira vez.
-- Criar o schema 'pst'

-- Criar o usuário 'usr' com uma senha
CREATE USER pst WITH PASSWORD 'password';

-- Criar o schema 'pst'
CREATE SCHEMA pst;

-- Alterar a propriedade do schema para o usuário 'pst'
ALTER SCHEMA pst OWNER TO pst;

CREATE TABLE pst.posts (
                           id BIGSERIAL PRIMARY KEY,
                           parent_post_id BIGINT,
                           repost_parent_post_id BIGINT,
                           user_id BIGINT,
                           content TEXT NOT NULL,
                           media_url TEXT,
                           created_at TIMESTAMP DEFAULT NOW(),
                           deleted_at TIMESTAMP,
                           deleted BOOLEAN DEFAULT FALSE
);

-- Adicionar constraint para parent_post_id referenciando pst.posts(id)
ALTER TABLE pst.posts
    ADD CONSTRAINT fk_parent_post
        FOREIGN KEY (parent_post_id)
            REFERENCES pst.posts(id);

-- Adicionar constraint para repost_parent_post_id referenciando pst.posts(id)
ALTER TABLE pst.posts
    ADD CONSTRAINT fk_repost_parent_post
        FOREIGN KEY (repost_parent_post_id)
            REFERENCES pst.posts(id);

-- Adicionar constraint para user_id referenciando usr.user(id)
ALTER TABLE pst.posts
    ADD CONSTRAINT fk_user_id
        FOREIGN KEY (user_id)
            REFERENCES usr.users(id);

-- Índices
CREATE INDEX idx_posts_user_id ON pst.posts (user_id);
CREATE INDEX idx_posts_parent_post_id ON pst.posts (parent_post_id);
CREATE INDEX idx_posts_parent_post_id ON pst.posts (parent_post_id);
CREATE INDEX idx_posts_id ON pst.posts (id);
CREATE INDEX idx_posts_user_id ON pst.posts (user_id);
CREATE INDEX idx_posts_parent_post_id_created_at ON pst.posts (parent_post_id, created_at);
CREATE INDEX idx_posts_user_id ON pst.posts (user_id);
CREATE INDEX idx_posts_deleted ON pst.posts (deleted);

-- Tabela 'like'
CREATE TABLE pst.like (
                          id BIGSERIAL PRIMARY KEY,       -- Identificador único
                          user_id BIGINT NOT NULL,        -- ID do usuário que curtiu
                          post_id BIGINT NOT NULL,        -- ID da postagem curtida
                          created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- Data e hora da curtida
                          unique (user_id, post_id)
);

-- Index para melhorar a performance em consultas
CREATE INDEX idx_like_user_post ON pst.like (user_id, post_id);


-- Adicionar constraint para user_id referenciando usr.user(id)
ALTER TABLE pst.like
    ADD CONSTRAINT fk_like_user_id
        FOREIGN KEY (user_id)
            REFERENCES usr.users(id);

-- Adicionar constraint para post_id referenciando pst.posts(id)
ALTER TABLE pst.like
    ADD CONSTRAINT fk_like_post_id
        FOREIGN KEY (post_id)
            REFERENCES pst.posts(id)
                ON DELETE CASCADE;


-- Tabela 'view'
CREATE TABLE pst.view (
                          id BIGSERIAL PRIMARY KEY,       -- Identificador único
                          user_id BIGINT NOT NULL,        -- ID do usuário que visualizou
                          post_id BIGINT NOT NULL,        -- ID da postagem visualizada
                          created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- Data e hora da visualização
                          unique (user_id, post_id)
);
-- Index para melhorar a performance em consultas
CREATE INDEX idx_view_user_post ON pst.view (user_id, post_id);

-- Adicionar constraint para user_id referenciando usr.user(id)
ALTER TABLE pst.view
    ADD CONSTRAINT fk_view_user_id
        FOREIGN KEY (user_id)
            REFERENCES usr.users(id);

-- Adicionar constraint para post_id referenciando pst.posts(id)
ALTER TABLE pst.view
    ADD CONSTRAINT fk_view_post_id
        FOREIGN KEY (post_id)
            REFERENCES pst.posts(id)
                ON DELETE CASCADE;


-- Alterar a propriedade do schema para o usuário 'pst'
ALTER SCHEMA pst OWNER TO pst;


DO $$
    BEGIN
        EXECUTE (
            SELECT string_agg('ALTER TABLE ' || table_schema || '.' || table_name || ' OWNER TO pst;', ' ')
            FROM information_schema.tables
            WHERE table_schema = 'pst'
        );
    END $$;