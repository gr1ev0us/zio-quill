package io.getquill.context.jasync.mysql

import java.time.{LocalDate, LocalDateTime}
import io.getquill.context.sql.EncodingSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import java.util.Date
import io.getquill.Query
import io.getquill.context.jasync.mysql

import java.time.temporal.ChronoUnit

class MysqlJAsyncEncodingSpec extends EncodingSpec {

  val context = testContext
  import testContext._

  "encodes and decodes types" in {
    val r =
      for {
        _      <- testContext.run(delete)
        _      <- testContext.run(liftQuery(insertValues).foreach(e => insert(e)))
        result <- testContext.run(query[EncodingTestEntity])
      } yield result

    verify(Await.result(r, Duration.Inf))
  }

  "decode numeric types correctly" - {
    "decode byte to" - {
      "short" in {
        prepareEncodingTestEntity()
        case class EncodingTestEntity(v3: Short)
        val v3List = Await.result(testContext.run(query[EncodingTestEntity]), Duration.Inf)
        v3List.map(_.v3) must contain theSameElementsAs List(1: Byte, 0: Byte)
      }
      "int" in {
        prepareEncodingTestEntity()
        case class EncodingTestEntity(v3: Int)
        val v3List = Await.result(testContext.run(query[EncodingTestEntity]), Duration.Inf)
        v3List.map(_.v3) must contain theSameElementsAs List(1, 0)
      }
      "long" in {
        prepareEncodingTestEntity()
        case class EncodingTestEntity(v3: Long)
        val v3List = Await.result(testContext.run(query[EncodingTestEntity]), Duration.Inf)
        v3List.map(_.v3) must contain theSameElementsAs List(1L, 0L)
      }
    }
    "decode short to" - {
      "int" in {
        prepareEncodingTestEntity()
        case class EncodingTestEntity(v5: Int)
        val v5List = Await.result(testContext.run(query[EncodingTestEntity]), Duration.Inf)
        v5List.map(_.v5) must contain theSameElementsAs List(23, 0)
      }
      "long" in {
        prepareEncodingTestEntity()
        case class EncodingTestEntity(v5: Long)
        val v5List = Await.result(testContext.run(query[EncodingTestEntity]), Duration.Inf)
        v5List.map(_.v5) must contain theSameElementsAs List(23L, 0L)
      }
    }
    "decode int to long" in {
      case class EncodingTestEntity(v6: Long)
      val v6List = Await.result(testContext.run(query[EncodingTestEntity]), Duration.Inf)
      v6List.map(_.v6) must contain theSameElementsAs List(33L, 0L)
    }

    "decode and encode any numeric as boolean" in {
      case class EncodingTestEntity(v3: Boolean, v4: Boolean, v6: Boolean, v7: Boolean)
      Await.result(testContext.run(query[EncodingTestEntity]), Duration.Inf)
      ()
    }
  }

  "decode date types" in {
    case class DateEncodingTestEntity(v1: Date, v2: Date, v3: Date)
    val entity = DateEncodingTestEntity(new Date, new Date, new Date)
    val r = for {
      _      <- testContext.run(query[DateEncodingTestEntity].delete)
      _      <- testContext.run(query[DateEncodingTestEntity].insertValue(lift(entity)))
      result <- testContext.run(query[DateEncodingTestEntity])
    } yield result
    Await.result(r, Duration.Inf)
    ()
  }

  "decode LocalDate and LocalDateTime types" in {
    case class DateEncodingTestEntity(v1: LocalDate, v2: LocalDateTime, v3: LocalDateTime)
    val entity = DateEncodingTestEntity(
      LocalDate.now,
      LocalDateTime.now.truncatedTo(ChronoUnit.MILLIS),
      LocalDateTime.now.truncatedTo(ChronoUnit.MILLIS)
    )
    val r = for {
      _      <- testContext.run(query[DateEncodingTestEntity].delete)
      _      <- testContext.run(query[DateEncodingTestEntity].insertValue(lift(entity)))
      result <- testContext.run(query[DateEncodingTestEntity])
    } yield result
    Await.result(r, Duration.Inf) must contain(entity)
  }

  "fails if the column has the wrong type" - {
    "numeric" in {
      Await.result(testContext.run(liftQuery(insertValues).foreach(e => insert(e))), Duration.Inf)
      case class EncodingTestEntity(v1: Int)
      val e = intercept[IllegalStateException] {
        Await.result(testContext.run(query[EncodingTestEntity]), Duration.Inf)
      }
    }
    "non-numeric" in {
      Await.result(testContext.run(liftQuery(insertValues).foreach(e => insert(e))), Duration.Inf)
      case class EncodingTestEntity(v1: Date)
      val e = intercept[IllegalStateException] {
        Await.result(testContext.run(query[EncodingTestEntity]), Duration.Inf)
      }
    }
  }

  "encodes sets" in {
    val q = quote { (set: Query[Int]) =>
      query[EncodingTestEntity].filter(t => set.contains(t.v6))
    }
    val fut =
      for {
        _ <- testContext.run(query[EncodingTestEntity].delete)
        _ <- testContext.run(liftQuery(insertValues).foreach(e => query[EncodingTestEntity].insertValue(e)))
        r <- testContext.run(q(liftQuery(insertValues.map(_.v6))))
      } yield {
        r
      }
    verify(Await.result(fut, Duration.Inf))
  }

  "encodes custom type inside singleton object" in {
    object Singleton {
      def apply()(implicit c: TestContext): Future[Seq[Long]] = {
        import c._
        for {
          _      <- c.run(query[EncodingTestEntity].delete)
          result <- c.run(liftQuery(insertValues).foreach(e => query[EncodingTestEntity].insertValue(e)))
        } yield result
      }
    }

    implicit val c = testContext
    Await.result(Singleton(), Duration.Inf)
  }

  private def prepareEncodingTestEntity(): Unit = {
    val prepare = for {
      _ <- testContext.run(delete)
      _ <- testContext.run(liftQuery(insertValues).foreach(e => insert(e)))
    } yield {}
    Await.result(prepare, Duration.Inf)
  }
}
