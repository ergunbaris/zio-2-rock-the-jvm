package content

import zio.{Console,  IO, Scope, Task, ULayer, ZIO, ZIOAppArgs, ZLayer}

object ZLayersPlayground extends zio.ZIOAppDefault {

  // ZIO[-R, +E , +A] = "effects" R= input E = error A = output
  // R => Either[E, A]

  val meaningOfLife: ZIO[Any, Nothing, Int] = ZIO.succeed(42)
  val aFailure: IO[String, Nothing] = ZIO.fail("an error")

  val greeting = for {
    _ <- Console.printLine("Hi name please")
    name <- Console.readLine
    _ <- Console.printLine(s"Hello $name")
  } yield ()

  case class User(name: String, email: String)

  object UserEmailer {

    // service def
    trait Service {
      def notify(user: User, message: String): Task[Unit] /* ZIO[ANy, Throwable, Unit] */

    }

    // service impl
    val live: ULayer[Service] = ZLayer.succeed(new Service {
      override def notify(user: User, message: String): Task[Unit] = ZIO.attempt {
        println(s"[UserEmailer] Sending $message to ${user.email}")
      }
    })

    // front-facing API
    def notify(user: User, message: String): ZIO[Service, Throwable, Unit] =
      ZIO.environmentWithZIO[Service](hasService => hasService.get.notify(user, message))
  }

  object UserDb {

    // service def
    trait Service {
      def insert(user: User): Task[Unit]
    }

    //    service impl
    val live: ULayer[Service] = ZLayer.succeed(new Service {
      override def insert(user: User): Task[Unit] = ZIO.attempt {
        println(s"[Database] Inserting $user to db")
      }
    })

    // front facing api
    def insert(user: User): ZIO[Service, Throwable, Unit] =
      ZIO.environmentWithZIO(_.get.insert(user))
  }

  // HORIZONTAL COMPOSITION
  // ZLayer[In1, E1, Out1] ++ Zlayer[In2, E2, Out2] => Zlayer[In1 with In2, super(E1, E2), Out1 with Out2]

  val userBackendLayer: ZLayer[Any, Nothing, UserDb.Service with UserEmailer.Service] = UserDb.live ++ UserEmailer.live

  // VERTICAL COMPOSITION
  object UserSubscription {

    case class Service(notifier: UserEmailer.Service, userDb: UserDb.Service) {
      def subscribe(user: User): Task[User] = for {
        _ <- userDb.insert(user)
        _ <- notifier.notify(user, s"Welcome ${user.name}")
      } yield (user)
    }

    val live: ZLayer[UserEmailer.Service with UserDb.Service, Nothing, Service] = ZLayer {
      for{
        userDbService <- ZIO.service[UserDb.Service]
        userEmailerService <- ZIO.service[UserEmailer.Service]
      } yield Service(userEmailerService, userDbService)
    }

    // front-facing
    def subscribe(user: User): ZIO[Service, Throwable, User] =
      ZIO.environmentWithZIO(_.get.subscribe(user))
  }

  val userSubscriptionLayer = userBackendLayer >>> UserSubscription.live

  val boris = User("boris", "boris_email")

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    UserSubscription.subscribe(boris)
      .provideLayer(userSubscriptionLayer)
      .exitCode
}
