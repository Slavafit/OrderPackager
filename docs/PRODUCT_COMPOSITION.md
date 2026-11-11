# Product composition

The registration screen now opens Contents → Category → Product → Quantity.
Finish includes the currently displayed quantity, saves a local draft and returns
to registration. The button counts distinct categories, not units or products.
Zero removes a product. Back from quantity cancels that quantity edit; leaving
the editor requires confirmation and does not save the draft edits.

Drafts survive process restarts in SharedPreferences, keyed by order and cyclic
position. Registering the position writes the product snapshots and quantities
into Room, then removes the draft. Drafts are local; existing order sync sends
registered contents to Supabase when the order is completed. The app therefore
still works offline. This does not introduce live cross-device draft editing.

Room migration 6 → 7 adds compositionJson. Existing data is retained. Supabase
already stores positions as JSONB, so the new composition array requires no SQL
migration. Existing authentication and ownership rules remain in force. No remote
schema changes or data deletion are performed by this change.

The bundled catalog contains the supplied 10 categories and 33 products, with
Russian, English and Spanish names. Gloves and Socks keep their stable export IDs;
these are not original Firestore UUIDs. This catalog is bundled/offline, not a
remotely editable catalog.

Order reports and history reports aggregate quantities by product ID and unit.
They use reportNameEn and the singular/plural export units. The order completion
screen also shows product totals; its editable position list remains below them.
Legacy checkbox-only positions have unknown quantities and are explicitly
excluded from product totals. We do not infer one item from a checked category.

## Validation before release

Run `bash gradlew testDebugUnitTest assembleDebug` with JDK 17+ and Android SDK 36.
CompositionTest covers category counts, aggregation across positions, separate
units, large totals, removed items, and old/new cloud payloads.

On a device:
- Select two products in Clothing and one in Footwear; finish with a quantity
  screen open. Registration must show 2 categories and preserve the last quantity.
- Reopen, edit/remove products, rotate, cancel, and relaunch the app. Saved drafts
  must remain and cancelled edits must not overwrite them.
- Register quantities 2 and 5 of the same product under two different positions.
  The shared order report must show 7, with no surname-based composition list.
- Complete and sync the order, then pull on a second device and check quantities.
- Upgrade a v6 database and verify existing orders/clients remain readable.

The cloud coding environment could not run Gradle: binary wrapper retrieval and
build dependency access were blocked by its network permissions. No APK or
successful JVM/device test run is claimed.
