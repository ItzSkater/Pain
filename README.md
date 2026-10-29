<div align="center">

# 🩸 Pain Client

**A visual-heavy Fabric client for Minecraft 1.21.4**
**Визуальный клиент для Minecraft 1.21.4 на Fabric**

<br>

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4-62B47A?style=for-the-badge&logo=minecraft&logoColor=white)
![Fabric](https://img.shields.io/badge/Fabric-mod-DBD0B4?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Version](https://img.shields.io/badge/version-1.2.2-8A2BE2?style=for-the-badge)
![License](https://img.shields.io/badge/license-GPL--3.0-blue?style=for-the-badge)

<br>

**[🇬🇧 English](#english)** &nbsp;•&nbsp; **[🇷🇺 Русский](#русский)**

</div>

<!--
Add a banner or screenshots here, for example:
<p align="center"><img src="assets/banner.png" width="800"></p>
-->

---

## English

### ✨ Features

| | Category | Modules |
|---|---|---|
| 🖥️ | **Interface** | **Menu** in Cool, Liquid Glass and Classic styles, plus a 3D menu on an in-world screen (`Menu3D`). **HUD** in Cool, Liquid Glass, Modern and Classic styles with a Dynamic Island watermark. **Minimap**. |
| 🌌 | **World & visuals** | **SkyShader** with the "Atmosphere" sky (volumetric clouds, sun, moon, stars, lightning). **Weather**: custom rain, snow and thunderstorms, visible only on your screen (streaks with splashes, drops on the glass, shine on wet surfaces). **CustomFog**, **Browser3D**. |
| 🎨 | **Shaders** | **Pain Shaders**: a custom shader pack and the `Shaders` module. Iris and Sodium are bundled into the mod, so you don't need to install them separately. |
| 🎯 | **Combat visuals** | **TargetESP** with 3D models, **HitFlash**, **HitMarker**, **BlockHighlight**, **Vignette**. |
| 😎 | **Cosmetics** | **ChinaHat**, **Halo**. |
| 🎵 | **Music & sound** | **MusicLyrics**: lyrics for the song that's playing. Synced lyrics from [LRCLIB](https://lrclib.net) or recognition from the audio (faster-whisper / Vosk), translation, 3D text in the sky, Island style. **HitSound** and **ShootSounds** with custom sounds. |
| 🐾 | **Fun** | **Pet**: Claw'd and Mauser pets that run after you, sleep, eat and fly with a jetpack (purely visual). **Claw'd**: turns the player into Claw'd (only on your own screen). |
| ⚔️ | **Combat & movement** | **AttributeSwap**, **RageBot**, **Scaffold**, **ElytraTarget** and more. See the categories in the in-game menu. |

### 🚀 Quick start

> Requires **JDK 21**.

```bash
./gradlew build
```

The finished mod will be at `build/libs/Pain.v<version>.jar`.
Put it in your `mods` folder together with **Fabric API**.

### 🧩 Optional dependencies (Linux)

| Feature | What you need |
|---|---|
| **MusicLyrics** | `python3`, `parec` (PipeWire/PulseAudio), `playerctl`. The recognition libraries are installed by the mod itself into `~/.config/pain/lyrics/venv`. |
| **Browser3D** | the niri compositor, PyGObject, GStreamer with `pipewiresrc` |
| **Custom 3D models in TargetESP** (FBX / OBJ / GLB) | Blender installed |

Model build scripts are in `tools/`.

### 📁 What else is in the repository

| Path | What it is |
|---|---|
| `shaderpack/pain-shaders/` | Sources of the Pain Shaders pack (built with the `release.py` script) |
| `site/` | The website and the accounts server |
| `loader/`, `launcher/` | The loader and launcher, written in Rust |
| `tools/` | A bridge for AI agents (`mcp-server`), a test copy of the game (`testgame`), and the ad video build (`ad`) |

### 📜 License

Released under **[GPL-3.0](LICENSE)**. Forks and modified versions must be distributed under the same license with open source code.

Third-party code and materials are listed in **[NOTICE.md](NOTICE.md)**.

<div align="right"><a href="#-pain-client">⬆ back to top</a></div>

---

## Русский

### ✨ Возможности

| | Категория | Модули |
|---|---|---|
| 🖥️ | **Интерфейс** | **Меню** в стилях Cool, Liquid Glass и Classic, плюс 3D-меню на экране в мире (`Menu3D`). **HUD** в стилях Cool, Liquid Glass, Modern и Classic с ватермаркой Dynamic Island. **Minimap**. |
| 🌌 | **Мир и визуал** | **SkyShader** с небом «Атмосфера» (объёмные облака, солнце, луна, звёзды, молнии). **Weather**: свой дождь, снег и гроза только на твоём экране (струи со всплесками, капли на стекле, блеск мокрых поверхностей). **CustomFog**, **Browser3D**. |
| 🎨 | **Шейдеры** | **Pain Shaders**: свой шейдерпак и модуль `Shaders`. Iris и Sodium встроены в мод, ставить отдельно не нужно. |
| 🎯 | **Боевой визуал** | **TargetESP** с 3D-моделями, **HitFlash**, **HitMarker**, **BlockHighlight**, **Vignette**. |
| 😎 | **Косметика** | **ChinaHat**, **Halo**. |
| 🎵 | **Музыка и звук** | **MusicLyrics**: текст играющей песни. Синхронный текст из [LRCLIB](https://lrclib.net) или распознавание со звука (faster-whisper / Vosk), перевод, 3D-текст в небе, стиль Island. **HitSound** и **ShootSounds** со своими звуками. |
| 🐾 | **Развлечения** | **Pet**: питомцы Claw'd и Mauser бегают за тобой, спят, едят и летают на джетпаке (чисто визуальные). **Claw'd**: превращает игрока в Claw'd (только на своём экране). |
| ⚔️ | **Бой и движение** | **AttributeSwap**, **RageBot**, **Scaffold**, **ElytraTarget** и другие. Смотри категории в меню игры. |

### 🚀 Быстрый старт

> Нужен **JDK 21**.

```bash
./gradlew build
```

Готовый мод будет лежать в `build/libs/Pain.v<версия>.jar`.
Положи его в папку `mods` вместе с **Fabric API**.

### 🧩 Необязательные зависимости (Linux)

| Функция | Что нужно |
|---|---|
| **MusicLyrics** | `python3`, `parec` (PipeWire/PulseAudio), `playerctl`. Библиотеки распознавания мод ставит сам в `~/.config/pain/lyrics/venv`. |
| **Browser3D** | композитор niri, PyGObject, GStreamer с `pipewiresrc` |
| **Свои 3D-модели в TargetESP** (FBX / OBJ / GLB) | установленный Blender |

Скрипты сборки моделей лежат в `tools/`.

### 📁 Что ещё лежит в репозитории

| Путь | Что это |
|---|---|
| `shaderpack/pain-shaders/` | Исходники шейдерпака Pain Shaders (собирается скриптом `release.py`) |
| `site/` | Сайт и сервер аккаунтов |
| `loader/`, `launcher/` | Загрузчик и лаунчер на Rust |
| `tools/` | Мост для ИИ-агентов (`mcp-server`), тестовая копия игры (`testgame`), сборка рекламных роликов (`ad`) |

### 📜 Лицензия

Распространяется под **[GPL-3.0](LICENSE)**. Форки и изменённые версии должны распространяться под той же лицензией с открытыми исходниками.

Сторонний код и материалы перечислены в **[NOTICE.md](NOTICE.md)**.

<div align="right"><a href="#-pain-client">⬆ наверх</a></div>
