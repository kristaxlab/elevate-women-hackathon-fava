# Modular monolith, not microservices

Ingest, catalog, classify, search, and telegram I/O are separate Gradle modules with deep interfaces, but they ship as **one** Spring Boot deployable. That keeps hackathon ops (one Docker Compose stack) simple while preserving seams for later extraction. Splitting into networked services now would add distributed failure modes without a scaling need.
