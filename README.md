# elev-api

Inngangen til elev-systemet. En Spring Boot-tjeneste i Kotlin som tar imot forespørsler fra klienten, sender nye elever til **Kafka** og henter elever fra [elev-service](https://github.com/VadimAndroid/elev-service) over **HTTP**.

`elev-api` har **ingen database** og lagrer ingenting selv.

## Rolle i systemet

Systemet består av to **mikrotjenester** i hvert sitt repository:

| Tjeneste | Repository | Port | Ansvar |
|---|---|---|---|
| **elev-api** | dette | 8080 | Eneste inngang for klienten. Validerer, genererer ID, sender til Kafka, henter fra `elev-service`. |
| elev-service | [VadimAndroid/elev-service](https://github.com/VadimAndroid/elev-service) | 8081 | Leser elever fra Kafka og lagrer dem i en database. Tilbyr GET-endepunkter. |
| Kafka | `docker-compose.yaml` i dette repoet | 9092 | Meldingskø mellom tjenestene. |

```
Opprette elev (asynkront):
Klient ──HTTP POST──▶ elev-api ──Kafka (topic "elever")──▶ elev-service ──▶ database

Hente elever (synkront):
Klient ──HTTP GET───▶ elev-api ──HTTP GET──▶ elev-service ──▶ database
```

Tjenestene deler **ingen kode**. Hver har sin egen `Elev`-klasse. Det eneste som binder dem sammen, er **kontrakten** under.

## Kontrakt med elev-service

### Kafka (elev-api sender)

| | |
|---|---|
| Topic | `elever` |
| Nøkkel | elevens ID (UUID som tekst) |
| Verdi | JSON: `{ "id": "<uuid>", "navn": "<tekst>", "klasse": "<tekst>" }` |

### HTTP (elev-api kaller)

| Kall | Forventet svar fra elev-service |
|---|---|
| `GET {elev.service.url}/elever` | `200` med JSON-liste |
| `GET {elev.service.url}/elever/{id}` | `200` med eleven, eller `404` |

## Endepunkter

| Metode | Sti | Svar |
|---|---|---|
| POST | `/elever` | `202` med eleven (inkl. ID) · `400` ved tomt navn/klasse · `503` hvis Kafka feiler |
| GET | `/elever` | `200` med liste (eventuelt `[]`) · `503` hvis `elev-service` er nede |
| GET | `/elever/{id}` | `200` med eleven · `404` hvis den ikke finnes · `503` hvis `elev-service` er nede |

Eksempel på body for POST:

```json
{ "navn": "Ola Nordmann", "klasse": "10A" }
```

## Kafka vs. HTTP

**Opprettelse går over Kafka (asynkront).** `POST /elever` validerer `navn` og `klasse`, genererer en unik ID og publiserer eleven som JSON på topicen `elever`, med ID-en som meldingsnøkkel. `elev-api` svarer først når Kafka har bekreftet mottak. `elev-api` trenger ikke at `elev-service` kjører akkurat nå – meldingen venter i Kafka.

Derfor svarer POST med **202 Accepted** og ikke 201 Created: eleven er sendt til behandling, men ikke nødvendigvis lagret ennå. En GET rett etter POST kan gi 404 et kort øyeblikk.

**Henting går over HTTP (synkront).** Klienten trenger svaret med en gang. Hvis `elev-service` svarer 404, svarer `elev-api` også 404. Hvis `elev-service` ikke svarer, returnerer `elev-api` **503 Service Unavailable** – ikke en tom liste, fordi en tom liste ville vært feil informasjon.

## Konfigurasjon

`src/main/resources/application.properties`:

```properties
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.producer.properties.max.block.ms=5000
elev.kafka.topic=elever
elev.service.url=http://localhost:8081
```

`max.block.ms=5000` gjør at POST feiler etter 5 sekunder (og gir 503) hvis Kafka ikke svarer, i stedet for å vente i 60 sekunder.

## Kjøre

**Forutsetninger:** Java 25, Docker Desktop.

**1. Start Kafka** (fra dette repoet):

```bash
docker compose up -d
docker ps          # kafka skal ha status "Up"
```

**2. Start elev-service** – se README i [elev-service](https://github.com/VadimAndroid/elev-service). Uten den fungerer POST, men GET gir 503.

**3. Start elev-api:**

```bash
./gradlew bootRun
```

eller ▶ ved `fun main` i `ElevApiApplication.kt` i IntelliJ.

**Stoppe:** `Ctrl + C`, og `docker compose down` for Kafka.

## Teste

- **IntelliJ:** åpne `requests.http` og trykk ▶ ved hver forespørsel, ovenfra og ned.
- **Enhetstester:** `./gradlew test`

## Struktur

```
src/main/kotlin/com/example/elev_api/
├── ElevApiApplication.kt
├── client/ElevServiceClient.kt   – HTTP-kall til elev-service (RestClient)
├── controller/ElevController.kt  – endepunktene, 202 / 404 / 503
├── kafka/ElevProducer.kt         – sender eleven til Kafka og venter på bekreftelse
├── model/Elev.kt
├── model/OpprettElevRequest.kt   – det klienten sender inn (@NotBlank)
└── service/ElevService.kt        – lager UUID, trimmer, velger Kafka eller HTTP
```
