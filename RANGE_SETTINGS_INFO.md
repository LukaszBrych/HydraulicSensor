# Range Settings Dialog - End Values Display

## Ostatnie zmiany / Recent Changes

### 2024-01-24: Rozdzielenie połączenia BLE od pomiarów

**Zmieniony flow aplikacji:**

1. **Przycisk "Połącz"** - tylko łączy się z SensorBox przez BLE
   - Status zmienia się na "Połączono" (zielona kropka)
   - NIE rozpoczyna automatycznie pomiarów
   - Pojawia się przycisk "Rozłącz" oraz "Rozpocznij pomiary"

2. **Przycisk "Rozpocznij pomiary"** (widoczny tylko gdy połączony)
   - Wysyła komendę `d` (live data stream)
   - Rozpoczyna odczyt pomiarów w czasie rzeczywistym
   - Zmienia się w "Zatrzymaj pomiary"

3. **Przycisk "Zatrzymaj pomiary"**
   - Zatrzymuje komendę `d` (stopLiveRead)
   - Przestaje odczytywać pomiary
   - NIE rozłącza BLE - można ponownie kliknąć "Rozpocznij pomiary"

4. **Przycisk "Rozłącz"**
   - Zatrzymuje pomiary (jeśli aktywne)
   - Rozłącza BLE (gatt.disconnect())
   - Przywraca przycisk "Połącz"

**Korzyści:**
- Możesz połączyć się i wysłać komendy bez rozpoczynania pomiarów
- Możesz zatrzymać pomiary bez rozłączania
- Mniej przypadkowych restartów połączenia

---

## Zmiany / Changes

Dodano wyświetlanie rzeczywistych wartości końcowych (W) z SensorBox w dialogu ustawień zakresów.

### Nowe Funkcje / New Features

1. **Automatyczne odpytywanie wartości końcowych**
   - Dialog automatycznie wysyła komendę `e` po otwarciu
   - Wartości są odczytywane bezpośrednio z SensorBox
   - Format odpowiedzi: `#10.00#10.00#10.00#125.00#150.00#394.00#`

2. **Pole tylko do odczytu dla wartości W**
   - Wyświetla aktualną wartość końcową dla danego czujnika
   - Jednostka automatycznie dopasowana do wybranej w dialogu
   - Kolor tła: szary (wskazuje że pole jest nieaktywne)

3. **Informacja dla użytkownika**
   - "ℹ️ Read from sensor via 'e' command. To change W values, use USB/BlueBox connection (BLE doesn't support 'w' command)."
   - Wyjaśnia dlaczego pole jest tylko do odczytu
   - Informuje o alternatywnej metodzie zmiany wartości

## Jak to działa / How It Works

### 1. MainActivity.kt
```kotlin
// Nowy stan dla wartości końcowych
private val endValues = mutableStateListOf("10.00", "10.00", "10.00", "125.00", "150.00", "394.00")

// Parsowanie odpowiedzi na komendę 'e'
if (response.startsWith("#") && response.count { it == '#' } >= 7) {
    val values = response.split("#").filter { it.isNotBlank() }
    if (values.size >= 6) {
        handler.post {
            for (i in 0 until 6) {
                endValues[i] = values[i].trim()
            }
        }
    }
}
```

### 2. OfflineRecordingScreen.kt
```kotlin
fun OfflineRecordingScreen(
    // ...
    endValues: List<String>,              // Nowy parametr
    onQueryEndValues: () -> Unit,         // Nowy callback
)

// Przekazanie do dialogu
RangeSettingsDialog(
    currentEndValue = endValues.getOrNull(index) ?: "---",
    onQueryEndValues = onQueryEndValues,
)
```

### 3. RangeSettingsDialog.kt
```kotlin
// Automatyczne zapytanie o wartości przy otwarciu
LaunchedEffect(Unit) {
    onQueryEndValues()
}

// Nowe pole read-only
OutlinedTextField(
    value = currentEndValue,
    onValueChange = {},
    enabled = false,  // Tylko do odczytu
    suffix = { Text(currentUnit) }
)
```

## Przykład użycia / Example Usage

1. **Otwórz dialog ustawień** - kliknij ikonę ⚙️ przy czujniku
2. **Poczekaj ~500ms** - dialog automatycznie wyśle komendę `e`
3. **Sprawdź wartość W** - zobaczysz rzeczywistą wartość z SensorBox
4. **Zmień zakres (R)** - możesz zmienić R1-R5 (to działa przez BLE)
5. **Wartość W** - jest tylko do odczytu, wymagana zmiana przez USB

## Dlaczego W jest tylko do odczytu? / Why is W Read-Only?

### Ograniczenie BLE / BLE Limitation
```
Komenda 'w' jest zablokowana w firmware BLE
Command 'w' is blocked in BLE firmware

✅ Działa przez BLE:    z, p, r, e, g, ba, v, h
❌ Blokowane przez BLE: K, w, ka, kr, we
```

### Rozwiązanie / Solution
- **Odczyt**: Komenda `e` działa przez BLE ✅
- **Zapis**: Wymagany USB lub BlueBox adapter ⚠️

## Testowanie / Testing

### 1. Sprawdź wartości początkowe
```bash
# W Logcat szukaj:
"✅ Odpowiedź 'e': Wartości końcowe = #10.00#10.00#..."
```

### 2. Test w dialogu
1. Otwórz dialog czujnika P1
2. Sprawdź czy wartość W to `10.00 bar` (lub inna z SensorBox)
3. Zmień jednostkę na `psi` - wartość powinna się przeliczyć
4. Pole powinno być szare (disabled)

### 3. Weryfikacja komunikacji
```kotlin
// Wyślij ręcznie w CommandTestScreen:
"e"

// Odpowiedź w Logcat:
"✅ Odpowiedź 'e': Wartości końcowe = #10.00#10.00#10.00#125.00#150.00#394.00#"
"   P1 end value = 10.00"
"   P2 end value = 10.00"
"   P3 end value = 10.00"
"   P4 end value = 125.00"
"   P5 end value = 150.00"
"   P6 end value = 394.00"
```

## Uwagi / Notes

### Wartości domyślne
- Na starcie aplikacji używane są wartości początkowe z kodu
- Po pierwszym zapytaniu `e` - aktualizowane na rzeczywiste z SensorBox
- Wartości są przechowywane w `MainActivity.endValues`

### Synchronizacja
- Każde otwarcie dialogu → automatyczne zapytanie `e`
- Odpowiedź pojawia się po ~200-500ms
- Stan jest współdzielony między wszystkimi czujnikami

### Konwersja jednostek
- Dialog automatycznie przelicza wartość W przy zmianie jednostki
- Np. `10.00 bar` → `145.04 psi` → `1.00 MPa`
- Konwersja tylko wizualna, nie wysyła do SensorBox

## Kolejne kroki / Next Steps

### Opcjonalne usprawnienia
1. **Wskaźnik ładowania** - pokazuj spinner podczas oczekiwania na odpowiedź `e`
2. **Cache timeout** - odśwież wartości tylko jeśli minęło >10 sekund
3. **Kolorowy wskaźnik** - zielony gdy wartość aktualna, żółty gdy stara
4. **Historia zmian** - loguj kiedy wartości W się zmieniły
5. **Porównanie** - pokaż poprzednią vs aktualną wartość

### Known Issues
- Brak obsługi błędów gdy `e` command timeout
- Brak wizualnej informacji o ładowaniu danych
- Wartość może być "stara" jeśli dialog był otwarty długo

---

**Data:** 2024
**Status:** ✅ Zaimplementowane i przetestowane
**Wersja:** 1.0
