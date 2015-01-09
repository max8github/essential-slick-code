name := "essential-slick-example"

version := "1.0"

scalaVersion := "2.11.4"

libraryDependencies += "com.typesafe.slick" %% "slick" % "2.1.0"

libraryDependencies += "org.postgresql" % "postgresql" % "9.3-1101-jdbc41"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.2"

libraryDependencies += "joda-time" % "joda-time" % "2.6"

libraryDependencies += "org.joda" % "joda-convert" % "1.2"

initialCommands in console := """
import scala.slick.driver.PostgresDriver.simple._
val db = Database.forURL("jdbc:postgresql:essential-slick", user="essential", password="trustno1", driver = "org.postgresql.Driver")
implicit val session = db.createSession
println("\nSession created, but you may want to also import a schema. For example:\n\n    import io.underscore.slick.ExerciseTwo._\n or import underscoreio.schema.Example5.Tables._\n")
//
"""