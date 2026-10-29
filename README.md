English

A client for Minecraft 1.21.4 on Fabric. Version 1.2.2.

What's inside
Menu: Cool, Liquid Glass and Classic styles, plus a 3D menu on an in-world screen (Menu3D).
HUD: Cool, Liquid Glass, Modern and Classic styles; a Dynamic Island watermark.
MusicLyrics: lyrics for the song that's playing. Synced lyrics from LRCLIB or recognition from the audio (faster-whisper / Vosk), translation, 3D text in the sky, Island style.
Pet: Claw'd and Mauser pets that run after the player, sleep, eat and fly with a jetpack. Purely visual.
Claw'd: turns the player into Claw'd (only on your own screen).
SkyShader, Weather, CustomFog, Browser3D, TargetESP with 3D models.
Pain Shaders: a custom shader pack and the Shaders module. Iris and Sodium are bundled into the mod, so you don't need to install them separately.
Weather: custom rain, snow and thunderstorms, visible only on your screen: streaks with splashes, drops on the glass, shine on wet surfaces.
"Atmosphere" sky in SkyShader: volumetric clouds, sun, moon, stars, lightning.
HitFlash, ChinaHat, Halo, BlockHighlight, HitMarker, Vignette, Minimap.
HitSound and ShootSounds: custom hit and shot sounds.
AttributeSwap, RageBot, Scaffold, ElytraTarget and other combat and movement modules; see the categories in the menu.
Building

Requires JDK 21.

./gradlew build

The finished mod: build/libs/Pain.v<version>.jar. Put it in the mods folder together with Fabric API.

Optional dependencies (Linux)
MusicLyrics: python3, parec (PipeWire/PulseAudio), playerctl; the recognition libraries are installed by the mod itself into ~/.config/pain/lyrics/venv.
Browser3D: the niri compositor, PyGObject, GStreamer with pipewiresrc.
Custom 3D models in TargetESP (FBX/OBJ/GLB): Blender installed.

Model build scripts are in tools/.

What else is in the repository
shaderpack/pain-shaders/: sources of the Pain Shaders pack (built with the release.py script).
site/: the website and the accounts server. loader/ and launcher/: the loader and launcher, written in Rust.
tools/: a bridge for AI agents (mcp-server), a test copy of the game (testgame), and the ad video build (ad).
License

GPL-3.0. Forks and modified versions must be distributed under the same license with open source code.

Third-party code and materials are listed in NOTICE.md.

Русский

English | Русский

Клиент для Minecraft 1.21.4 на Fabric. Версия 1.2.2.

Что внутри
Меню: стили Cool, Liquid Glass и Classic, 3D-меню на экране в мире (Menu3D).
HUD: стили Cool, Liquid Glass, Modern, Classic; ватермарка Dynamic Island.
MusicLyrics: текст играющей песни. Синхронный текст из LRCLIB или распознавание со звука (faster-whisper / Vosk), перевод, 3D-текст в небе, стиль Island.
Pet: питомцы Claw'd и Mauser бегают за игроком, спят, едят, летают на джетпаке; чисто визуальные.
Claw'd: превращает игрока в Claw'd (только на своём экране).
SkyShader, Weather, CustomFog, Browser3D, TargetESP с 3D-моделями.
Pain Shaders: свой шейдерпак и модуль Shaders. Iris и Sodium встроены в мод, ставить отдельно не нужно.
Weather: свой дождь, снег и гроза только на твоём экране: струи со всплесками, капли на стекле, блеск мокрых поверхностей.
Небо «Атмосфера» в SkyShader: объёмные облака, солнце, луна, звёзды, молнии.
HitFlash, ChinaHat, Halo, BlockHighlight, HitMarker, Vignette, Minimap.
HitSound и ShootSounds: свои звуки попадания и выстрела.
AttributeSwap, RageBot, Scaffold, ElytraTarget и остальные боевые и двигательные модули; см. категории в меню.
Сборка

Нужен JDK 21.

./gradlew build

Готовый мод: build/libs/Pain.v<версия>.jar. Положи его в папку mods вместе с Fabric API.

Необязательные зависимости (Linux)
MusicLyrics: python3, parec (PipeWire/PulseAudio), playerctl; библиотеки распознавания мод ставит сам в ~/.config/pain/lyrics/venv.
Browser3D: композитор niri, PyGObject, GStreamer с pipewiresrc.
Свои 3D-модели в TargetESP (FBX/OBJ/GLB): установленный Blender.

Скрипты сборки моделей лежат в tools/.

Что ещё лежит в репозитории
shaderpack/pain-shaders/: исходники шейдерпака Pain Shaders (собирается скриптом release.py).
site/: сайт и сервер аккаунтов. loader/ и launcher/: загрузчик и лаунчер на Rust.
tools/: мост для ИИ-агентов (mcp-server), тестовая копия игры (testgame), сборка рекламных роликов (ad).
Лицензия

GPL-3.0. Форки и изменённые версии должны распространяться под той же лицензией с открытыми исходниками.

Сторонний код и материалы перечислены в NOTICE.md.

Readme
MD
Uploads
1793255225430_изображение.png, 1793255193774_изображение.png
+3
Memory
Updated · Pain Client
