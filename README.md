# Pain Client

Клиент для Minecraft **1.21.4** на Fabric. Версия 1.2.

## Что внутри

- **Меню** — стили Cool, Liquid Glass и Classic, 3D-меню на экране в мире (`Menu3D`).
- **HUD** — стили Cool, Liquid Glass, Modern, Classic; ватермарка Dynamic Island.
- **MusicLyrics** — текст играющей песни: синхронный текст из [LRCLIB](https://lrclib.net) или распознавание со звука (faster-whisper / Vosk), перевод, 3D-текст в небе, стиль Island.
- **Pet** — питомцы Claw'd и Mauser: бегают за игроком, спят, едят, летают на джетпаке; чисто визуальные.
- **Claw'd** — превращает игрока в Claw'd (только на своём экране).
- **SkyShader**, **Weather**, **CustomFog**, **Browser3D**, **TargetESP** с 3D-моделями.
- Боевые, двигательные и прочие модули — см. категории в меню.

## Сборка

Нужен JDK 21.

```
./gradlew build
```

Готовый мод: `build/libs/Pain.v<версия>.jar`. Положи его в папку `mods` вместе с Fabric API.

## Необязательные зависимости (Linux)

- MusicLyrics: `python3`, `parec` (PipeWire/PulseAudio), `playerctl`; библиотеки распознавания мод ставит сам в `~/.config/pain/lyrics/venv`.
- Browser3D: композитор niri, PyGObject, GStreamer с `pipewiresrc`.
- Свои 3D-модели в TargetESP (FBX/OBJ/GLB): установленный Blender.

Скрипты сборки моделей лежат в `tools/`.

## Лицензия

[GPL-3.0](LICENSE). Форки и изменённые версии должны распространяться под той же лицензией с открытыми исходниками.

Сторонний код и материалы перечислены в [NOTICE.md](NOTICE.md).
