# pasta Wiki source

This directory is the source for pasta's multilingual documentation.

## Languages

- [English](en/Home.md)
- [日本語](ja/Home.md)

Each language keeps the same core page structure so links and maintenance stay predictable.

## Pages

| Topic | English | 日本語 |
| --- | --- | --- |
| Home | [Home](en/Home.md) | [ホーム](ja/Home.md) |
| Getting started | [Getting Started](en/Getting-Started.md) | [はじめに](ja/Getting-Started.md) |
| Architecture | [Architecture](en/Architecture.md) | [アーキテクチャ](ja/Architecture.md) |

## GitHub Wiki publishing

The repository's GitHub Wiki is currently disabled. These files are intentionally kept in the main repository so documentation changes can be reviewed through pull requests and versioned with the code.

If the GitHub Wiki is enabled later, use these pages as the canonical source when mirroring content into the wiki repository.

## Translation policy

- English is the reference language for technical identifiers, commands, file names, and API names.
- Translations should preserve command examples exactly unless the command itself changes.
- Safety warnings must be carried across languages without weakening their meaning.
- When a behavior changes, update every affected language in the same pull request when practical.
