package com.github.lppedd.cc.parser

import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.ApiStatus
import kotlin.contracts.contract
import kotlin.internal.InlineOnly
import kotlin.math.max

/**
 * @author Edoardo Luppi
 */
@ApiStatus.Experimental
object CCParser {
  private const val TYPE = "type"
  private const val SCOPE = "scope"
  private const val BRK_CHANGE = "brkChange"
  private const val SEPARATOR = "separator"
  private const val SUBJECT = "subject"
  private const val FOOTER_TYPE = "footerType"
  private const val FOOTER = "footer"

  private val headerRegExp = """
    |(?:[^:]*? |)??(?<$TYPE>[a-zA-Z0-9-]+)
    |(?<$SCOPE>(?:\([^()\r\n]*\)|\(.*(?=!)|\(.*(?=:))|\(.*(?=$))?
    |(?<$BRK_CHANGE>!)?
    |(?<$SEPARATOR>:)?
    |(?<$SUBJECT>(?<=:).+)?$
  """
    .trimMargin()
    .replace("\n", "")
    .trim()
    .toRegex()

  // TODO: should not consider lines below if there is nothing after the separator
  private val footerRegExp = """
    |^(?<$FOOTER_TYPE>[ a-zA-Z0-9-]+)?
    |(?<$SEPARATOR>:)?
    |(?<$FOOTER>(?<=:)(?:.|[\r\n](?![\r\n]|([a-zA-Z0-9-]+)?(:)?((?<=:))))+)?
  """
    .trimMargin()
    .replace("\n", "")
    .trim()
    .toRegex()

  fun parseHeader(segment: CharSequence): CommitTokens {
    val groups = headerRegExp.matchEntire(segment)?.groups ?: return CommitTokens()
    return CommitTokens(
      type = groups[TYPE]?.run { ValidToken(value, range.forCaretModel()) } ?: InvalidToken,
      scope = buildCommitScope(groups[SCOPE]),
      breakingChange = BreakingChange(groups[BRK_CHANGE] != null),
      separator = Separator(groups[SEPARATOR] != null),
      subject = groups[SUBJECT]?.run { ValidToken(value, range.forCaretModel()) } ?: InvalidToken
    )
  }

  fun parseFooter(text: CharSequence): FooterTokens {
    val groups = footerRegExp.find(text)?.groups ?: return FooterTokens()
    return FooterTokens(
      type = groups[FOOTER_TYPE]?.run { ValidToken(value, range.forCaretModel()) } ?: InvalidToken,
      separator = Separator(groups[SEPARATOR] != null),
      footer = groups[FOOTER]?.run { ValidToken(value, range.forCaretModel()) } ?: InvalidToken
    )
  }

  private fun buildCommitScope(matchGroup: MatchGroup?): Scope {
    if (matchGroup == null) {
      return InvalidToken
    }

    var value = StringBuilder(matchGroup.value) as CharSequence
    var startIndex = matchGroup.range.first
    var endIndex = matchGroup.range.last

    if (value.first() == '(') {
      value = value.drop(1)
      startIndex++
      endIndex++
    }

    if (value.isNotEmpty() && value.last() == ')') {
      value = value.dropLast(1)
      endIndex--
    }

    return ValidToken("$value", CCTextRange(startIndex, max(startIndex, endIndex)))
  }
}

@InlineOnly
private inline fun IntRange.forCaretModel(): TextRange =
  CCTextRange(first, maxOf(1, last + 1))

internal fun Token.isInContext(offset: Int): Boolean {
  contract { returns(true) implies (this@isInContext is ValidToken) }
  return this is ValidToken && range.contains(offset)
}

interface Token
interface Type : Token
interface Scope : Token
interface Subject : Token
interface FooterType : Token
interface Footer : Token
inline class BreakingChange(val isPresent: Boolean)
inline class Separator(val isPresent: Boolean)

object InvalidToken :
    Type,
    Scope,
    Subject,
    FooterType,
    Footer

class ValidToken(val value: String, val range: TextRange) :
    Type,
    Scope,
    Subject,
    FooterType,
    Footer
