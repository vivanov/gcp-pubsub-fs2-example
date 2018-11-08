import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

val FS2Version = "0.10.6"
val Http4sVersion = "0.18.19"
val Specs2Version = "4.1.0"
val LogbackVersion = "1.2.3"
val GoogleCloudPubSubVersion = "1.48.0"
val ScalaJSReactVersion = "1.3.1"
val scalaJsDomVersion = "0.9.6"

lazy val root = project 
  .in(sbt file ".")
  .aggregate(`common-jvm`, `common-js`, publisher, subscriber)
  .settings(
    organization := "org.vilvaadn",
    name := "pubsub-example",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.12.6"
  )

lazy val common = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(
    // Add common settings here
  ).
  jvmSettings(
    // Add JVM-specific settings here
  ).
  jsSettings(
    // Add JS-specific settings here
  )

lazy val `common-jvm` = common.jvm
lazy val `common-js` = common.js

lazy val subscriber = 
  project
    .aggregate(`subscriber-client`, `subscriber-server`)
             
lazy val `subscriber-client` = 
  project
    .in(sbt file s"subscriber/subscriber-client")
    .settings(
      scalaJSUseMainModuleInitializer := true,
      // Build a js dependencies file
      skip in packageJSDependencies := false,
      jsEnv := new org.scalajs.jsenv.nodejs.NodeJSEnv(),
      // Put the jsdeps file on a place reachable for the server
      crossTarget in (Compile, packageJSDependencies) := (resourceManaged in Compile).value,
      libraryDependencies ++= Seq(
        "org.scala-js" %%% "scalajs-dom" % scalaJsDomVersion,
        "com.github.japgolly.scalajs-react" %%% "core" % ScalaJSReactVersion,
        "com.github.japgolly.scalajs-react" %%% "extra" % ScalaJSReactVersion
      ),
      npmDependencies in Compile ++= Seq(
        "react" -> "16.5.1",
        "react-dom" -> "16.5.1")
      )
    .enablePlugins(ScalaJSPlugin)
    .enablePlugins(ScalaJSBundlerPlugin)
    .dependsOn(`common-js`)
                
lazy val `subscriber-server` =
  project
    .in(sbt file s"subscriber/subscriber-server")
    .settings(
      libraryDependencies ++= Seq(
        "org.http4s"      %% "http4s-blaze-server" % Http4sVersion,
        "org.http4s"      %% "http4s-circe"        % Http4sVersion,
        "org.http4s"      %% "http4s-dsl"          % Http4sVersion,
        "org.http4s"      %% "http4s-twirl" % Http4sVersion,
        "com.google.cloud" % "google-cloud-pubsub" % GoogleCloudPubSubVersion,
        "org.specs2"      %% "specs2-core"          % Specs2Version % "test",
        "ch.qos.logback"  %  "logback-classic"     % LogbackVersion
      ),
      // Allows to read the generated JS on client (-bundle.js and -bundle.js.map files)
      resources in Compile ++= (webpack in (`subscriber-client`, Compile, fastOptJS)).value
        .map ((x: sbt.Attributed[sbt.File]) => x.data),
      // Lets the server read the jsdeps file
      (managedResources in Compile) += (artifactPath in (`subscriber-client`, Compile, packageJSDependencies)).value,
      // do a fastOptJS on reStart
      reStart := (reStart dependsOn (webpack in (`subscriber-client`, Compile, fastOptJS))).evaluated,
      // This settings makes reStart to rebuild if a scala.js file changes on the client
      watchSources ++= (watchSources in `subscriber-client`).value,
      // Support stopping the running server
      mainClass in reStart := Some("cota.pubsubexample.PubSubExampleServer"),
      addCompilerPlugin("org.spire-math" %% "kind-projector"     % "0.9.6"),
      addCompilerPlugin("com.olegpy"     %% "better-monadic-for" % "0.2.4")
    )
    .dependsOn(`common-jvm`)

lazy val publisher =
  project
    .settings(
      libraryDependencies ++= Seq(
        "co.fs2" %% "fs2-core" % FS2Version,
        "com.google.cloud" % "google-cloud-pubsub" % GoogleCloudPubSubVersion
      ),
    )
