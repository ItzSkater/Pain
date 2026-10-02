# Сторонний код и материалы

Лицензия GPL-3.0 из файла `LICENSE` относится к коду Pain Client. Ниже — то, что написано или создано не автором проекта.

## Код

- **ThunderHack Recode** (GPL-3.0) — часть боевых модулей пришла через RaceClient, старый форк ThunderHack того же автора.
- **EventAPI** от DarkMagician6 (`com.darkmagician6.eventapi`) — система событий.
- **media-player-info** (`dev.redstones.mediaplayerinfo`) — подключается как зависимость.
- Fabric Loader, Fabric API, Yarn — инструменты и библиотеки Fabric.

## Материалы

- **Скайбоксы** `assets/pain/textures/sky/`:
  - `deep_space.jpg` — «Deep Space Skybox 16K with planets», автор указан в исходном архиве как JJ;
  - `inside_galaxy.jpg` — «Inside Galaxy Skybox HDRI 360 panorama».
  Оба скачаны автором проекта; условия использования — по лицензии на странице оригинала. **TODO: вписать ссылки на оригиналы и их лицензии.**
- **Claw'd** — талисман Claude Code, принадлежит Anthropic. Модели в `assets/pain/models/pet_*` — фанатская работа, к Anthropic отношения не имеют.
- **Шрифты и иконки** в `assets/pain/fonts/` — под лицензиями их авторов. **TODO: перечислить.**

## Сервисы, к которым обращается клиент

- lrclib.net — синхронные тексты песен;
- translate.googleapis.com — перевод строк;
- alphacephei.com, huggingface.co — загрузка моделей распознавания речи.
