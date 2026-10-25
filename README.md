# Status aut — Android (Kotlin + Jetpack Compose)

Natywna aplikacja Android oparta na istniejącym backendzie PHP/MySQL.

## Co zawiera ta wersja

- natywny interfejs Jetpack Compose / Material 3;
- ekran statusu aut z kartami wolne/zajęte;
- akcje zajęcia i zwolnienia auta przez istniejące `api.php`;
- ekran planera pobierający dane z `reservations.php`;
- ekran statystyk pobierający dane z `stats.php`;
- ustawienia adresu API i opcjonalnego wspólnego kodu tablicy;
- workflow GitHub Actions do budowania debug APK.

Planer i statystyki w tej wersji są etapem bazowym: pobierają dane i pokazują odpowiedź JSON. Formularze rezerwacji, natywne wykresy oraz pełny panel administratora wymagają kolejnego etapu implementacji.

## Ważne: backend

Aplikacja nie zawiera PHP ani danych dostępowych do MySQL. PHP/MySQL nadal musi działać na serwerze. W ustawieniach aplikacji wpisz bazowy adres HTTPS katalogu, w którym są pliki `api.php`, `reservations.php` i `stats.php`, np.:

`https://twoja-domena.pl/auta/`

Nie umieszczaj haseł bazy danych ani pliku `config.php` w repozytorium aplikacji.

## Budowanie na GitHubie

1. Utwórz nowe repozytorium GitHub, np. `status-aut-android`.
2. Wgraj zawartość tego projektu do głównego katalogu repozytorium.
3. Otwórz zakładkę **Actions** i uruchom workflow **Build Android APK** (lub wykonaj push do `main`).
4. Po zakończeniu kompilacji otwórz uruchomiony workflow i pobierz artefakt `status-aut-debug-apk`.
5. Zainstaluj `app-debug.apk` na telefonie.

## Technologie

- Kotlin
- Jetpack Compose
- Material 3
- Android Gradle Plugin 8.7.3
- Java 17

## Bezpieczeństwo

Adres API powinien korzystać z HTTPS. Kod dostępu do tablicy jest przechowywany lokalnie w preferencjach aplikacji; na urządzeniu współdzielonym nie zapisuj poufnych danych. Nie dodawaj sekretów ani danych dostępowych z PHP do repozytorium.
