# Сторонний код и материалы

Лицензия GPL-3.0 из файла `LICENSE` относится к коду Pain Client. Ниже — то, что написано или создано не автором проекта.

## Код

- **ThunderHack Recode** (GPL-3.0) — часть боевых модулей пришла через RaceClient, старый форк ThunderHack того же автора.
- **EventAPI** от DarkMagician6 (`com.darkmagician6.eventapi`) — система событий.
- **media-player-info** (`dev.redstones.mediaplayerinfo`) — подключается как зависимость.
- Fabric Loader, Fabric API, Yarn — инструменты и библиотеки Fabric.
- **Iris** (LGPL-3.0) и **Sodium** (Polyform Shield 1.0.0) — готовые сборки лежат в `src/main/resources/META-INF/jars/` и вкладываются в мод без изменений.
- **ModernFix** (LGPL-3.0), **FerriteCore** (MIT), **ImmediatelyFast** (LGPL-3.0), **More Culling** (GPL-3.0), **Sodium Extra** (LGPL-3.0) и **Cloth Config** (LGPL-3.0, нужен More Culling) — готовые сборки с Modrinth, лежат там же и вкладываются без изменений.
- **Entity Culling** не вложен: его лицензия (tr7zw Protective License) не разрешает распространять мод в составе другого. Его ставят отдельным файлом в `mods`.
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
- **Звук «Nonagon shoot»** `assets/pain/sounds/nonagon_shoot.ogg` — прислан автором проекта, источник и лицензия не установлены. **TODO: найти автора или заменить.**
- **Картинка «Котозаяц»** `assets/pain/icons/kotozayac.png` и **обои** `assets/pain/textures/wallpaper/` — взяты из сети, автор не установлен. **TODO: найти автора или заменить.**
- **Шрифты и иконки** в `assets/pain/fonts/` — под лицензиями их авторов. **TODO: перечислить.**

## Сервисы, к которым обращается клиент

- lrclib.net — синхронные тексты песен;
- translate.googleapis.com — перевод строк;
- alphacephei.com, huggingface.co — загрузка моделей распознавания речи.

## Обои главного меню

Фотографии с Wikimedia Commons, уменьшены и обрезаны до 1920×1080 (`src/main/resources/assets/pain/textures/wallpaper/`). Лицензия у каждой своя:

- **Калифорния** (`california.jpg`) — Taavi Niittee/Tõrva Astronoomiaklubi/Tõrva Ast, CC BY 4.0, https://commons.wikimedia.org/wiki/File:California_nebula_from_Estonia.jpg
- **Туманность Киля** (`carina.jpg`) — ESO/IDA/Danish 1.5 m/R.Gendler, J-E. Ovaldsen, C. Thöne, and C. Feron., CC BY 4.0, https://commons.wikimedia.org/wiki/File:ESO_-_The_Carina_Nebula_(by).jpg
- **Тарантул** (`tarantula.jpg`) — NASA, ESA, CSA, STScI, Webb ERO Production Team, Public domain, https://commons.wikimedia.org/wiki/File:Tarantula_Nebula_by_JWST.jpg
- **Трифид и Лагуна** (`trifid.jpg`) — NSF–DOE Vera C. Rubin Observatory, CC BY 4.0, https://commons.wikimedia.org/wiki/File:Trifid_and_Lagoon_nebulae.jpg
- **Лагуна** (`lagoon.jpg`) — ESO/VVV, CC BY 4.0, https://commons.wikimedia.org/wiki/File:VISTA%27s_infrared_view_of_the_Lagoon_Nebula_(Messier_8).jpg
- **Звёздный след** (`star_trails.jpg`) — Antoine Lamielle, CC BY-SA 4.0, https://commons.wikimedia.org/wiki/File:2017-04_Circumpolar_trails_sunset_at_La_Hague_lighthouse.jpg
- **Чжанцзяцзе** (`zhangjiajie.jpg`) — chensiyuan, CC BY-SA 4.0, https://commons.wikimedia.org/wiki/File:1_tianzishan_wulingyuan_zhangjiajie_2012.jpg
- **Над облаками** (`above_clouds.jpg`) — Dmytro Balkhovitin, CC BY-SA 4.0, https://commons.wikimedia.org/wiki/File:Crimea,_Ai-Petri,_low_clouds.jpg
- **Глен-Коу** (`glen_coe.jpg`) — Colin, CC BY-SA 4.0, https://commons.wikimedia.org/wiki/File:Glen_Coe_Rainbow.jpg
- **Уюни** (`uyuni.jpg`) — Diego Delso, CC BY-SA 4.0, https://commons.wikimedia.org/wiki/File:Salar_de_Uyuni,_Bolivia,_2016-02-04,_DD_10-12_HDR.JPG
- **Остров Скай** (`skye.jpg`) — Colin, CC BY-SA 4.0, https://commons.wikimedia.org/wiki/File:South_over_the_Quiraing,_Isle_of_Skye_-_2.jpg
- **Лейк-Луиз** (`lake_louise.jpg`) — Chensiyuan, CC BY-SA 4.0, https://commons.wikimedia.org/wiki/File:1_lake_louise_pano_2019.jpg
- **Глен-Этив** (`etive.jpg`) — Colin, CC BY-SA 4.0, https://commons.wikimedia.org/wiki/File:Lagangarbh_cottage_with_Buachaille_Etive_M%C3%B2r.jpg
- **Дедвлей** (`deadvlei.jpg`) — Daniel Kraft, CC BY-SA 3.0, https://commons.wikimedia.org/wiki/File:Dead_Vlei_Tree_central.jpg
- **Тейде** (`teide.jpg`) — Thomas Wolf, www.foto-tw.de, CC BY-SA 3.0 de, https://commons.wikimedia.org/wiki/File:Roque_Cinchado_und_Teide.jpg
