# ⚔️ Solo Leveling — AI-Powered Self-Development System

## 🧠 Концепция

**Solo Leveling** — это Android-приложение для саморазвития с интегрированным ИИ-ассистентом на базе Claude (Anthropic). Система превращает повседневные задачи в квесты, анализирует твоё состояние и адаптируется под тебя как живой друг и наставник.

---

## 🗂 Структура проекта

```
SoloLevelingApp/
├── app/src/main/
│   ├── java/com/sololeveling/app/
│   │   ├── ai/
│   │   │   └── AIEngine.kt              # Ядро ИИ, Anthropic API интеграция
│   │   ├── data/
│   │   │   ├── local/
│   │   │   │   ├── Converters.kt        # Room type converters
│   │   │   │   ├── Daos.kt              # Все DAO интерфейсы
│   │   │   │   └── SoloLevelingDatabase.kt
│   │   │   ├── model/
│   │   │   │   └── Models.kt            # Все data models
│   │   │   └── repository/
│   │   │       └── UserRepository.kt    # Центральный репозиторий
│   │   ├── service/
│   │   │   ├── AIBackgroundService.kt   # Фоновый ИИ-сервис
│   │   │   ├── OverlayService.kt        # Overlay поверх приложений
│   │   │   └── Receivers.kt             # Accessibility, Boot, Alarm receivers
│   │   ├── ui/
│   │   │   ├── main/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── MainViewModel.kt
│   │   │   │   └── HomeFragment.kt
│   │   │   ├── chat/
│   │   │   │   └── ChatActivity.kt      # Чат с ИИ + typewriter effect
│   │   │   ├── quest/
│   │   │   │   └── QuestFragment.kt     # Система квестов
│   │   │   ├── analytics/
│   │   │   │   └── AnalyticsFragment.kt # Графики и аналитика
│   │   │   ├── profile/
│   │   │   │   └── ProfileFragment.kt   # Профиль и настройки
│   │   │   └── onboarding/
│   │   │       └── OnboardingActivity.kt
│   │   ├── util/
│   │   │   ├── AlarmScheduler.kt        # AlarmManager управление
│   │   │   ├── Extensions.kt            # Kotlin extensions
│   │   │   ├── NotificationHelper.kt    # Умные уведомления
│   │   │   └── UsageStatsHelper.kt      # Usage Stats API
│   │   └── worker/
│   │       └── Workers.kt               # WorkManager workers
│   └── res/
│       ├── layout/                      # Все XML layouts
│       ├── drawable/                    # Иконки и фоны
│       ├── values/                      # Colors, Strings, Styles
│       └── xml/                         # Accessibility config
└── AndroidManifest.xml
```

---

## 🚀 Быстрый старт

### 1. Клонировать и открыть в Android Studio

```bash
git clone <repo>
# Открой SoloLevelingApp/ в Android Studio Hedgehog или новее
```

### 2. Получить API ключ Anthropic

1. Зайди на [console.anthropic.com](https://console.anthropic.com)
2. Создай аккаунт и получи API ключ
3. В приложении: **Профиль → 🔑 Установить API ключ**

Или прямо в коде в `AIEngine.kt`:
```kotlin
private const val API_KEY = "sk-ant-YOUR_KEY_HERE"
```

### 3. Собрать и установить

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 4. Выдать разрешения

После установки приложение запросит:
- **Overlay** — для показа уведомлений поверх приложений
- **Уведомления** — push-уведомления от ИИ
- **Служба доступности** — для определения активного приложения
- **Usage Stats** — для анализа использования телефона

---

## 🧩 Ключевые компоненты

### 🤖 AIEngine.kt — Мозг системы
- Интеграция с Anthropic Claude API
- Адаптивные системные промпты под характер ИИ
- Генерация квестов на основе анализа состояния
- Анализ сна, продуктивности, активности
- Парсинг JSON-действий из ответов ИИ

### 🎯 Система квестов
| Тип | Описание |
|-----|----------|
| DAILY | Ежедневные, сбрасываются в полночь |
| RANDOM | Случайные задания от ИИ |
| BOSS | Раз в 7 дней, повышенная награда |
| SLEEP | Задания на режим сна |
| WEEKLY | Недельные цели |

### 🏆 Ранги
```
E → D → C → B → A → S → SS → SSS
```

### 🛡 Безопасность
| Механизм | Действие |
|----------|----------|
| Safe Mode | Отключает overlay и уведомления |
| Pause | Временная пауза всей системы |
| Emergency Exit | 5 сек удержание или 3 тапа в overlay |
| Protected Apps | Настройки, Google Play, само приложение никогда не блокируются |
| Quiet Hours | 22:00–08:00 — тишина |
| Battery < 15% | Автоматический тихий режим |

### 🌙 Система сна
1. Нажми **"🌙 Иду спать"** — фиксирует время засыпания
2. Нажми **"☀️ Проснулся"** — рассчитывает качество сна
3. ИИ анализирует и адаптирует сложность квестов

---

## ⚙️ Конфигурация

### Тихие часы (по умолчанию)
```
Начало: 22:00
Конец:  08:00
```
Изменить: **Профиль → ⚙️ Настройки → 🌙 Тихие часы**

### Максимум уведомлений
```kotlin
// NotificationHelper.kt
private const val MAX_NOTIFICATIONS_PER_DAY = 5
private const val MIN_INTERVAL_MS = 2 * 60 * 60 * 1000L // 2 часа
```

### Характеры ИИ
| Характер | Описание |
|----------|----------|
| 😊 Дружелюбный | Тёплый, поддерживающий, с эмодзи |
| 💪 Строгий | Требовательный тренер, прямой |
| ⚡ Сбалансированный | Адаптируется по ситуации |

---

## 🔧 Необходимые шрифты

Добавь шрифт **Orbitron** в `/res/font/`:

```
res/font/orbitron.ttf          # Regular
res/font/orbitron_bold.ttf     # Bold
```

Скачать: [Google Fonts — Orbitron](https://fonts.google.com/specimen/Orbitron)

Или замени в `styles.xml`:
```xml
<!-- Убери эту строку если нет шрифта -->
<item name="android:fontFamily">@font/orbitron</item>
```

---

## 📦 Зависимости

| Библиотека | Назначение |
|-----------|-----------|
| Room 2.6.1 | Локальная база данных |
| DataStore | Пользовательские настройки |
| WorkManager 2.9 | Фоновые задачи |
| Retrofit 2.9 | HTTP клиент для Anthropic API |
| Coroutines 1.7.3 | Асинхронность |
| MPAndroidChart | Графики сна и активности |
| Lottie | Анимации |
| Navigation Component | Навигация |

---

## 🎨 Дизайн-система

### Цвета
```
Background: #060B14  (глубокий тёмно-синий)
Card:       #0D1B2A  (тёмно-синий)
Neon Blue:  #00B4D8  (основной акцент)
Neon Cyan:  #00F5FF  (вторичный акцент)
Neon Green: #39FF14  (успех)
Neon Gold:  #FFD700  (достижения)
Neon Red:   #FF073A  (опасность)
```

### Шрифт
- **Orbitron** — для заголовков и UI (sci-fi/gaming стиль)

---

## 📋 Checklist после установки

- [ ] Выдать разрешение Overlay
- [ ] Выдать разрешение Уведомлений
- [ ] Включить Службу доступности
- [ ] Выдать разрешение Usage Stats
- [ ] Ввести API ключ в Профиле
- [ ] Пройти онбординг (имя + имя ИИ + характер)
- [ ] Опционально: добавить шрифт Orbitron

---

## 🔒 Приватность

- API ключ хранится **только локально** (DataStore)
- Никакие данные пользователя не передаются третьим лицам
- Usage Stats используется **только локально** для анализа
- Anthropic получает только текст чата (для генерации ответов)

---

## 📞 Техподдержка

Если ИИ не отвечает:
1. Проверь API ключ в Профиле
2. Проверь интернет-соединение
3. Перезапусти сервис: **Профиль → 🔄 Перезапустить сервис**

Если overlay не работает:
1. **Профиль → 🪟 Разрешение Overlay** → разреши

---

*Solo Leveling — стань охотником своей жизни. ⚔️*
