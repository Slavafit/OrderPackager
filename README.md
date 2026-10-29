# Order Packager

Mobile application for fast and convenient order packing at the warehouse.

Current version: **v1.23**.

## Project Description

The application is designed for warehouse employees. It allows you to quickly process customer orders, where each customer has a list of positions (names/surnames).

### Core Workflow

- There is a **Customers List** (surnames only)
- There is a **Single Cyclic List** of names/surnames (positions)
- After selecting a customer, the app goes through positions from the cyclic list
- For each position the user marks the content and records the weight
- After finishing the customer's order, the total weight, box size, and sharing option become available

---

## Key Features

- Customer selection from the list
- Cyclic navigation through positions (names/surnames)
- Marking order content (Clothes, Shoes, Cosmetics, Accessories, Other)
- Getting weight from network scales (`http://192.168.1.50/`)
- Label printing via net printer 192.168.X.XX
- Automatic calculation of total order weight
- Box size adjustment at the end of the order
- **Share Order** function (WhatsApp, Telegram, etc.)
- Daily order history and summary report
- Editing both directories (customers and positions)
- Cloud backup and synchronization of the clients directory
- Cloud backup and synchronization of the cyclic positions directory

---

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Architecture:** Clean Architecture + MVVM
- **Database:** Room (offline) + Supabase (cloud synchronization)
- **Networking:** Retrofit + OkHttp
- **Printing:** Android PrintManager (network printer)
- **Build System:** Gradle with Version Catalog

---

## Requirements

- Android 8.0+ (minSdk 26)
- Android SDK 36
- JDK 17+
- Tablet is highly recommended
- Access to local network scales (`http://192.168.1.50/`)
- Network printer for label printing

---

## Project Structure
OrderPackager/
├── app/
│   ├── src/main/java/com/orderpackager/
│   │   ├── data/           # Room, Repositories
│   │   ├── domain/         # Models and Use Cases
│   │   ├── presentation/   # UI Screens + ViewModels
│   │   └── utils/
├── libs.versions.toml
├── gradle.properties
└── README.md

---

## How to Run

1. Clone the repository
2. Open the project in Android Studio
3. Add the local Supabase settings described in [`supabase/README.md`](supabase/README.md)
4. Click **Sync Project with Gradle Files**
5. Run on a phone or emulator

---

## Future Plans

- Excel report export
- Frequent order templates
- Photo capture of packed orders
- Multi-user support

---

**Developed to optimize the order packing process.**

---

## Облачная синхронизация

Приложение сохраняет данные локально в Room и синхронизирует завершённые заказы с
Supabase в фоне. Инструкция первоначальной настройки находится в
[`supabase/README.md`](supabase/README.md).

## Google Play

Проект подготовлен для сборки Android App Bundle с `targetSdk 36`. Чек-лист первой
публикации, настройки подписи, Data safety и необходимые материалы описаны в
[`docs/GOOGLE_PLAY_RELEASE.md`](docs/GOOGLE_PLAY_RELEASE.md).

Финальная политика конфиденциальности находится в
[`docs/PRIVACY_POLICY.md`](docs/PRIVACY_POLICY.md), а готовая для
публикации веб-версия — в [`docs/privacy-policy.html`](docs/privacy-policy.html).
Для Google Play веб-версию необходимо разместить на публичной HTTPS-странице.
Для других приложений можно использовать
[`docs/PRIVACY_POLICY_TEMPLATE.md`](docs/PRIVACY_POLICY_TEMPLATE.md) с заменяемыми
полями и необязательными разделами.

Синхронизация использует Supabase Auth и RLS: пользователь входит в приложение, а
облачные заказы доступны только владельцу записи.
