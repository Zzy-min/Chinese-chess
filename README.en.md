# XiangqiGame

A Java-based Xiangqi project that provides both a desktop edition (Swing) and a browser edition (local web).

## Documentation

- Chinese documentation: [`README.zh-CN.md`](./README.zh-CN.md)
- Repository landing page: [`README.md`](./README.md)

## Quick Notes

- The browser UI has been refreshed with a more board-centered layout, theme switching, and clearer onboarding.
- The project supports PvP, PvE, endgame practice, move review, and external engine integration.
- Use the language links above if you want the Chinese version of the documentation.

## Overview

This project aims to deliver a complete Xiangqi experience across local desktop and browser environments, while keeping room for deployment and engine-related extensions.

Current highlights include:

- Desktop gameplay interface
- Local browser-based web interface
- Human vs human and human vs AI modes
- Endgame practice and move review
- External engine integration

## Browser Edition

The browser edition focuses on the following improvements:

- A more board-centered page structure
- Clearer separation of controls and status areas
- Theme switching
- Easier onboarding for new players

If you want the newer interface first, the browser edition is the recommended entry point.

## Features

### Core Play

- Standard Xiangqi rules
- Local two-player gameplay
- Common gameplay actions

### AI and Extensions

- Human vs AI mode
- External engine integration
- Review and extended analysis capabilities

### Practice Modes

- Endgame practice
- Match review

## Running Locally

### Desktop Edition

Install Java 11 or later, then run the desktop entry point according to the Java launch setup in the repository.

### Browser Edition

If the repository provides a local web entry, use the project scripts or instructions to start it. If it is exposed as a static page, you can also serve it locally with a static server.

## Deploying to Render

The repository already includes `render.yaml` and `Dockerfile` for Render deployment.

- The Blueprint uses `runtime: docker`
- The container entry class is `com.xiangqi.web.PublicWebMain`
- After deployment, use the public URL assigned by Render directly

## Replacing Sound Effects

Default sound files:

- `src/main/resources/audio/move.wav`
- `src/main/resources/audio/mate.wav`

Recommended format:

- `WAV / PCM / 44.1kHz / 16-bit / mono`

Recommended license/source record:

- `docs/audio-license.md`

## System Requirements

- Java 11+
- Windows, since the current scripts and examples are Windows-oriented

## Documentation Strategy

- `README.md` stays as a landing page only
- `README.zh-CN.md` contains Chinese content only
- `README.en.md` contains English content only

This keeps language boundaries clear and easier to maintain.

## Repository

- GitHub: `https://github.com/Zzy-min/turbo-octo-lamp`

## License

For learning and communication purposes only.
