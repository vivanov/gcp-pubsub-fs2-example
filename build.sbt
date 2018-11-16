import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

val FS2Version = "1.0.0"
val Http4sVersion = "0.20.0-M3"
val Specs2Version = "4.1.0"
val LogbackVersion = "1.2.3"
val GoogleCloudPubSubVersion = "1.48.0"
val ScalaJSReactVersion = "1.3.1"
val ScalaJsDomVersion = "0.9.6"
val PureConfigVersion = "0.10.0"
val Log4CatsVersion = "0.1.0"

val ReactVersion = "16.5.1"

lazy val compilerFlags = Seq(
  scalacOptions ++= Seq(
    "-deprecation",                      // Emit warning and location for usages of deprecated APIs.
    "-encoding", "utf-8",                // Specify character encoding used by source files.
    "-explaintypes",                     // Explain type errors in more detail.
    "-feature",                          // Emit warning and location for usages of features that should be imported explicitly.
    "-language:existentials",            // Existential types (besides wildcard types) can be written and inferred
    "-language:experimental.macros",     // Allow macro definition (besides implementation and application)
    "-language:higherKinds",             // Allow higher-kinded types
    "-language:implicitConversions",     // Allow definition of implicit functions called views
    "-unchecked",                        // Enable additional warnings where generated code depends on assumptions.
    "-Xcheckinit",                       // Wrap field accessors to throw an exception on uninitialized access.
    "-Xfuture",                          // Turn on future language features.
    "-Xlint:adapted-args",               // Warn if an argument list is modified to match the receiver.
    "-Xlint:by-name-right-associative",  // By-name parameter of right associative operator.
    "-Xlint:constant",                   // Evaluation of a constant arithmetic expression results in an error.
    "-Xlint:delayedinit-select",         // Selecting member of DelayedInit.
    "-Xlint:doc-detached",               // A Scaladoc comment appears to be detached from its element.
    "-Xlint:inaccessible",               // Warn about inaccessible types in method signatures.
    "-Xlint:infer-any",                  // Warn when a type argument is inferred to be `Any`.
    "-Xlint:missing-interpolator",       // A string literal appears to be missing an interpolator id.
    "-Xlint:nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
    "-Xlint:nullary-unit",               // Warn when nullary methods return Unit.
    "-Xlint:option-implicit",            // Option.apply used implicit view.
    "-Xlint:package-object-classes",     // Class or object defined in package object.
    "-Xlint:poly-implicit-overload",     // Parameterized overloaded implicit methods are not visible as view bounds.
    "-Xlint:private-shadow",             // A private field (or class parameter) shadows a superclass field.
    "-Xlint:stars-align",                // Pattern sequence wildcard must align with sequence component.
    "-Xlint:type-parameter-shadow",      // A local type parameter shadows a type already in scope.
    "-Xlint:unsound-match",              // Pattern match may not be typesafe.
    "-Yrangepos",                        // Report Range Position of Errors to Language Server
    "-Ywarn-dead-code",                  // Warn when dead code is identified.
    "-Ywarn-extra-implicit",             // Warn when more than one implicit parameter section is defined.
    "-Ywarn-inaccessible",               // Warn about inaccessible types in method signatures.
    "-Ywarn-infer-any",                  // Warn when a type argument is inferred to be `Any`.
    "-Ywarn-nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
    "-Ywarn-nullary-unit",               // Warn when nullary methods return Unit.
    "-Ywarn-numeric-widen",              // Warn when numerics are widened.
    "-Ywarn-unused:implicits",           // Warn if an implicit parameter is unused.
    "-Ywarn-unused:imports",             // Warn if an import selector is not referenced.
    "-Ywarn-unused:locals",              // Warn if a local definition is unused.
    "-Ywarn-unused:params",              // Warn if a value parameter is unused.
    "-Ywarn-unused:patvars",             // Warn if a variable bound in a pattern is unused.
    "-Ywarn-unused:privates",            // Warn if a private member is unused.
    "-Ywarn-value-discard"               // Warn when non-Unit expression results are unused.
  ),
  scalacOptions in (Test, compile) --= Seq(
    "-Ywarn-unused:privates",
    "-Ywarn-unused:locals",
    "-Ywarn-unused:imports",
    "-Yno-imports"
  ),
  scalacOptions in (Compile, console) --= Seq("-Xfatal-warnings", "-Ywarn-unused:imports", "-Yno-imports")
)

lazy val sharedSettings = 
  compilerFlags ++ Seq(
  publishArtifact := false,
  libraryDependencies ++= Seq(
    "io.chrisdavenport" %% "log4cats-core"    % Log4CatsVersion,
    "io.chrisdavenport" %% "log4cats-slf4j"   % Log4CatsVersion,
    "ch.qos.logback"    %  "logback-classic"  % LogbackVersion,
    "com.github.pureconfig" %% "pureconfig"    % PureConfigVersion,
    "com.github.pureconfig" %% "pureconfig-cats-effect" % PureConfigVersion
  )
)

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
    // Add settings common both for JVM and JS here
  ).
  jvmSettings(sharedSettings: _*).
  jvmSettings(
    // Add JVM-specific settings here
      libraryDependencies ++= Seq(
        "co.fs2" %% "fs2-core" % FS2Version,
        "com.google.cloud" % "google-cloud-pubsub" % GoogleCloudPubSubVersion,
      )
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
        "org.scala-js" %%% "scalajs-dom" % ScalaJsDomVersion,
        "com.github.japgolly.scalajs-react" %%% "core" % ScalaJSReactVersion,
        "com.github.japgolly.scalajs-react" %%% "extra" % ScalaJSReactVersion
      ),
      npmDependencies in Compile ++= Seq(
        "react" -> ReactVersion,
        "react-dom" -> ReactVersion)
      )
    .enablePlugins(ScalaJSPlugin)
    .enablePlugins(ScalaJSBundlerPlugin)
    .dependsOn(`common-js`)
                
lazy val `subscriber-server` =
  project
    .in(sbt file s"subscriber/subscriber-server")
    .settings(sharedSettings: _*)
    .settings(
      libraryDependencies ++= Seq(
        "org.http4s"      %% "http4s-blaze-server" % Http4sVersion,
        "org.http4s"      %% "http4s-circe"        % Http4sVersion,
        "org.http4s"      %% "http4s-dsl"          % Http4sVersion,
        "org.http4s"      %% "http4s-twirl"        % Http4sVersion,
        "org.specs2"      %% "specs2-core"         % Specs2Version % "test"
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
      mainClass in reStart := Some("org.vilvaadn.pubsubexample.PubSubExampleServer"),
      addCompilerPlugin("org.spire-math" %% "kind-projector"     % "0.9.6"),
      addCompilerPlugin("com.olegpy"     %% "better-monadic-for" % "0.2.4")
    )
    .dependsOn(`common-jvm`)
    .enablePlugins(SbtTwirl)

lazy val publisher =
  project
    .settings(sharedSettings: _*)
    .settings(
      libraryDependencies ++= Seq(
        "co.fs2" %% "fs2-core" % FS2Version
      )
    )
    .dependsOn(`common-jvm`)
