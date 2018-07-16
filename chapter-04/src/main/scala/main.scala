import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import slick.jdbc.H2Profile.api._

import scala.util.{Failure, Success, Try}

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
  val db = Database.forConfig("chapter04")

  // Helper method for running a query in this example file:
  def exec[T](program: DBIO[T]): T =
    Await.result(db.run(program), 5000 milliseconds)

  def testData = Seq(
    Message("Dave", "Hello, HAL. Do you read me, HAL?"),
    Message("HAL",  "Affirmative, Dave. I read you."),
    Message("Dave", "Open the pod bay doors, HAL."),
    Message("HAL",  "I'm sorry, Dave. I'm afraid I can't do that."))

  def populate =
    DBIO.seq(
      messages.schema.create,
      messages ++= testData
    )

  // Utility to print out what is in the database:
  def printCurrentDatabaseState() = {
    println("\nState of the database:")
    exec(messages.result.map(_.foreach(println)))
  }

  def reverse(msg: Message): DBIO[Int] =
    messages.filter(_.id === msg.id).
      map(_.content).
      update(msg.content.reverse)


  try {
    exec(populate)

    // Map:
    val textAction: DBIO[Option[String]] = messages.map(_.content).result.headOption

    val encrypted: DBIO[Option[String]] = textAction.map(maybeText => maybeText.map(_.reverse))

    println("\nAn 'encrypted' message from the database:")
    println(exec(encrypted))

    //DBIO.sequence
    val updates: DBIO[Seq[DBIO[Int]]] = messages.result.map(msgs => msgs.map(reverse))
    val updates2: DBIO[Seq[Int]] = messages.result.flatMap(msgs => DBIO.sequence(msgs.map(reverse)))


    // FlatMap:
    val delete: DBIO[Int] = messages.delete
    def insert(count: Int) = messages += Message("NOBODY", s"I removed ${count} messages")

    val resetMessagesAction: DBIO[Int] = delete.flatMap{ count => insert(count) }

    val resetMessagesAction2: DBIO[Int] =
      delete.flatMap{
        case 0 | 1 => DBIO.successful(0)
        case n     => insert(n)
      }


    // Fold:
    val report1: DBIO[Int] = DBIO.successful(41)
    val report2: DBIO[Int] = DBIO.successful(1)
    val reports: List[DBIO[Int]] = report1 :: report2 :: Nil

    val summary: DBIO[Int] = DBIO.fold(reports, 0) {
      (total, report) => total + report
    }

    println("\nSummary of all reports via fold:")
    println(exec(summary))

    // Zip
    val countAndHal: DBIO[(Int, Seq[Message])] =
      messages.size.result zip messages.filter(_.sender === "HAL").result
    println("\nZipped actions:")
    println(exec(countAndHal))

    //
    // Transactions
    //

    val willRollback = (
      (messages += Message("HAL",  "Daisy, Daisy..."))                   >>
      (messages += Message("Dave", "Please, anything but your singing")) >>
       DBIO.failed(new Exception("agggh my ears"))                       >>
      (messages += Message("HAL", "Give me your answer do"))
      ).transactionally

    println("\nResult from rolling back:")
    println(exec(willRollback.asTry))
    printCurrentDatabaseState



    //Exercises
    val drop: DBIO[Unit] = messages.schema.drop
    val create: DBIO[Unit] = messages.schema.create
    val seed: DBIO[Option[Int]] = messages ++= testData
    val resetDb = drop andThen create andThen seed
    exec(resetDb)
    printCurrentDatabaseState

    //First!
    def insertF(m: Message): DBIO[Int] = {
      messages.result.headOption.flatMap {
        case None => messages += (m.copy(content = s"First! ${m.content}"))
        case _ => messages += m
      }
    }

    //Only one
    println("------------------- Only one -----")
    def onlyOne[T](action: DBIO[Seq[T]]): DBIO[T] = action.flatMap {
        case seq if seq.size == 1 => action.map(seq => seq.head)
        case _=> DBIO.failed(new RuntimeException("more than one"))
    }
    val happy = messages.filter(_.content like "%sorry%").result
    val boom = messages.filter(_.content like "%I%").result
    val out = exec(onlyOne(happy))
    println(s"only one ok: $out")
//    val outBoom = exec(onlyOne(boom))
//    println(s"only one ok: $outBoom")

    //Exactly one
    println("------------------- Exactly one -----")
    def exactlyOne[T](action: DBIO[Seq[T]]): DBIO[Try[T]] = action.flatMap {
        case seq if seq.size == 1 => action.map(seq => seq.head)
        case _=> DBIO.failed(new RuntimeException("more than one"))
    }.asTry
    val outS = exec(exactlyOne(happy))
    println(s"only one ok: $outS")
    val outF = exec(exactlyOne(boom))
    println(s"only one ok: $outF")

    //Filter
    println("------------------- Filter -----")
    def myFilter[T](action: DBIO[T])(p: T => Boolean)(alternative: => T) = {
      val t = action.filter(p).asTry
      t.map {
        case Success(s) => s
        case Failure(f) => alternative
      }
    }
    val myf = myFilter(messages.size.result)( _ > 100)(100)
    println(s"filter: $myf")

    //Unfold
    println("------------------- Unfold -----")
    final case class Room(name: String, connectsTo: String)

    // defined class Room
    final class FloorPlan(tag: Tag) extends Table[Room](tag, "floorplan") {
      def name = column[String]("name")

      def connectsTo = column[String]("next")

      def * = (name, connectsTo).mapTo[Room]
    }

    lazy val floorplan = TableQuery[FloorPlan]
    // floorplan: slick.lifted.TableQuery[FloorPlan] = <lazy>
    Example.exec {
      (floorplan.schema.create) >>
        (floorplan += Room("Outside", "Podbay Door")) >>
        (floorplan += Room("Podbay Door", "Podbay")) >>
        (floorplan += Room("Podbay", "Galley")) >>
        (floorplan += Room("Galley", "Computer")) >>
        (floorplan += Room("Computer", "Engine Room"))
    }

    def unfold(z: String, f: String => DBIO[Option[String]], acc: Seq[String] = Seq.empty): DBIO[Seq[String]] = {
      ???
    }

    println(s"filter: $myf")

  } finally db.close

}
