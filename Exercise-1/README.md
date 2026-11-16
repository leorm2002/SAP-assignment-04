# Smart Home Alarm — Pekko Cluster

## Avvio

Da `Exercise-1`:

Per `run`, porta e ruolo sono obbligatori; i ruoli ammessi sono `control`, `sensors` e `keypad`.

```sh
sbt "runMain run 7355 control"
sbt "runMain run 7356 sensors"
sbt "runMain HttpServer 7357"
```

Aprire <http://localhost:8080>. Inserire il PIN di `config.json` (1234) e premere
**Unarm** per uscire da **Recovery**. Poi usare normalmente Arm e i sensori.
Premere INVIO nel terminale per arrestare il relativo nodo.

- `control` ospita l'unica centralina `Alarm/home` e la sirena.
- `sensors` ospita i sensori, identificati dal campo originale `name`, usato come sensorId.
- `keypad` espone l'interfaccia; gli altri nodi mantengono anche il proprio tastierino locale.

Il programma originale resta disponibile sul nodo keypad:

```sh
sbt "runMain run 7357 keypad"
```

Ogni nuova centralina, anche dopo restart o ricreazione, entra in Recovery: eventi sensore e armamento sono ignorati; serve Unarm con PIN corretto.
Lo stato non viene persistito. Per riallocare la centralina dopo la perdita del nodo control, serve un altro nodo con lo stesso ruolo, ad esempio:

```sh
sbt "runMain run 7358 control"
```

Gli indirizzi seed sono in `src/main/resources/application.conf`.
