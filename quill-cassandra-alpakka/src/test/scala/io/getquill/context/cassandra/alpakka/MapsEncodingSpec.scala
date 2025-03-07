package io.getquill.context.cassandra.alpakka

import io.getquill.context.cassandra.CollectionsSpec

import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDate}
import java.util.UUID

class MapsEncodingSpec extends CollectionsSpec with CassandraAlpakkaSpec {
  val ctx = testDB
  import ctx._

  case class MapsEntity(
    id: Int,
    textDecimal: Map[String, BigDecimal],
    intDouble: Map[Int, Double],
    longFloat: Map[Long, Float],
    boolDate: Map[Boolean, LocalDate],
    uuidTimestamp: Map[UUID, Instant]
  )

  val e = MapsEntity(
    1,
    Map("1"  -> BigDecimal(1)),
    Map(1    -> 1d, 2 -> 2d, 3 -> 3d),
    Map(1L   -> 3f),
    Map(true -> LocalDate.now()),
    Map(
      UUID.randomUUID() -> Instant.now().truncatedTo(ChronoUnit.MILLIS)
    ) // See https://stackoverflow.com/a/74781779/2431728
  )
  val q = quote(query[MapsEntity])

  "Map encoders/decoders" in {
    await {
      for {
        _   <- ctx.run(q.insertValue(lift(e)))
        res <- ctx.run(q.filter(_.id == 1))
      } yield {
        res.head mustBe e
      }
    }
  }

  "Empty maps and optional fields" in {
    case class Entity(
      id: Int,
      textDecimal: Option[Map[String, BigDecimal]],
      intDouble: Option[Map[Int, Double]],
      longFloat: Map[Long, Float]
    )
    val e = Entity(1, Some(Map("1" -> BigDecimal(1))), None, Map())
    val q = quote(querySchema[Entity]("MapsEntity"))

    await {
      for {
        _   <- ctx.run(q.insertValue(lift(e)))
        res <- ctx.run(q.filter(_.id == 1))
      } yield {
        res.head mustBe e
      }
    }
  }

  "Mapped encoding for CassandraType" in {
    case class StrEntity(id: Int, textDecimal: Map[StrWrap, BigDecimal])
    val e = StrEntity(1, Map(StrWrap("1") -> BigDecimal(1)))
    val q = quote(querySchema[StrEntity]("MapsEntity"))

    await {
      for {
        _   <- ctx.run(q.insertValue(lift(e)))
        res <- ctx.run(q.filter(_.id == 1))
      } yield {
        res.head mustBe e
      }
    }
  }

  "Mapped encoding for CassandraMapper types" in {
    case class IntEntity(id: Int, intDouble: Map[IntWrap, Double])
    val e = IntEntity(1, Map(IntWrap(1) -> 1d))
    val q = quote(querySchema[IntEntity]("MapsEntity"))

    await {
      for {
        _   <- ctx.run(q.insertValue(lift(e)))
        res <- ctx.run(q.filter(_.id == 1))
      } yield {
        res.head mustBe e
      }
    }
  }

  "Map in where clause / contains" in {
    val e = MapFrozen(Map(1 -> true))

    await {
      for {
        _    <- ctx.run(mapFroz.insertValue(lift(e)))
        res1 <- ctx.run(mapFroz.filter(_.id == lift(Map(1 -> true))))
        res2 <- ctx.run(mapFroz.filter(_.id == lift(Map(1 -> false))))
      } yield {
        res1 mustBe List(e)
        res2 mustBe Nil
      }
    }
    await {
      for {
        _    <- ctx.run(mapFroz.insertValue(lift(e)))
        res1 <- ctx.run(mapFroz.filter(_.id.contains(1)).allowFiltering)
        res2 <- ctx.run(mapFroz.filter(_.id.contains(2)).allowFiltering)
      } yield {
        res1 mustBe List(e)
        res2 mustBe Nil
      }
    }
  }

  "Map.containsValue" in {
    val e = MapFrozen(Map(1 -> true))

    await {
      for {
        _    <- ctx.run(mapFroz.insertValue(lift(e)))
        res1 <- ctx.run(mapFroz.filter(_.id.containsValue(true)).allowFiltering)
        res2 <- ctx.run(mapFroz.filter(_.id.containsValue(false)).allowFiltering)
      } yield {
        res1 mustBe List(e)
        res2 mustBe Nil
      }
    }
  }

  override protected def beforeEach(): Unit = {
    await {
      ctx.run(q.delete)
    }
    await {
      ctx.run(mapFroz.delete)
    }
  }
}
