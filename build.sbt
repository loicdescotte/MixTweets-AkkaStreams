name := "MixTweets"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  ws,
  "org.twitter4j" % "twitter4j-core" % "4.0.2",
  "org.twitter4j" % "twitter4j-async" % "4.0.2",
  "org.twitter4j" % "twitter4j-stream" % "4.0.2",
  "org.twitter4j" % "twitter4j-media-support" % "4.0.2",
  "com.typesafe.akka" %% "akka-stream-experimental" % "1.0-RC3", 
  "com.typesafe.play" %% "play-streams-experimental" % "2.4.0"  
)     

lazy val root = (project in file(".")).enablePlugins(PlayScala)
