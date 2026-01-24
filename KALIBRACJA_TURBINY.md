# Kalibracja turbiny 0-27 lpm dla SensorBox MC6600

## Format tablic LPM (K51-K62)

Z odpowiedzi SensorBox na komendę `g`:
```
K51 829.29 605.10 394.27 286.03 39.92 30.28
```

**Format: 6 współczynników funkcji konwersji Hz → lpm**

Prawdopodobnie wielomian lub funkcja interpolacji.

---

## Dane Twojej turbiny

Z etykiety na turbinie:
```
2625.01 Hz → 26.64 lpm
1290.76 Hz → 13.12 lpm
164.85 Hz → 1.79 lpm
```

**Stosunek Hz/lpm:**
- 2625.01 / 26.64 = 98.5 Hz/lpm
- 1290.76 / 13.12 = 98.4 Hz/lpm
- 164.85 / 1.79 = 92.1 Hz/lpm

**Średnio: ~98 Hz/lpm (bardzo liniowa charakterystyka)**

---

## Obliczenie parametrów K55

### Metoda 1: Skalowanie proporcjonalne z K51

K51 to zakres 1-60 lpm
Twoja turbina: 1-27 lpm

**Współczynnik skalowania: 27 / 60 = 0.45**

Parametry K55 (skalowane z K51):
```
829.29 × 0.45 = 373.18
605.10 × 0.45 = 272.30
394.27 × 0.45 = 177.42
286.03 × 0.45 = 128.71
39.92 × 0.45 = 17.96
30.28 × 0.45 = 13.63
```

**Komenda do wysłania:**
```
K55 373.18 272.30 177.42 128.71 17.96 13.63
```

---

### Metoda 2: Współczynniki liniowe

Jeśli to wielomian liniowy `lpm = a + b·Hz`, to:
- `a` (offset) = 0 (turbina zaczyna od 0)
- `b` = 1 / 98 = 0.01020408

**Komenda do wysłania:**
```
K55 0.0 0.01020408 0.0 0.0 0.0 0.0
```

---

### Metoda 3: Punkty interpolacji

Jeśli 6 parametrów to lpm przy równomiernie rozłożonych Hz:
- Przy 0 Hz → 0 lpm
- Przy 525 Hz (20%) → ~5.36 lpm
- Przy 1050 Hz (40%) → ~10.71 lpm
- Przy 1575 Hz (60%) → ~16.07 lpm
- Przy 2100 Hz (80%) → ~21.43 lpm
- Przy 2625 Hz (100%) → ~26.79 lpm

**Komenda do wysłania:**
```
K55 0.0 5.36 10.71 16.07 21.43 26.79
```

---

## Testowanie

### Krok 1: Wyślij pierwszą próbę (Metoda 1 - skalowanie)
```
K55 373.18 272.30 177.42 128.71 17.96 13.63
```

### Krok 2: Ustaw zakres Q1 na R5
```
r111151
```

### Krok 3: Dmuchnij w turbinę i sprawdź wartość

Jeśli wartość jest:
- ✅ **Bliska 0-27 lpm** → Sukces!
- ❌ **Dziwna (np. 4500)** → Spróbuj Metody 2 lub 3
- ❌ **Ujemna lub zero** → Odwróć kolejność parametrów

---

## Backup - cofnięcie zmian

Jeśli nic nie działa, przywróć domyślne:
```
q
we
```
(reset fabryczny wszystkich zakresów)

---

## Kolejne kroki po sukcesie

1. Zapisz działające parametry K55
2. W aplikacji ustaw P5 (Q1) na zakres R5 (Custom)
3. W RangeSettingsDialog ustaw maksymalną wartość: 27 lpm
4. Kliknij "Zapisz"

Gotowe! Turbina będzie działać z prawidłową kalibracją 0-27 lpm. 🎯
