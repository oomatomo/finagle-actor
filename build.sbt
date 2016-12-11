name := "finagle-actor"

organization := "com.oomatomo"

version := "0.0.1"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "com.twitter" %% "finagle-http" % "6.35.0",
  "com.twitter" %% "finagle-redis" % "6.35.0",
  "joda-time" %  "joda-time" % "2.7",
  "org.joda" %  "joda-convert" % "1.7",
  "com.typesafe.akka" % "akka-actor_2.11" % "2.4.14",
  "com.typesafe.akka" % "akka-slf4j_2.11" % "2.4.14",
  "ch.qos.logback" % "logback-classic" % "1.1.3",
  "net.debasishg" %% "redisclient" % "3.0",
  "org.scalatest" %% "scalatest" % "2.2.6" % "test"
)

resolvers ++= Seq("snapshots", "releases").map(Resolver.sonatypeRepo)
resolvers += "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/"

initialCommands := "import com.oomatomo.finagle._"

// scalariform
import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

SbtScalariform.scalariformSettings
ScalariformKeys.preferences := FormattingPreferences()
  .setPreference(AlignParameters, false)
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 40)
  .setPreference(CompactControlReadability, false)
  .setPreference(CompactStringConcatenation, false)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(FormatXml, true)
  .setPreference(IndentLocalDefs, false)
  .setPreference(IndentPackageBlocks, true)
  .setPreference(IndentSpaces, 2)
  .setPreference(IndentWithTabs, false)
  .setPreference(MultilineScaladocCommentsStartOnFirstLine, false)
  .setPreference(PlaceScaladocAsterisksBeneathSecondAsterisk, false)
  .setPreference(PreserveSpaceBeforeArguments, false)
  .setPreference(RewriteArrowSymbols, false)
  .setPreference(SpaceBeforeColon, false)
  .setPreference(SpaceInsideBrackets, false)
  .setPreference(SpaceInsideParentheses, false)
  .setPreference(SpacesWithinPatternBinders, true)