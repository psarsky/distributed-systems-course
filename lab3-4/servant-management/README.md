# Zadanie I2 - Efektywne zarządzanie serwantami

## Serwer

```bash
docker compose up --build server
```
`--build` wymusza zbudowanie image przy każdym uruchomieniu

## Klient

W drugim terminalu:

```bash
docker compose run --rm --build client
```
`--rm` automatycznie usuwa kontener po zakończeniu
`--build` jak wyżej
