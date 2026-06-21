# Zadanie A1 - "Inteligentne" otoczenie

## Serwery (dwa piętra)

```bash
docker compose up --build server1 server2
```
`--build` wymusza zbudowanie image przy każdym uruchomieniu

W logach widać wystartowane urządzenia oraz - na bieżąco - obsługiwane zadania.

## Klient

W drugim terminalu:

```bash
docker compose run --rm --build client
```
`--rm` automatycznie usuwa kontener po zakończeniu

## Analiza ruchu sieciowego (opcjonalnie)

Serwery wystawiaja porty `50051` (Floor-1) i `50052` (Floor-2) na hoscie -
mozna podejrzec HTTP/2 + protobuf np. w Wiresharku (filtr `tcp.port==50051`).
