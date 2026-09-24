CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE vector_store (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    content TEXT,
    metadata JSON,
    embedding VECTOR(768)
);

CREATE INDEX spring_ai_vector_index
    ON vector_store
    USING HNSW (embedding vector_cosine_ops);
