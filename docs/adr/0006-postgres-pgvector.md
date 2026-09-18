# Postgres with pgvector as the single store

Relational Catalog data and embeddings live in one Postgres instance with pgvector. Alternatives (separate vector DB, SQLite-only) either add another moving part for Docker demos or weaken multi-Catalog durability. One database keeps Compose and Spring AI wiring small for the hackathon; swapping the vector adapter later is possible behind the catalog module’s seam.
