# Distributed Tic Tac Toe

## Requisiti

- JDK **26**


## Build e avvio

```powershell
mvn -f .\dttt\pom.xml clean install
```

I file compilati e i JAR vengono prodotti nelle directory `target` dei moduli.
`install` rende disponibili le dipendenze fra moduli per i successivi avvii.
In tre terminali separati, sempre dalla cartella `Excercise-2`, eseguire:

```powershell
# Server
mvn -f .\dttt\pom.xml -pl server/gameengine exec:exec

# Giocatore 1
mvn -f .\dttt\pom.xml -pl client/game exec:exec

# Giocatore 2
mvn -f .\dttt\pom.xml -pl client/game exec:exec
```

Attendere il messaggio `Server ready` prima di avviare entrambi i client.