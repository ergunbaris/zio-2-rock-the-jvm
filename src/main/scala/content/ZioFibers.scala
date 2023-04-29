package content

import content.ZioFibers.printThread
import zio.{Exit, Scope, UIO, ZIO, ZIOAppArgs, durationInt}

object ZioFibers extends zio.ZIOAppDefault{
  // effect pattern
  // computation = value + an effect in the world
  // substitution model referential transparency
  // effect library to encapsulate value and side effects

  // IO Monad = ZIO => ZIO[R-, E+, A+]
  val zmol: UIO[Int] = ZIO.succeed(42) // ZIO[Any, Nothing, Int]

  // concurrency = daily routine of Bob
  val showerTime = ZIO.succeed("Taking a shower")
  val boilingWater = ZIO.succeed("Boiling some water")
  val preparingCoffee = ZIO.succeed("Prepare some coffee")

  def printThread = s"[${Thread.currentThread.getName}]"

  def syncRoutine() = for {
    _ <- showerTime.debug(printThread)
    _ <- boilingWater.debug(printThread)
    _ <- preparingCoffee.debug(printThread)
  } yield ()

  // fiber = schedulable computation
  // Fiber[E, A]

  def concurrentShowerWhileBoilingWater() = for {
    _ <- showerTime.debug(printThread).fork
    _ <- boilingWater.debug(printThread)
    _ <- preparingCoffee.debug(printThread)
  } yield ()

  def concurrentRoutine() = for {
    showerFibre <- showerTime.debug(printThread).fork
    boilingWaterFibre <- boilingWater.debug(printThread).fork
    zippedFiber = showerFibre.zip(boilingWaterFibre)
    result <- zippedFiber.join.debug(printThread)
    _ <- ZIO.succeed(s"$result done").debug(printThread) *> preparingCoffee.debug(printThread)
  } yield ()

  val getPhoneCall = ZIO.succeed("Call from Alice")
  val boilingWaterWithTime = boilingWater.debug(printThread) *> ZIO.sleep(5.seconds) *> ZIO.succeed("Boiled water ready!")

  def concurrentCallWithAliceCall() = for {
    _ <- showerTime.debug(printThread)
    boilingFiber <- boilingWaterWithTime.fork
    _ <- getPhoneCall.debug(printThread).fork *> ZIO.sleep(3.seconds) *> boilingFiber.interrupt.debug(printThread)
    _ <- ZIO.succeed("Screw my coffee, going with Alice").debug(printThread)
  } yield ()

  val preparingCoffeeWithTime = preparingCoffee.debug(printThread) *> ZIO.sleep(5.seconds) *> ZIO.succeed("Coffee ready")

  def concurrentCallWithCoffeeAtHome() = for {
    _ <- showerTime.debug(printThread)
    _ <- boilingWater.debug(printThread)
    coffeeFiber <- preparingCoffeeWithTime.debug(printThread).fork.uninterruptible
    result <- getPhoneCall.debug(printThread).fork *> coffeeFiber.interrupt.debug(printThread)
    _ <- result match {
      case Exit.Success(value) => ZIO.succeed("Sorry Alice making breakfast at home").debug(printThread)
      case Exit.Failure(cause) => ZIO.succeed("Going to a cafe with Alice").debug(printThread)
    }

  } yield ()


  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
//    syncRoutine().exitCode
//    concurrentShowerWhileBoilingWater().exitCode
//    concurrentRoutine().exitCode
//  concurrentCallWithAliceCall().exitCode
    concurrentCallWithCoffeeAtHome().exitCode
}
