# Instrukcja komend SensorBox MC6600

## Dostosowanie aplikacji zgodnie z instrukcją programowania MC 6600 SmartVision

### Zaimplementowane komendy

#### 1. Pomiary
- **`d`** - Live data stream (250ms), format: `#val1#val2#val3#val4#val5#val6#`
  - Automatycznie uruchamiany po połączeniu
  - Wysyłany co 250ms przez `liveReadRunnable`
  
- **`z`** - Pomiar czytelny z jednostkami
  - Wyświetla wszystkie 6 kanałów w ustawionej jednostce
  - Wartość -100 = brak czujnika lub błąd
  - Funkcja: `queryReadableMeasurement()`

#### 2. Konfiguracja zakresów

- **`p`** - Zapytanie o ustawione zakresy
  - Odpowiedź: np. "555212" (6 cyfr, każda 1-5)
  - Funkcja: `queryCurrentRanges()`
  - Wywoływana automatycznie po połączeniu

- **`r######`** - Ustawienie zakresów pomiarowych
  - 6 cyfr dla kanałów P1-P6, wartości 1-5
  - Przykład: `r433222` ustawi P1=R4, P2=R3, P3=R3, P4=R2, P5=R2, P6=R2
  - Wywoływana w `sendRangeSettingsToSensorBox()`

- **`w{CH}{RN} {value} {unit}`** - Ustawienie wartości końcowej zakresu
  - CH = kanał (1-6)
  - RN = zakres (1-5)
  - Przykład: `w23 250 bar` - kanał 2, zakres 3, wartość 250 bar
  - Wywoływana w `sendRangeSettingsToSensorBox()`

#### 3. Odczyt wartości końcowych

- **`e`** - Wyprowadzenie wartości końcowych zakresów
  - Odpowiedź: `#100.00#200.00#300.00#500.00#2600.00#6000.00#`
  - Funkcja: `queryEndValues()`

- **`ba`** - Lista wszystkich wartości końcowych zakresów/jednostek
  - Funkcja: `queryBulkAll()`
  - Wywoływana automatycznie po połączeniu

#### 4. Konfiguracja LPM (turbiny Q1, Q2)

- **`g`** - Zapytanie o wartości LPM
  - Wyświetla konfigurację krzywych kalibracyjnych turbin
  - Funkcja: `queryLpmConfig()`

- **`K{CH}{RN} {param1} {param2} ... {param6}`** - Ustawienie wartości LPM
  - CH = 5 (Q1) lub 6 (Q2)
  - RN = zakres 1-5 (Q1) lub 1-2 (Q2)
  - 6 parametrów krzywej kalibracyjnej turbiny (oddzielone JEDNĄ spacją!)
  - Przykład: `K51 2600.00 25.0 1200.0 12.00 160.0 1.50`
  - Funkcja: `setLpmConfig(channel, range, params)`

#### 5. Kalibracja

- **`ka`** - Kalibracja kanałów prądowych
  - ⚠️ **WYMAGANIA**: Wszystkie kanały oprócz P2 wolne, 20mA na P2
  - Automatycznie ustawia zakresy na `r555111` (600 bar, 125°C)
  - Funkcja: `calibrateCurrentChannels()`

- **`kr`** - Wyświetlenie wartości kalibracji
  - Pokazuje aktualną wartość kalibracji i % odchylenia od standardu
  - Funkcja: `queryCalibrationValues()`

#### 6. System

- **`v`** - Napięcie akumulatora
  - Wyświetla napięcie w voltach (V)
  - Funkcja: `queryBatteryVoltage()`
  - Wywoływana automatycznie po połączeniu

- **`h`** - Numer wersji firmware
  - Funkcja: `queryVersion()`

- **`q` + `we`** - Reset do ustawień fabrycznych
  - ⚠️ **UWAGA**: Przywraca wszystkie zakresy do parametrów podstawowych
  - Funkcja: `restoreFactorySettings()`
  - Wykonuje `q`, czeka 100ms, następnie `we`

### Zaktualizowane struktury danych

#### rangeCodes (MainActivity.kt)
Zaktualizowane zgodnie z instrukcją - zakresy R1-R5 reprezentują **TYPY CZUJNIKÓW**, nie skalowanie:

```kotlin
// P1, P2 - Pressure sensors (4-20mA)
mapOf("10 bar" to 1, "60 bar" to 2, "100 bar" to 3, "250 bar" to 4, "600 bar" to 5)

// P3 - Differential/Pressure (R1 = P1-P2 differential)
mapOf("P1-P2" to 1, "60 bar" to 2, "100 bar" to 3, "250 bar" to 4, "600 bar" to 5)

// T - Temperature sensor types
mapOf("125°C" to 1, "500°C" to 2, "200°C" to 3, "Custom" to 4, "Custom" to 5)

// Q1 - Turbine types (flow)
mapOf("1-60 lpm" to 1, "1-100 lpm" to 2, "1-250 lpm" to 3, "1-600 lpm" to 4, "Custom" to 5)

// Q2 - Multi-function
mapOf("Flow R1" to 1, "Flow R2" to 2, "P1×Q1" to 3, "RPM-1" to 4, "RPM-2" to 5)
```

#### RangeSettingsDialog.kt
Zaktualizowane limity i etykiety zgodnie z typami czujników:

- **P1, P2**: Domyślne wartości 10, 60, 100, 250, 600 bar
- **P3**: R1 = "P1-P2" (tryb różnicowy), reszta jak P1/P2
- **T**: 125, 500, 200, 300, 400°C
- **Q1**: 60, 100, 250, 600, 1000 lpm (custom)
- **Q2**: R1-R2=flow, R3=P1×Q1 (moc hydrauliczna), R4-R5=RPM

### Parsowanie odpowiedzi (gattCallback)

Dodana funkcja `parseCommandResponse()` rozpoznająca:
- Odpowiedź na `p`: 6 cyfr 1-5 (np. "555212")
- Odpowiedź na `e`: format `#val1#val2#...#` z wieloma #
- Odpowiedź na `v`: tekst zawierający "V" lub voltage
- Odpowiedź na `h`: numer wersji
- Odpowiedź na `z`: pomiar z jednostkami (bar, C, lpm)
- Odpowiedź na `g`/`ba`: dane konfiguracyjne
- Odpowiedź na `kr`: dane kalibracyjne (%, Calibration)

### Ekran testowy (CommandTestScreen.kt)

Nowy ekran dostępny przez przycisk **"Komendy"** na głównym ekranie:

**Sekcje:**
1. **Komendy pomiarowe**: `z`
2. **Konfiguracja zakresów**: `p`, `e`, `ba`
3. **Konfiguracja LPM**: `g`, `K##`
4. **Kalibracja**: `ka`, `kr`
5. **System**: `v`, `h`, `q+we`
6. **Własna komenda**: dowolny tekst

Wszystkie odpowiedzi logowane w Logcat z tagiem `SensorBox`.

### Automatyzacja po połączeniu

Po udanym połączeniu BLE (`onDescriptorWrite`):
1. Odczekaj 100ms → `queryCurrentRanges()` (p)
2. Odczekaj 300ms → `queryBulkAll()` (ba)
3. Odczekaj 500ms → `queryBatteryVoltage()` (v)
4. Odczekaj 700ms → Uruchom live read + wyślij zakresy

### Przykład użycia

#### Ustawienie zakresu dla przepływomierza 0-27 lpm:

1. Otwórz RangeSettingsDialog dla Q1 (P5)
2. Wybierz **R1** (typ turbiny "1-60 lpm")
3. Ustaw maksymalną wartość: **27 lpm**
4. Kliknij "Zapisz"

Aplikacja wyśle:
```
r444111  // Jeśli pozostałe kanały mają R4
w51 27 lpm  // Ustawienie wartości końcowej dla Q1, zakres 1
```

#### Konfiguracja LPM dla turbiny:

1. Przejdź do ekranu "Komendy"
2. W sekcji "Konfiguracja LPM":
   - Kanał: **5** (Q1)
   - Zakres: **1** (R1)
   - Parametry: **2600.00 25.0 1200.0 12.00 160.0 1.50**
3. Kliknij "K - Wyślij konfigurację LPM"

Aplikacja wyśle:
```
K51 2600.00 25.0 1200.0 12.00 160.0 1.50
```

### Uwagi techniczne

1. **Kolejkowanie komend**: Wszystkie komendy są kolejkowane przez `enqueueWrite()` aby uniknąć kolizji BLE
2. **Parsowanie ramek**: Live data (`d`) używa bufora do łączenia fragmentów ramek
3. **Opóźnienia**: Handler.postDelayed zapewnia odpowiednie odstępy czasowe między komendami
4. **Format komend**: Wszystkie komendy kończą się `\n`

### Logowanie

Wszystkie komendy i odpowiedzi są logowane w Logcat:
```bash
adb logcat -s SensorBox
```

Przykłady logów:
```
D/SensorBox: Wysłano komendę: p (zapytanie o zakresy)
D/SensorBox: Odpowiedź 'p': Aktualne zakresy = 555212
D/SensorBox: Wysłano komendę: K51 2600.00 25.0 1200.0 12.00 160.0 1.50 (konfiguracja LPM)
```

### Pliki zmodyfikowane

1. **MainActivity.kt**:
   - Dodany komentarz dokumentacyjny z listą komend
   - Funkcje: `queryCurrentRanges()`, `queryEndValues()`, `queryBulkAll()`, `queryBatteryVoltage()`, `queryVersion()`, `queryLpmConfig()`, `setLpmConfig()`, `restoreFactorySettings()`, `calibrateCurrentChannels()`, `queryCalibrationValues()`, `queryReadableMeasurement()`, `sendCustomCommand()`
   - Zaktualizowana funkcja `parseCommandResponse()`
   - Automatyczne odpytywanie konfiguracji po połączeniu
   - Nawigacja do ekranu testowego

2. **RangeSettingsDialog.kt**:
   - Zaktualizowane wartości domyślne i limity zgodnie z instrukcją
   - Etykiety odzwierciedlające typy czujników

3. **OfflineRecordingScreen.kt**:
   - Dodany przycisk "Komendy"
   - Nowy callback `onCommandTest`

4. **CommandTestScreen.kt** (nowy):
   - Ekran testowy ze wszystkimi komendami
   - UI do wysyłania komend LPM
   - Przycisk reset fabryczny
   - Pole własnej komendy

### Zgodność z instrukcją MC 6600 SmartVision

✅ Wszystkie komendy z instrukcji zaimplementowane  
✅ Format komend zgodny z dokumentacją  
✅ Automatyczne odczytywanie konfiguracji po połączeniu  
✅ Kalibracja z automatycznym ustawianiem zakresów  
✅ LPM config dla Q1 i Q2  
✅ Parsowanie wszystkich typów odpowiedzi  
✅ Bezpieczne kolejkowanie komend BLE  
