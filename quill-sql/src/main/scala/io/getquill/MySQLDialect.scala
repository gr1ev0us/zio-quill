package io.getquill

import io.getquill.ast.{ Ast, _ }
import io.getquill.context.CanReturnField
import io.getquill.context.sql.OrderByCriteria
import io.getquill.context.sql.idiom.{ NoConcatSupport, QuestionMarkBindVariables, SqlIdiom }
import io.getquill.idiom.StatementInterpolator._
import io.getquill.idiom.{ Statement, Token }
import io.getquill.util.Messages.fail

trait MySQLDialect
  extends SqlIdiom
  with QuestionMarkBindVariables
  with NoConcatSupport
  with CanReturnField {

  override def prepareForProbing(string: String) = {
    val quoted = string.replace("'", "\\'")
    s"PREPARE p${quoted.hashCode.abs.toString.token} FROM '$quoted'"
  }

  override def defaultAutoGeneratedToken(field: Token) = stmt"($field) VALUES (DEFAULT)"

  override def astTokenizer(implicit astTokenizer: Tokenizer[Ast], strategy: NamingStrategy): Tokenizer[Ast] =
    Tokenizer[Ast] {
      case c: OnConflict => c.token
      case ast           => super.astTokenizer.token(ast)
    }

  implicit def conflictTokenizer(implicit astTokenizer: Tokenizer[Ast], strategy: NamingStrategy): Tokenizer[OnConflict] = {
    import OnConflict._

    lazy val insertIgnoreTokenizer =
      Tokenizer[Entity] {
        case Entity.Opinionated(name, _, renameable) => stmt"IGNORE INTO ${renameable.fixedOr(name.token)(strategy.table(name).token)}"
      }

    def tokenizer(implicit astTokenizer: Tokenizer[Ast]) =
      Tokenizer[OnConflict] {
        case OnConflict(i, NoTarget, Update(a)) =>
          stmt"${i.token} ON DUPLICATE KEY UPDATE ${a.token}"

        case OnConflict(i, Properties(p), Ignore) =>
          val assignments = p
            .map(p => astTokenizer.token(p))
            .map(t => stmt"$t=$t")
            .mkStmt(",")
          stmt"${i.token} ON DUPLICATE KEY UPDATE $assignments"

        case OnConflict(i: io.getquill.ast.Action, NoTarget, Ignore) =>
          actionTokenizer(insertIgnoreTokenizer)(actionAstTokenizer, strategy).token(i)

        case _ =>
          fail("This upsert construct is not supported in MySQL. Please refer documentation for details.")
      }

    val customAstTokenizer =
      Tokenizer.withFallback[Ast](MySQLDialect.this.astTokenizer(_, strategy)) {
        case Property.Opinionated(Excluded(_), name, renameable) =>
          renameable.fixedOr(name.token)(stmt"VALUES(${strategy.column(name).token})")

        case Property.Opinionated(_, name, renameable) =>
          renameable.fixedOr(name.token)(strategy.column(name).token)
      }

    tokenizer(customAstTokenizer)
  }

  override implicit def operationTokenizer(implicit astTokenizer: Tokenizer[Ast], strategy: NamingStrategy): Tokenizer[Operation] =
    Tokenizer[Operation] {
      case BinaryOperation(a, StringOperator.`+`, b) => stmt"CONCAT(${a.token}, ${b.token})"
      case other                                     => super.operationTokenizer.token(other)
    }

  override implicit def orderByCriteriaTokenizer(implicit astTokenizer: Tokenizer[Ast], strategy: NamingStrategy): Tokenizer[OrderByCriteria] = Tokenizer[OrderByCriteria] {
    case OrderByCriteria(prop, AscNullsFirst | Asc)  => stmt"${prop.token} ASC"
    case OrderByCriteria(prop, DescNullsFirst)       => stmt"ISNULL(${prop.token}) DESC, ${prop.token} DESC"
    case OrderByCriteria(prop, AscNullsLast)         => stmt"ISNULL(${prop.token}) ASC, ${prop.token} ASC"
    case OrderByCriteria(prop, DescNullsLast | Desc) => stmt"${prop.token} DESC"
  }

  override protected def limitOffsetToken(query: Statement)(implicit astTokenizer: Tokenizer[Ast], strategy: NamingStrategy) =
    Tokenizer[(Option[Ast], Option[Ast])] {
      case (None, Some(offset)) => stmt"$query LIMIT 18446744073709551610 OFFSET ${offset.token}"
      case other                => super.limitOffsetToken(query).token(other)
    }
}

object MySQLDialect extends MySQLDialect
