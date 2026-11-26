# Changelog

## Unreleased
- Unit tests for the shared helpers (`FileKinds`, `Formats`) in the `common` module.
- Login screens (desktop and Android) reject ports outside 1–65535.
- The server explains an invalid port argument instead of crashing.
- `ai.properties.example`: web search off by default (new free Gemini keys don't include it).
- README: where to start reading the code.
- `.gitattributes`: consistent line endings.
- Beginner-friendly comments in every class.

## v2 — ChatFX desktop + Android
- Multi-module project: `common`, `server`, `desktop`, `android`.
- Multithreaded server: one reader and one writer thread per client, `ConcurrentHashMap` registry.
- JavaFX desktop client in the Telegram style: color emoji, photos, files, audio, voice notes.
- Native Android client with the same features, a foreground service and notifications.
- AI assistant (Gemini) as an always-online contact, running on its own thread pool.
- 11 end-to-end server tests, including a concurrency test.

## v1
- First socket chat server (`ChatServer.java`).
