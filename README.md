# Домашняя касса

Личный трекер финансов для Android. Данные хранятся только на телефоне.

- Правила и спецификация: [CLAUDE.md](CLAUDE.md)
- Стиль v3 «мех-HUD»: [docs/STYLE.md](docs/STYLE.md), живой эталон — [prototype/style.html](prototype/style.html)
- Журнал решений: [docs/DECISIONS.md](docs/DECISIONS.md)
- Статус и бэклог: [docs/ROADMAP.md](docs/ROADMAP.md)
- Эталон логики и дизайна (веб-версия, этап A): [kassa.html](kassa.html)

## Структура

- `core/` — чистый Kotlin: модель данных, значения по умолчанию, расчёты (без Android и базы).
- `app/` — Android: база Room, тема, экраны Compose. Схема базы — `app/schemas/`.
- `prototype/` — локальный HTML-прототип стиля: открыть в браузере, без сети (шрифты берёт из `app/src/main/res/font/`).
- `fonts/` — лицензии шрифтов (OFL). Сами шрифты — `app/src/main/res/font/`.

## Сборка

Нужны JDK 17+ и Android SDK (platform 36, build-tools 36). Gradle скачивается сам через `./gradlew`.

```sh
echo "sdk.dir=/путь/к/android-sdk" > local.properties   # не попадает в репозиторий
./gradlew :core:test :app:assembleDebug
```

Готовый файл: `app/build/outputs/apk/debug/app-debug.apk`.

### Релизная сборка (ей пользуемся на телефоне)

Подписывается постоянным ключом, чтобы новые версии ставились поверх старой без потери данных.
Ключ и пароли лежат вне репозитория (в облачной среде — общая папка проекта `signing/`):

```sh
cp /mnt/project-files/signing/keystore.properties .   # не попадает в репозиторий
./gradlew :app:assembleRelease
```

Готовый файл: `app/build/outputs/apk/release/app-release.apk`. Без `keystore.properties` APK получится неподписанным и не установится.

### Скриншоты экранов без эмулятора

```sh
./gradlew :app:testDebugUnitTest --tests dev.bessaract.kassa.ui.ScreenshotTest
```

PNG — в `app/build/screenshots/`.
Debug-сборка ставится рядом с будущей релизной (id `dev.bessaract.kassa.debug`) и подписана временным отладочным ключом.

## Установка на телефон

1. Скачать APK на телефон.
2. Разрешить установку из неизвестных источников для приложения, через которое открываешь файл.
3. Открыть APK и установить.

## Что не попадает в репозиторий

Финансовые данные, экспорт JSON, ключи подписи APK, `local.properties`. См. `.gitignore`.
