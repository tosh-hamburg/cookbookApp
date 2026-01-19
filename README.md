# Cookbook Android App

Eine native Android-App als mobiles Frontend für die Cookbook-Webanwendung.

## Features

- 🔐 **Authentifizierung**: Login mit Benutzername/Passwort oder Google Sign-In
- 🔒 **2FA-Unterstützung**: Zwei-Faktor-Authentifizierung
- 📖 **Rezeptliste**: Übersichtliche Darstellung aller Rezepte mit Filterung nach Kategorien
- 🔍 **Suche**: Rezepte durchsuchen
- 📝 **Rezepte erstellen/bearbeiten**: Neue Rezepte anlegen und bestehende bearbeiten
- 🌐 **Rezept-Import**: Rezepte von URLs automatisch importieren
- 📤 **Teilen**: Rezepte mit anderen teilen
- 📅 **Wochenplaner**: Mahlzeiten für die Woche planen mit Zutatenliste

## Voraussetzungen

- Android Studio (Arctic Fox oder neuer)
- Android SDK 26+ (Min SDK)
- Android SDK 34 (Target/Compile SDK)
- Kotlin 1.9+
- Ein laufendes Cookbook-Backend

## Setup

### 1. Repository klonen

```bash
git clone https://github.com/your-username/cookbook-android.git
cd cookbook-android
```

### 2. Konfigurationsdatei erstellen

Die App benötigt eine `local.properties` Datei im Projektroot mit deinen Server-Einstellungen.

**Kopiere die Beispieldatei:**

```bash
cp local.properties.example local.properties
```

**Bearbeite `local.properties` und trage deine Werte ein:**

```properties
# Android SDK (wird meist automatisch von Android Studio gesetzt)
sdk.dir=/path/to/your/Android/Sdk

# Google Sign-In Client ID (optional, für Google-Login)
GOOGLE_CLIENT_ID=your-google-client-id.apps.googleusercontent.com

# API Server Konfiguration
API_URL_INTERNAL=https://your-server.local:3003/api
API_URL_EXTERNAL=https://cookbook.yourdomain.com/api
INTERNAL_HOST=your-server.local
INTERNAL_PORT=3003
```

> ⚠️ **Wichtig**: Die `local.properties` Datei enthält sensible Daten und wird von Git ignoriert. Committe diese Datei niemals!

### 3. Google Sign-In konfigurieren (optional)

Für Google Sign-In musst du eine Google Cloud Console OAuth Client ID erstellen:

1. Gehe zu [Google Cloud Console](https://console.cloud.google.com/)
2. Erstelle ein neues Projekt oder wähle ein bestehendes
3. Aktiviere die "Google Sign-In API"
4. Gehe zu "Credentials" → "Create Credentials" → "OAuth Client ID"
5. Erstelle eine **Web Application** Client ID (für das Backend)
6. Erstelle eine **Android** Client ID:
   - Package Name: `com.cookbook.app`
   - SHA-1 Fingerprint deines Debug-Keystores:
     ```bash
     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
     ```
7. Trage die **Web Client ID** in `local.properties` ein:
   ```properties
   GOOGLE_CLIENT_ID=your-web-client-id.apps.googleusercontent.com
   ```

### 4. API Server Konfiguration

Die App unterstützt automatische Netzwerkerkennung, um zwischen internem und externem Zugriff zu unterscheiden:

| Einstellung | Beschreibung | Beispiel |
|-------------|--------------|----------|
| `API_URL_INTERNAL` | URL für Zugriff im lokalen Netzwerk | `https://192.168.1.100:3003/api` |
| `API_URL_EXTERNAL` | URL für Zugriff über Internet | `https://cookbook.example.com/api` |
| `INTERNAL_HOST` | Host für Netzwerk-Check | `192.168.1.100` |
| `INTERNAL_PORT` | Port für Netzwerk-Check | `3003` |

**Wie funktioniert die Netzwerkerkennung?**

Die App prüft beim Start, ob `INTERNAL_HOST:INTERNAL_PORT` erreichbar ist:
- **Erreichbar** → Verwendet `API_URL_INTERNAL`
- **Nicht erreichbar** → Verwendet `API_URL_EXTERNAL`

**Typische Konfigurationen:**

*Gleicher Server für intern/extern (Reverse Proxy):*
```properties
API_URL_INTERNAL=https://cookbook.example.com/api
API_URL_EXTERNAL=https://cookbook.example.com/api
INTERNAL_HOST=cookbook.example.com
INTERNAL_PORT=443
```

*Separater interner Zugang:*
```properties
API_URL_INTERNAL=https://192.168.1.50:3003/api
API_URL_EXTERNAL=https://cookbook.example.com/api
INTERNAL_HOST=192.168.1.50
INTERNAL_PORT=3003
```

### 5. Build und Run

**Via Gradle:**
```bash
./gradlew assembleDebug
```

**Via Android Studio:**
- Öffne das Projekt in Android Studio
- Run → Run 'app'

## Projektstruktur

```
app/src/main/
├── java/com/cookbook/app/
│   ├── CookbookApplication.kt      # Application-Klasse
│   ├── data/
│   │   ├── api/                    # Retrofit API Definitionen
│   │   ├── auth/                   # Token-Management
│   │   ├── models/                 # Datenmodelle
│   │   └── repository/             # Repository-Pattern
│   ├── ui/
│   │   ├── adapter/                # RecyclerView Adapter
│   │   ├── LoginActivity.kt        # Login-Screen
│   │   ├── MainActivity.kt         # Rezeptliste
│   │   ├── RecipeDetailActivity.kt # Rezeptdetails
│   │   ├── RecipeEditActivity.kt   # Rezept erstellen/bearbeiten
│   │   ├── RecipeImportActivity.kt # Rezept importieren
│   │   └── WeeklyPlannerActivity.kt # Wochenplaner
│   └── util/                       # Hilfsfunktionen
└── res/
    ├── drawable/                   # Icons und Grafiken
    ├── layout/                     # XML Layouts
    ├── menu/                       # Menü-Definitionen
    └── values/                     # Strings, Colors, Themes
```

## API-Endpunkte

Die App kommuniziert mit dem Cookbook-Backend über folgende Endpunkte:

### Authentifizierung
- `POST /auth/login` - Login mit Benutzername/Passwort
- `POST /auth/google` - Google Login
- `GET /auth/me` - Aktueller Benutzer

### Rezepte
- `GET /recipes` - Alle Rezepte (paginiert)
- `GET /recipes/:id` - Einzelnes Rezept
- `POST /recipes` - Rezept erstellen
- `PUT /recipes/:id` - Rezept aktualisieren
- `DELETE /recipes/:id` - Rezept löschen
- `POST /import` - Rezept von URL importieren

### Kategorien & Sammlungen
- `GET /categories` - Alle Kategorien
- `GET /collections` - Alle Sammlungen

### Wochenplaner
- `GET /mealplans/:weekStart` - Wochenplan abrufen
- `PUT /mealplans/:weekStart/slots/:day/:mealType` - Mahlzeit setzen
- `DELETE /mealplans/:weekStart/slots/:day/:mealType` - Mahlzeit entfernen
- `POST /mealplans/:weekStart/sent-ingredients` - Zutaten als gesendet markieren

## Technologien

- **Kotlin** - Programmiersprache
- **Retrofit** - HTTP Client
- **Coil** - Bildladung
- **Material Design 3** - UI-Komponenten
- **DataStore** - Persistente Datenspeicherung
- **Coroutines** - Asynchrone Programmierung
- **Google Credential Manager** - Google Sign-In

## Troubleshooting

### Build-Fehler: "local.properties not found"
Stelle sicher, dass du `local.properties.example` zu `local.properties` kopiert und die Werte eingetragen hast.

### Google Sign-In funktioniert nicht
- Überprüfe, ob die Web Client ID korrekt in `local.properties` eingetragen ist
- Stelle sicher, dass der SHA-1 Fingerprint in der Google Cloud Console korrekt ist
- Das Backend muss ebenfalls für Google Auth konfiguriert sein

### Verbindungsfehler
- Überprüfe, ob die API-URLs in `local.properties` korrekt sind
- Stelle sicher, dass das Backend läuft und erreichbar ist
- Bei selbstsignierten Zertifikaten: SSL-Pinning beachten

## Lizenz

MIT License - siehe [LICENSE](LICENSE)
