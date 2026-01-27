# Instrukcja Offline Recording - Pobieranie Danych

## Przegląd funkcjonalności

Aplikacja pozwala na:
1. **Konfigurację offline recording** - ustawienie triggera, kanałów, czasu próbkowania
2. **Start/Stop nagrywania** - kontrola zapisu do pamięci SensorBox
3. **Pobieranie danych** - transfer danych z pamięci SensorBox do telefonu
4. **Zapis CSV** - eksport danych do formatu CSV

---

## 1. Konfiguracja Offline Recording

### Przejście do ekranu konfiguracji
- Z głównego ekranu kliknij **ikonę menu** (3 kreski) w prawym górnym rogu
- Wybierz **"Offline Recording Config"**

### Ustawienia Trigger
**Trigger** określa moment rozpoczęcia zapisu:
- **Kanał** (P1-P6): Który kanał ma wyzwolić nagrywanie
- **Threshold** (0-100%): Próg wyzwolenia w procentach zakresu
- **Edge**: 
  - `Rising` (0) - start gdy wartość przekroczy próg (wzrost)
  - `Falling` (1) - start gdy wartość spadnie poniżej progu

**Przykład:**
```
Trigger: P5 (turbina)
Threshold: 20%
Edge: Rising
→ Zapis rozpocznie się gdy przepływ przekroczy 20% zakresu
```

### Wybór kanałów do zapisu
Zaznacz checkboxy dla kanałów które mają być zapisywane:
- ✅ P1, P2, P3 - ciśnienie
- ✅ P4 - temperatura
- ✅ P5, P6 - przepływ

**Uwaga:** Liczba kanałów wpływa na maksymalną liczbę próbek:
- 1 kanał: max 241 000 próbek
- 2 kanały: max 121 000 próbek
- 3-4 kanały: max 61 000 próbek

### Parametry czasowe
- **Number of Samples**: Ile próbek zapisać (np. 10000)
- **Time Base**: Okres próbkowania
  - `1ms` - 1000 próbek/s (pomiary szybkie)
  - `10ms` - 100 próbek/s (standardowe)
  - `100ms` - 10 próbek/s (wolne)
  - `1s`, `10s` - dla długich testów

**Duration** jest obliczana automatycznie:
```
Duration = (Number of Samples × Time Base) / 1000
Przykład: 10000 × 10ms = 100 sekund
```

---

## 2. Rozpoczęcie nagrywania

### Start Recording
1. Skonfiguruj parametry (trigger, kanały, timing)
2. Kliknij **"Start Offline Recording"**
3. SensorBox wysyła komendę `sr` i przechodzi w tryb oczekiwania na trigger

**Co się dzieje:**
- SensorBox czeka na spełnienie warunku triggera (np. P5 > 20%)
- Gdy warunek zostanie spełniony, zaczyna zapisywać próbki
- Lampka LED na SensorBox może migać (zależnie od modelu)
- Zapis trwa do osiągnięcia liczby próbek lub czasu trwania

### Przykładowy test turbiny
```
Konfiguracja:
- Trigger: P5 @ 20%, Rising
- Kanały: P5, P6 (obie turbiny)
- Samples: 10 000
- Time Base: 10ms
- Duration: 100s

Procedura:
1. Kliknij "Start Offline Recording"
2. Dmuchnij w turbinę P5 (przepływ > 20%)
3. SensorBox zapisuje 100 sekund danych
4. Po zakończeniu LED przestaje migać
```

### Stop Recording
Aby przerwać nagrywanie przed zakończeniem:
1. Kliknij **"Stop Offline Recording"**
2. SensorBox otrzymuje komendę `q` i wraca do trybu normal

---

## 3. Pobieranie danych z SensorBox

### Przejście do ekranu pobierania
- Z głównego ekranu kliknij **ikonę Download** (strzałka w dół)
- Otwiera się ekran **"Download Offline Data"**

### Sprawdzenie statusu
1. Kliknij **"Sprawdź Status"**
2. SensorBox odpowiada:
   - **'N' (Normal)** - ✅ gotowy do pobrania danych
   - **'R' (Recording)** - ⏺️ trwa nagrywanie, zatrzymaj najpierw

**Jeśli status = 'R':**
- Wróć do Offline Config
- Kliknij "Stop Offline Recording"
- Sprawdź status ponownie

### Pobieranie danych
1. Upewnij się że status = 'N'
2. Kliknij **"Pobierz dane z SensorBox"**

**Co się dzieje:**
1. **Pobieranie nagłówka** (`sh`)
   - Timestamp, kanały, trigger, próbki
   - End values, jednostki
2. **Pobieranie kanałów** (`sd1`, `sd2`, `sd3`, `sd4`)
   - Dla każdego zapisanego kanału
   - Binary stream zakończony `&`
   - Dekodowanie: `(byte - 40) / 1.6 → 0-100% → physical_value`
3. **Zapis CSV**
   - Plik: `sensorbox_<timestamp>.csv`
   - Lokalizacja: `Downloads/HydraulicSensorApp/`
   - Format: nagłówek + kolumny Sample,P1,P2,P3,P4

**Progress:**
- "Pobieranie nagłówka..."
- "Pobieranie P5... (1/2)"
- "Pobieranie P6... (2/2)"
- "Zapisywanie CSV..."
- "✅ Dane zapisane: sensorbox_1234567890.csv"

---

## 4. Format pliku CSV

### Nagłówek
```csv
# SensorBox MC6600 Offline Recording
# Timestamp: 1703001234
# Recording Channels: 48 (binary: 0b110000 = P5, P6)
# Trigger: P5 threshold=20% edge=0 (Rising)
# Duration: 100s, Time Base: 10ms
# End Values: P1=250.0 P2=250.0 P3=250.0 P4=125.0
# Units: P1=bar P2=bar P3=bar P4=C
# Total Samples: 10000
```

### Dane
```csv
Sample,P5,P6
0,5.234,12.456
1,5.678,12.890
2,6.123,13.234
...
9999,4.567,11.234
```

**Kolumny:**
- `Sample` - numer próbki (0, 1, 2, ...)
- `P1-P6` - wartości fizyczne w jednostkach z nagłówka

---

## 5. Protokół komunikacji (technicznie)

### Komendy SensorBox

#### Start Recording
```
sr <timestamp> <rc> <tc> <th> <ed> <du> <fac>
```
- `timestamp` - Unix epoch (sekundy)
- `rc` - recording channels (bitmask: 1=P1, 2=P2, 4=P3, 8=P4, 16=P5, 32=P6)
- `tc` - trigger channel (1-6)
- `th` - trigger threshold (0-100)
- `ed` - trigger edge (0=rising, 1=falling)
- `du` - duration (sekundy)
- `fac` - time base factor (1, 10, 100, 1000, 10000 ms)

**Przykład:**
```
sr 1703001234 48 5 20 0 100 10
→ Start recording: P5+P6, trigger P5@20% rising, 100s, 10ms
```

#### Stop Recording
```
q
```
Odpowiedź: brak (SensorBox wraca do trybu normal)

#### Check Mode
```
m
```
Odpowiedź: 
- `N` - Normal mode
- `R` - Recording mode

#### Send Header
```
sh
```
Odpowiedź (15 linii):
```
1703001234    # timestamp
48            # rc (bitmask)
5             # tc (trigger channel)
20            # th (threshold)
0             # ed (edge)
100           # du (duration)
10            # tb (time base)
250.0         # e1 (end value P1)
250.0         # e2
250.0         # e3
125.0         # e4
bar           # u1 (unit P1)
bar           # u2
bar           # u3
C             # u4
10000         # end (number of samples)
```

#### Send Channel Data
```
sd1  # Download P1
sd2  # Download P2
sd3  # Download P3
sd4  # Download P4
```

Odpowiedź: Binary stream zakończony `&`
```
[binary bytes...] & 
```

**Dekodowanie:**
```kotlin
unsigned = byte.toInt() and 0xFF
percentage = (unsigned - 40.0) / 1.6
physical_value = percentage * endValue / 100
```

**Przykład:**
```
Byte = 104
unsigned = 104
percentage = (104 - 40) / 1.6 = 40.0%
physical_value = 40.0% × 250.0 bar / 100 = 100.0 bar
```

---

## 6. Przykładowy workflow

### Test turbiny z zapisem offline

**Cel:** Zarejestrować 100 sekund pracy turbiny gdy przepływ przekroczy 20%

**Krok 1: Konfiguracja**
1. Połącz z SensorBox
2. Otwórz "Offline Recording Config"
3. Ustaw:
   - Trigger: P5 @ 20%, Rising
   - Kanały: ✅ P5, ✅ P6
   - Samples: 10000
   - Time Base: 10ms
   - Duration: 100s (auto)
4. Kliknij "Start Offline Recording"

**Krok 2: Wykonanie testu**
1. Dmuchnij w turbinę P5 (przepływ > 20%)
2. SensorBox zaczyna zapisywać
3. Kontynuuj test przez 100 sekund
4. SensorBox automatycznie kończy zapis

**Krok 3: Pobieranie danych**
1. Otwórz "Download Offline Data"
2. Kliknij "Sprawdź Status" → powinno być 'N'
3. Kliknij "Pobierz dane z SensorBox"
4. Poczekaj (~30s dla 10k próbek × 2 kanały)
5. Plik CSV zapisany w Downloads/HydraulicSensorApp/

**Krok 4: Analiza**
1. Otwórz plik CSV w Excel/Google Sheets
2. Stwórz wykres Sample vs P5, P6
3. Zweryfikuj trigger (próbka 0 powinna być ~20%)
4. Sprawdź duration (10000 próbek × 10ms = 100s)

---

## 7. Troubleshooting

### Problem: Status = 'R' (nie można pobrać)
**Rozwiązanie:**
- Wróć do Offline Config → Stop Offline Recording
- Poczekaj 2 sekundy
- Sprawdź status ponownie

### Problem: Timeout podczas pobierania
**Przyczyna:** Transfer dużych danych (>100k próbek)
**Rozwiązanie:**
- Zwiększony timeout do 30s w kodzie
- Jeśli nadal timeout, zmniejsz liczbę próbek

### Problem: Plik CSV jest pusty
**Przyczyna:** Trigger nie został spełniony
**Rozwiązanie:**
- Sprawdź threshold - może być za wysoki
- Użyj Edge=Falling jeśli wartość spada

### Problem: "Nie udało się pobrać nagłówka"
**Przyczyna:** SensorBox nie odpowiada na `sh`
**Rozwiązanie:**
- Sprawdź połączenie BLE
- Disconnect → Connect ponownie
- Sprawdź czy SensorBox jest w trybie Normal

### Problem: CSV ma błędne wartości
**Przyczyna:** Błąd dekodowania binarnego
**Debug:**
```kotlin
Log.d("SensorBox", "Byte: $byte, Unsigned: $unsigned")
Log.d("SensorBox", "Percentage: $percentage, Physical: $physicalValue")
```

---

## 8. Limity techniczne

### Pamięć SensorBox
- **Całkowita pojemność:** 241 000 próbek
- **1 kanał:** 241 000 próbek
- **2 kanały:** 121 000 próbek każdy (razem 242k)
- **3-4 kanały:** 61 000 próbek każdy

### Time Base
- **Min:** 1ms (1000 Hz) - maksymalna szybkość
- **Max:** 10s (0.1 Hz) - najwolniejsza

### Transfer Speed
- **Domyślnie:** ~1000 próbek/s przez BLE
- **Dla 10k próbek:** ~10 sekund
- **Dla 100k próbek:** ~100 sekund

### Rozmiar pliku CSV
```
Header: ~500 bytes
Dane: (liczba_próbek × liczba_kanałów × 10 bytes)
Przykład: 10k próbek × 2 kanały × 10 bytes = ~200 KB
```

---

## 9. Wskazówki optymalizacji

### Dla szybkich testów (< 10s)
```
Samples: 1000
Time Base: 1ms
Duration: 1s
Kanały: 1-2
```

### Dla standardowych testów (1-2 min)
```
Samples: 10000
Time Base: 10ms
Duration: 100s
Kanały: 2-4
```

### Dla długich testów (> 10 min)
```
Samples: 60000
Time Base: 100ms
Duration: 6000s (100 min)
Kanały: 1-2
```

---

## 10. Changelog

### v1.0 (2024-01-XX)
- ✅ Implementacja offline recording config
- ✅ Implementacja download offline data
- ✅ Parser binarny + zapis CSV
- ✅ Obsługa komend `m`, `sh`, `sd1-4`
- ✅ UI z progress indicators

### Planned features
- 📋 Lista zapisanych nagrań
- 📊 Podgląd wykresów w aplikacji
- 📤 Udostępnianie CSV przez email/cloud
- 🔄 Auto-restart recording po triggerze
