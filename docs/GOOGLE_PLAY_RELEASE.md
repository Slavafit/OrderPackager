# Подготовка Order Packer к Google Play

## Техническое состояние

- Пакет приложения: `com.orderpackager`.
- Версия: `v1.22`, `versionCode 20`.
- Минимальная версия Android: 8.0 (API 26).
- Target/compile SDK: API 36.
- Формат загрузки: Android App Bundle (`.aab`).
- В проекте настроено чтение ключа подписи из локального `local.properties`.

> Имя пакета нельзя изменить после создания приложения в Google Play. До первой
> загрузки нужно окончательно решить, подходит ли `com.orderpackager`.

## Облачная безопасность

Синхронизация использует Supabase Auth. Пароль не встраивается в приложение и не
сохраняется; локально хранится обновляемая сессия. RLS ограничивает заказы владельцем.

## Что потребуется от владельца приложения

1. Аккаунт разработчика Google Play и завершённая проверка личности.
2. Окончательное имя приложения и пакет `com.orderpackager`.
3. Контактный email поддержки.
4. Публичный HTTPS-адрес политики конфиденциальности (не PDF, без авторизации).
5. Графика страницы приложения:
   - иконка 512×512 PNG;
   - feature graphic 1024×500;
   - минимум два скриншота телефона;
   - краткое и полное описание на нужных языках.
6. Ответы для разделов **Data safety**, **App access**, **Ads**, **Content rating** и
   **Target audience**.

## Данные для Data safety

Приложение отправляет в Supabase фамилию клиента, номер бокса и партии, содержимое
заказа, размеры и вес. Эти данные используются для облачного резервирования и
восстановления, передаются по HTTPS и не предназначены для рекламы или продажи.
Ответы в Play Console должны совпадать с опубликованной политикой конфиденциальности.

## Ключ загрузки

Создайте отдельный upload key через Android Studio: **Build → Generate Signed App
Bundle or APK → Android App Bundle → Create new**. Храните `.jks` вне Git и сделайте
резервную копию.

Добавьте в локальный `local.properties`:

```properties
RELEASE_STORE_FILE=путь/к/upload-key.jks
RELEASE_STORE_PASSWORD=пароль_хранилища
RELEASE_KEY_ALIAS=upload
RELEASE_KEY_PASSWORD=пароль_ключа
```

Не добавляйте эти значения и файл ключа в Git.

## Проверка и сборка

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest lintRelease bundleRelease
```

Готовый подписанный файл появится в:

`app/build/outputs/bundle/release/app-release.aab`

Проверьте на странице **App bundle explorer**, что versionCode равен 20, затем сначала
загрузите сборку в **Internal testing**. После установки из Google Play проверьте
печать, весы, завершение заказа и облачную синхронизацию.

Для каждого следующего релиза увеличивайте `versionCode`.

В приложение встроен офлайн-экран политики. Готовая HTML-копия для публикации:
`docs/privacy-policy.html`. После размещения добавьте URL в `local.properties`:

```properties
PRIVACY_POLICY_URL=https://example.com/privacy-policy
```
