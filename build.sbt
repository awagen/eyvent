import sbt.Keys._

val sl4jApiVersion = "2.0.9"
val scalaTestVersion = "3.2.15"
val scalaMockVersion = "5.2.0"
val shapelessVersion = "2.3.10"
val logbackVersion = "1.4.11"
val macwireVersion = "2.5.8"
val scalacScoverageRuntimeVersion = "1.4.9"
val testcontainersVersion = "1.17.6"
val sprayVersion = "1.3.6"
val zioVersion = "2.0.13"
val zioJsonVersion = "0.5.0"
val zioCacheVersion = "0.2.3"
val zioConfigVersion = "4.0.0-RC14"
val zioLoggingVersion = "2.1.12"
val zioHttpVersion = "3.0.0-RC2"
val zioMetricsConnectorsVersion = "2.0.8"
val mockitoVersion = "3.2.10.0"
val kolibriStorageVersion = "0.2.4"

ThisBuild / scalaVersion := "2.13.2"
ThisBuild / version := "0.0.1"
name := "eyvent"

// defining fixed env vars for test scope
Test / envVars := Map("PROFILE" -> "test")
IntegrationTest / envVars := Map("PROFILE" -> "test")

// Scala Compiler Options
ThisBuild / scalacOptions ++= Seq(
  "-encoding", "UTF-8",
  "-deprecation", // warning and location for usages of deprecated APIs
  "-feature", // warning and location for usages of features that should be imported explicitly
  "-unchecked", // additional warnings where generated code depends on assumptions
  "-Xlint", // recommended additional warnings
  "-Ywarn-value-discard", // Warn when non-Unit expression results are unused
  "-Ywarn-dead-code",
  "-language:postfixOps" // New lines for each options
)
//javacOptions
ThisBuild / javacOptions ++= Seq(
  "-source", "11",
  "-target", "11"
)

//by default run types run on same JVM as sbt. This might lead to crashing, thus we fork the JVM.
ThisBuild / Runtime / fork := true
ThisBuild / Test / fork := true
ThisBuild / IntegrationTest / fork := true
ThisBuild / run / fork := true

// with TrackIfMissing, sbt will not try to compile internal
// (inter-project) dependencies automatically if there are *.class files
// (or JAR file when exportJars is true) in output directory
ThisBuild / trackInternalDependencies := TrackLevel.TrackIfMissing

//logging
//disables buffered logging (buffering would cause results of tests to be logged only at end of all tests)
//http://www.scalatest.org/user_guide/using_scalatest_with_sbt
ThisBuild / Test / logBuffered := false
ThisBuild / IntegrationTest / logBuffered := false
//disable version conflict messages
ThisBuild / update / evictionWarningOptions := EvictionWarningOptions.default
  .withWarnTransitiveEvictions(false)
  .withWarnDirectEvictions(false)


test in assembly := {} //causes no tests to be executed when calling "sbt assembly" (without this setting executes all)
assemblyJarName in assembly := s"eyvent.${version.value}.jar" //jar name
//sbt-assembly settings. If it should only hold for specific subproject build, place the 'assemblyMergeStrategy in assembly' within subproject settings
assemblyMergeStrategy in assembly := {
  // picking last to make sure the application configs and logback config of kolibri-fleet-zio are picked up instead
  // from a dependency
  case x if "application.*\\.conf".r.findFirstMatchIn(x.split("/").last).nonEmpty =>
    MergeStrategy.last
  case x if x.endsWith("logback.xml") =>
    MergeStrategy.last
  case "module-info.class" => MergeStrategy.discard
  case PathList("META-INF", xs@_*) => MergeStrategy.discard
  case manifest if manifest.contains("MANIFEST.MF") =>
    // We don't need manifest files since sbt-assembly will create
    // one with the given settings
    MergeStrategy.discard
  case referenceOverrides if referenceOverrides.contains("reference-overrides.conf") =>
    // Keep the content for all reference-overrides.conf files
    MergeStrategy.concat
  case reference if reference.contains("reference.conf") =>
    // Keep the content for all reference.conf files
    MergeStrategy.concat
  case x if x.endsWith(".proto") =>
    MergeStrategy.first
  // same class conflicts (too general but resolving for now)
  case x if x.endsWith(".class") =>
    MergeStrategy.first
  case x =>
    // For all the other files, use the default sbt-assembly merge strategy
    val oldStrategy = (assembly / assemblyMergeStrategy).value
    oldStrategy(x)
}


val additionalResolvers: Seq[Resolver] = Seq(
  ("scalaz-bintray" at "https://dl.bintray.com/scalaz/releases").withAllowInsecureProtocol(false),
  Resolver.mavenLocal
) ++ Resolver.sonatypeOssRepos("releases") ++ Resolver.sonatypeOssRepos("snapshots")

val additionalDependencies = Seq(
  "org.slf4j" % "slf4j-api" % sl4jApiVersion,
  //scala test framework (scalactic is recommended but not required)(http://www.scalatest.org/install)
  "org.scalactic" %% "scalactic" % scalaTestVersion % Test,
  "org.scalatest" %% "scalatest" % scalaTestVersion % Test,
  "io.spray" %%  "spray-json" % sprayVersion,
  "ch.qos.logback" % "logback-classic" % logbackVersion,
  "com.chuusai" %% "shapeless" % shapelessVersion,
  "org.slf4j" % "slf4j-api" % sl4jApiVersion,
  "com.softwaremill.macwire" %% "macros" % macwireVersion,
  "com.softwaremill.macwire" %% "util" % macwireVersion,
  "org.scalamock" %% "scalamock" % scalaMockVersion % Test,
  "org.scoverage" %% "scalac-scoverage-runtime" % scalacScoverageRuntimeVersion % Test,
  "org.testcontainers" % "testcontainers" % testcontainersVersion % Test,
  "org.testcontainers" % "localstack" % testcontainersVersion % Test,
  "dev.zio" %% "zio" % zioVersion,
  "dev.zio" %% "zio-streams" % zioVersion,
  "dev.zio" %% "zio-concurrent" % zioVersion,
  "dev.zio" %% "zio-http" % zioHttpVersion,
  "dev.zio" %% "zio-json" % zioJsonVersion,
  "dev.zio" %% "zio-cache" % zioCacheVersion,
  "dev.zio" %% "zio-config" % zioConfigVersion,
  "dev.zio" %% "zio-config-typesafe" % zioConfigVersion,
  "dev.zio" %% "zio-config-magnolia" % zioConfigVersion,
  "dev.zio" %% "zio-logging-slf4j" % zioLoggingVersion,
  "dev.zio" %% "zio-http" % zioHttpVersion,
  "dev.zio" %% "zio-metrics-connectors" % zioMetricsConnectorsVersion,
  "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
  "dev.zio" %% "zio-test-magnolia" % zioVersion % Test,
  "org.scalatestplus" %% "mockito-3-4" % mockitoVersion % Test,
  "de.awagen.kolibri" %% "kolibri-storage" % kolibriStorageVersion
)

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

libraryDependencies ++= additionalDependencies
resolvers ++= additionalResolvers

assembly / mainClass := Some("de.awagen.eyvent.App")

// actual project definitions
// NOTE: do not additionally define the project definitions per single-project build.sbt file,
// otherwise changes from projects referenced in dependsOn here dont seem to be picked up from local
// but need local jar publishing
lazy val root = (project in file("."))
  .enablePlugins(JvmPlugin)
  // extending Test config here to have access to test classpath
  .configs(IntegrationTest.extend(Test))
  .settings(
    Defaults.itSettings
  )
