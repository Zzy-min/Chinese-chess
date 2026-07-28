# Production board engines

The production image contains pinned official engine releases:

- Pikafish `Pikafish-2026-01-02`, Linux AVX2 build:
  https://github.com/official-pikafish/Pikafish/releases/tag/Pikafish-2026-01-02
- Rapfi `250615`, Linux Clang AVX2 build:
  https://github.com/dhbloo/rapfi/releases/tag/250615

Both engines are licensed under GPL-3.0. Their license, author information and
required evaluation files are copied into `/opt/engines/<engine>` in the
runtime image. The Docker build verifies every downloaded release asset by its
published SHA-256 digest.

The Java adapters use UCI for Pikafish and Piskvork for Rapfi. If an external
engine cannot start or return a legal move, the application falls back to its
built-in engine.
