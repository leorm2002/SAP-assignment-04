val scala3Version = "3.8.4"
val PekkoVersion = "1.1.2"
val PekkoHttpVersion = "1.1.0"

// Abilita SemanticDB
ThisBuild / semanticdbEnabled := true

scalacOptions ++= Seq(
  "-no-indent", // Disables the braceless indentation syntax
  "-old-syntax" // Enforces Scala 2 style control structures (like 'if (cond)' instead of 'if cond then')
  // "-Xfatal-warnings" // Optional: Turns syntax warnings into compilation errors
)
lazy val root = project
  .in(file("."))
  .settings(
    name := "es1",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,
    Compile / run / fork := true,
    Compile / run / connectInput := true,

    libraryDependencies ++= Seq(
      // Framework di Test
      "org.scalatest" %% "scalatest" % "3.2.20" % Test,

      // Apache Pekko (Attori)
      "org.apache.pekko" %% "pekko-actor-typed" % PekkoVersion,
      "org.apache.pekko" %% "pekko-actor-testkit-typed" % PekkoVersion % Test,
      "org.apache.pekko" %% "pekko-stream" % PekkoVersion,
      "org.apache.pekko" %% "pekko-http" % PekkoHttpVersion,
      "org.apache.pekko" %% "pekko-http-spray-json" % PekkoHttpVersion,
      "org.apache.pekko" %% "pekko-cluster-typed" % PekkoVersion,
      "org.apache.pekko" %% "pekko-cluster-sharding-typed" % PekkoVersion,
      "org.apache.pekko" %% "pekko-serialization-jackson" % PekkoVersion,
      // Logging
      "ch.qos.logback" % "logback-classic" % "1.5.37",

      // Lettura files
      "com.lihaoyi" %% "os-lib" % "0.11.3",
      // Deserializzazione json
      "com.lihaoyi" %% "upickle" % "4.0.2"
    )
  )
