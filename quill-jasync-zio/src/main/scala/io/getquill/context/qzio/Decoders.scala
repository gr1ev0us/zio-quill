package io.getquill.context.qzio

import com.github.jasync.sql.db.RowData
import io.getquill.context.Context
import io.getquill.util.Messages.fail

import java.math.{BigDecimal => JavaBigDecimal}
import java.time.{LocalDate, LocalDateTime, ZoneId}
import java.util.Date
import scala.reflect.{ClassTag, classTag}

trait Decoders {
  this: ZioJAsyncContext[_, _, _] =>

  type Decoder[T] = AsyncDecoder[T]

  type ResultRow = RowData
  type Session   = Unit

  type DecoderSqlType = SqlTypes.SqlTypes

  case class AsyncDecoder[T](sqlType: DecoderSqlType)(implicit decoder: BaseDecoder[T]) extends BaseDecoder[T] {
    override def apply(index: Index, row: ResultRow, session: Session) =
      decoder(index, row, session)
  }

  def decoder[T: ClassTag](
    f: PartialFunction[Any, T] = PartialFunction.empty,
    sqlType: DecoderSqlType
  ): Decoder[T] =
    AsyncDecoder[T](sqlType)(new BaseDecoder[T] {
      def apply(index: Index, row: ResultRow, session: Session) =
        row.get(index) match {
          case value: T                      => value
          case value if f.isDefinedAt(value) => f(value)
          case value =>
            fail(
              s"Value '$value' at index $index can't be decoded to '${classTag[T].runtimeClass}'"
            )
        }
    })

  implicit def mappedDecoder[I, O](implicit mapped: MappedEncoding[I, O], decoder: Decoder[I]): Decoder[O] =
    AsyncDecoder(decoder.sqlType)(new BaseDecoder[O] {
      def apply(index: Index, row: ResultRow, session: Session): O =
        mapped.f(decoder.apply(index, row, session))
    })

  trait NumericDecoder[T] extends BaseDecoder[T] {

    def apply(index: Index, row: ResultRow, session: Session) =
      (row.get(index): Any) match {
        case v: Byte           => decode(v)
        case v: Short          => decode(v)
        case v: Int            => decode(v)
        case v: Long           => decode(v)
        case v: Float          => decode(v)
        case v: Double         => decode(v)
        case v: JavaBigDecimal => decode(v: BigDecimal)
        case other =>
          fail(s"Value $other is not numeric, type: ${other.getClass.getCanonicalName}")
      }

    def decode[U](v: U)(implicit n: Numeric[U]): T
  }

  implicit def optionDecoder[T](implicit d: Decoder[T]): Decoder[Option[T]] =
    AsyncDecoder(d.sqlType)(new BaseDecoder[Option[T]] {
      def apply(index: Index, row: ResultRow, session: Session) =
        row.get(index) match {
          case null  => None
          case value => Some(d(index, row, session))
        }
    })

  implicit val stringDecoder: Decoder[String] = decoder[String](PartialFunction.empty, SqlTypes.VARCHAR)

  implicit val bigDecimalDecoder: Decoder[BigDecimal] =
    AsyncDecoder(SqlTypes.REAL)(new NumericDecoder[BigDecimal] {
      def decode[U](v: U)(implicit n: Numeric[U]) =
        BigDecimal(n.toDouble(v))
    })

  implicit val booleanDecoder: Decoder[Boolean] =
    decoder[Boolean](
      {
        case v: Byte  => v == (1: Byte)
        case v: Short => v == (1: Short)
        case v: Int   => v == 1
        case v: Long  => v == 1L
      },
      SqlTypes.BOOLEAN
    )

  implicit val byteDecoder: Decoder[Byte] =
    decoder[Byte](
      { case v: Short =>
        v.toByte
      },
      SqlTypes.TINYINT
    )

  implicit val shortDecoder: Decoder[Short] =
    decoder[Short](
      { case v: Byte =>
        v.toShort
      },
      SqlTypes.SMALLINT
    )

  implicit val intDecoder: Decoder[Int] =
    AsyncDecoder(SqlTypes.INTEGER)(new NumericDecoder[Int] {
      def decode[U](v: U)(implicit n: Numeric[U]) =
        n.toInt(v)
    })

  implicit val longDecoder: Decoder[Long] =
    AsyncDecoder(SqlTypes.BIGINT)(new NumericDecoder[Long] {
      def decode[U](v: U)(implicit n: Numeric[U]) =
        n.toLong(v)
    })

  implicit val floatDecoder: Decoder[Float] =
    AsyncDecoder(SqlTypes.FLOAT)(new NumericDecoder[Float] {
      def decode[U](v: U)(implicit n: Numeric[U]) =
        n.toFloat(v)
    })

  implicit val doubleDecoder: Decoder[Double] =
    AsyncDecoder(SqlTypes.DOUBLE)(new NumericDecoder[Double] {
      def decode[U](v: U)(implicit n: Numeric[U]) =
        n.toDouble(v)
    })

  implicit val byteArrayDecoder: Decoder[Array[Byte]] = decoder[Array[Byte]](PartialFunction.empty, SqlTypes.TINYINT)

  implicit val dateDecoder: Decoder[Date] = decoder[Date](
    {
      case date: LocalDateTime => Date.from(date.atZone(ZoneId.systemDefault()).toInstant)
      case date: LocalDate     => Date.from(date.atStartOfDay.atZone(ZoneId.systemDefault()).toInstant)
    },
    SqlTypes.TIMESTAMP
  )
  implicit val localDateDecoder: Decoder[LocalDate] = decoder[LocalDate](PartialFunction.empty, SqlTypes.DATE)
  implicit val localDateTimeDecoder: Decoder[LocalDateTime] =
    decoder[LocalDateTime](PartialFunction.empty, SqlTypes.TIMESTAMP)
}
