# Сторонний код и материалы

Лицензия GPL-3.0 из файла `LICENSE` относится к коду Pain Client. Ниже — то, что написано или создано не автором проекта.

## Код

- **ThunderHack Recode** (GPL-3.0) — часть боевых модулей пришла через RaceClient, старый форк ThunderHack того же автора.
- **EventAPI** от DarkMagician6 (`com.darkmagician6.eventapi`) — система событий.
- **media-player-info** (`dev.redstones.mediaplayerinfo`) — подключается как зависимость.
- Fabric Loader, Fabric API, Yarn — инструменты и библиотеки Fabric.
- **Iris** (LGPL-3.0) и **Sodium** (Polyform Shield 1.0.0) — готовые сборки лежат в `src/main/resources/META-INF/jars/` и вкладываются в мод без изменений.
- **Baritone** (LGPL-3.0), **Orbit**, **discord-ipc** — вложены в мод так же.
- **mcpfabric** — мост для ИИ-агентов, вложен в мод; `tools/mcp-server` — его сервер с дополнениями.
- **Pain Shaders** (`shaderpack/pain-shaders/`) — форк шейдера Mellow автора TheCMK (MIT). Лицензия оригинала лежит там же без изменений; название и оформление оригинала не используются по просьбе автора.
- Хеш без синуса в шейдере неба — Dave Hoskins, «Hash without Sine» (MIT).

## Материалы

- **Скайбоксы** `assets/pain/textures/sky/`:
  - `deep_space.jpg` — «Deep Space Skybox 16K with planets», автор указан в исходном архиве как JJ;
  - `inside_galaxy.jpg` — «Inside Galaxy Skybox HDRI 360 panorama».
  Оба скачаны автором проекта; условия использования — по лицензии на странице оригинала. **TODO: вписать ссылки на оригиналы и их лицензии.**
- **Claw'd** — талисман Claude Code, принадлежит Anthropic. Модели в `assets/pain/models/pet_*` — фанатская работа, к Anthropic отношения не имеют.
- **Звук грозы** `assets/pain/sounds/` — запись с freesound.org (mshahen, «rain_thunder_birds_cars»). **TODO: вписать лицензию со страницы записи.**
- **Картинка «Котозаяц»** `assets/pain/icons/kotozayac.png` и **обои** `assets/pain/textures/wallpaper/` — взяты из сети, автор не установлен. **TODO: найти автора или заменить.**
- **Шрифты и иконки** в `assets/pain/fonts/` — под лицензиями их авторов. **TODO: перечислить.**

## Сервисы, к которым обращается клиент

- lrclib.net — синхронные тексты песен;
- translate.googleapis.com — перевод строк;
- alphacephei.com, huggingface.co — загрузка моделей распознавания речи.
