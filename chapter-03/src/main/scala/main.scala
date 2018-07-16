import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import slick.jdbc.H2Profile.api._
import scala.util.Try

object Example extends App {

  // Row representation:
  final case class Message(sender: String, content: String, id: Long = 0L)

  // Schema:
  final class MessageTable(tag: Tag) extends Table[Message](tag, "message") {
    def id      = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def sender  = column[String]("sender")
    def content = column[String]("content")
    def * = (sender, content, id).mapTo[Message]
  }

  // Table:
  lazy val messages = TableQuery[MessageTable]

  // Database connection details:
  val db = Database.forConfig("chapter03")

  // Helper method for running a query in this example file:
  def exec[T](program: DBIO[T]): T = Await.result(db.run(program), 5000 milliseconds)


  def testData = Seq(
    Message("Dave", "Hello, HAL. Do you read me, HAL?"),
    Message("HAL",  "Affirmative, Dave. I read you."),
    Message("Dave", "Open the pod bay doors, HAL."),
    Message("HAL",  "I'm sorry, Dave. I'm afraid I can't do that."))

  def populate: DBIOAction[Option[Int], NoStream,Effect.All] =  {
    for {
      _ <- messages.schema.drop.asTry andThen messages.schema.create //Drop table if it already exists, then create the table:
      count <- messages ++= testData
    } yield count
  }

  // Utility to print out what is in the database:
  def printCurrentDatabaseState(msg: String*) = {
    val m = msg.mkString
    println(s"\nState of the database $m:")
    exec(messages.result.map(_.foreach(println)))
  }

  try {
    exec(populate)

    // -- INSERTS --

    // Insert one, returning the ID:
    val id = exec((messages returning messages.map(_.id)) += Message("HAL", "I'm back"))
    println(s"The ID inserted was: $id")

    // -- DELETES --

    // Delete messages from HAL:
    println("\nDeleting messages from HAL:")
    val rowsDeleted = exec(messages.filter(_.sender === "HAL").delete)
    println(s"Rows deleted: $rowsDeleted")

    // Repopulate the database:
    exec( messages ++= testData.filter(_.sender == "HAL") )

    printCurrentDatabaseState()

    // -- UPDATES --

    // Update HAL's name:
    val rows = exec(messages.filter(_.sender === "HAL").map(_.sender).update("HAL 9000"))

    // Update HAL's name and message:
    val query =
      messages.
        filter(_.id === 4L).
        map(message => (message.sender, message.content))

    val rowsAffected  = exec(query.update(("HAL 9000", "Sure, Dave. Come right in.")))
    printCurrentDatabaseState("------------------------")

    //UPSERT
    val upser = messages.insertOrUpdate(Message("Max", "So sleepy...", 8L))
    exec(upser)
    printCurrentDatabaseState("------------------------")
    val upser2 = messages.insertOrUpdate(Message("Max", "Movie tonite?", 8L))
    exec(upser2)
    printCurrentDatabaseState("------------------------")

    // Action for updating multiple fields:
    exec {
      messages.
        filter(_.id === 4L).
        map(message => (message.sender, message.content)).
        update(("HAL 9000", "Sure, Dave. Come right in."))
      }

    // Client-side update:
    def exclaim(msg: Message): Message = msg.copy(content = msg.content + "!")

    val all: DBIO[Seq[Message]] = messages.result
    def modify(msg: Message): DBIO[Int] = messages.filter(_.id === msg.id).update(exclaim(msg))
    val action: DBIO[Seq[Int]] = all.flatMap( msgs => DBIO.sequence(msgs.map(modify)) )
    val rowCounts: Seq[Int] = exec(action)


    //------
    val conversation = List(
      Message("Bob", "Hi Alice"),
      Message("Alice","Hi Bob"),
      Message("Bob", "Are you sure this is secure?"),
      Message("Alice","Totally, why do you ask?"),
      Message("Bob", "Oh, nothing, just wondering."),
      Message("Alice","Ten was too many messages"),
      Message("Bob", "I could do with a sleep"),
      Message("Alice","Let's just to to the point"),
      Message("Bob", "Okay okay, no need to be tetchy."),
      Message("Alice","Humph!"))

    val allDelete = exec(messages.delete)
    println(s"All rows deleted: $rowsDeleted")
    exec(messages.result) foreach println
    exec(populate)

    //---
    println("\n//------------ msg inserts back:")
    val ten = messages returning messages.map(_.id) into{ (message, id) => message.copy(id = id)}
    val insertTen = ten ++= conversation
    val idsTen = exec(insertTen)
    idsTen foreach println
    println("\n//------------ All now:")
    exec(messages.result) foreach println

    //---
    val sorrys = messages.filter(_.content like "%sorry%")
    val sorryDeletes = exec(sorrys.delete)
    println(s"\n//------------ Sorry deletes were $sorryDeletes:")
    println("\n//------------ All now:")
    exec(messages.result) foreach println

    //---
    val ups = for {
      m <- messages if m.sender == "HAL"
    } yield (m.sender, m.content)
    println("\n//------------ For expr update:")
    exec(ups.update(("HAL", "Hi")))

    //---
    exec(populate)
    val firstTwo = messages.filter{_.id in messages.filter(_.sender === "HAL").sortBy(_.id.asc).map(_.id).take(2)}
    println("\n//------------ Delete first two HAL msges. Before:")
    exec(messages.filter(_.sender === "HAL").result) foreach println
    println("\n//---------- After:")
    exec(firstTwo.delete)
    exec(messages.filter(_.sender === "HAL").result) foreach println


  } finally db.close

}
