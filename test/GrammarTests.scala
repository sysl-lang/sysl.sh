package sh.sysl

import io.github.edadma.cross_platform.*

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/** The site's syntax-highlighting grammar, checked against the lexer it is supposed to describe.
 *
 * `docs/grammars/sysl.tmLanguage.json` is a second, hand-written statement of which words are
 * keywords, and nothing makes the two agree. A word added to `SyslLexical.reserved` is a keyword the
 * compiler honours and the site renders as an ordinary identifier — which looks like a line that
 * simply has little to highlight rather than like a fault, so it survives being looked at. `ref` did
 * exactly that: reserved, taught in the tour, and unstyled on the published page.
 *
 * **The compiler owns the grammar now** — `sysl weave` renders literate sources with it, so it lives
 * beside the `SyslLexical` it is a claim about and has a `GrammarTests` of its own there. The copy
 * here is not redundant with that one: `docs.yml` builds the published site from this repository
 * alone and fetches nothing, so **this file is what the site actually renders with**. `test.yml`
 * diffs the two and fails on a drift; this suite checks the one that deploys.
 *
 * Both directions are checked against the grammar's **`keyword` section only**. The predeclared
 * scalars are styled from a section of their own and are deliberately *not* reserved words — that is
 * what lets the open `iN` / `uN` families need no lexical support — so a check spanning the whole
 * file would have to allow them and would stop meaning anything.
 */
class GrammarTests extends AnyFreeSpec with Matchers {

  private val grammarPath = "docs/grammars/sysl.tmLanguage.json"

  /** One entry of the grammar's repository, as text. */
  private def section(name: String): String = {
    val text  = readFile(grammarPath)
    val start = text.indexOf(s"\"$name\": {")

    withClue(s"$grammarPath has no '$name' section\n") { start should be >= 0 }

    val end = text.indexOf("\n    },", start)

    end should be > start

    text.substring(start, end)
  }

  /** Every regex that section styles with, concatenated, with the regex escapes blanked out.
    *
    * Blanking matters: the patterns arrive still escaped as they appear in the JSON, so `self` is
    * written `\\bself\\b` and is preceded by the *letter* `b`. Any word-boundary reasoning done
    * against the raw text is reasoning about backslashes, and quietly finds nothing.
    *
    * `comment` and `name` fields are left out, because the comment beside a keyword group nearly
    * always names the very words in it — which is exactly how this kind of test passes by accident.
    */
  private def styledIn(sectionText: String): Set[String] = {
    val field = """"(?:match|begin|end)"\s*:\s*"((?:[^"\\]|\\.)*)"""".r
    val text = field
      .findAllMatchIn(sectionText)
      .map(_.group(1))
      .mkString(" ")
      .replaceAll("""\\\\.""", " ")

    """[a-z_]{2,}""".r.findAllMatchIn(text).map(_.matched).toSet
  }

  /** The `match` patterns of a section, compiled — which is the other question this file can ask of
    * the grammar and did not until 2026-08-28: whether a pattern **matches** what it claims to,
    * rather than which words appear inside one.
    *
    * The JSON holds each one doubly escaped, so `\\b` in the file is `\b` in the pattern, and one
    * `replace` is the whole of the decoding. Compiled with `java.util.regex`, which is what juicer
    * uses for a TextMate grammar — so `\p{L}` means here what it means on the published page.
    */
  private def patterns(name: String): List[scala.util.matching.Regex] = {
    val field = """"match"\s*:\s*"((?:[^"\\]|\\.)*)"""".r

    field.findAllMatchIn(section(name)).map(m => m.group(1).replace("\\\\", "\\").r).toList
  }

  private lazy val asKeyword: Set[String] = styledIn(section("keyword"))

  /** `true`, `false` and `null` are reserved words that the grammar styles as constants rather than
    * as keywords, which is the right call — they are values, and colouring them like `if` would say
    * something false about them. So a reserved word is satisfied by either section.
    */
  private lazy val asConstant: Set[String] = styledIn(section("constant"))

  /** Words the grammar styles as keywords without the lexer reserving them. They are keywords only
    * where the grammar expects one and ordinary identifiers everywhere else, so `SyslLexical` never
    * sees them. Adding to this set is a decision about the language, not a way to fix a red test.
    */
  private val soft =
    Set("is", "not", "invariant", "new", "with", "within", "where", "opaque", "derives", "become")

  "the highlighting grammar" - {

    "styles every word the lexer reserves" in {
      val styled  = asKeyword ++ asConstant
      val missing = new SyslLexical().reserved.toList.sorted.filterNot(styled)

      withClue(s"reserved by SyslLexical but unstyled by $grammarPath: ${missing.mkString(", ")}\n") {
        missing shouldBe empty
      }
    }

    // The disagreement half. Without it the test above would pass on a grammar whose keyword section
    // had been replaced by one alternation listing every lowercase word imaginable — which would
    // style correctly and prove nothing — and it is also what notices a word going the other way,
    // when the lexer stops reserving something the grammar goes on colouring.
    "and styles nothing else, beyond the soft keywords" in {
      val unknown = (asKeyword -- new SyslLexical().reserved -- soft).toList.sorted

      withClue(s"styled as a keyword by $grammarPath but neither reserved nor soft: " +
        s"${unknown.mkString(", ")}\n") {
        unknown shouldBe empty
      }
    }

    // **The reconciliation above is about WORDS, and the grammar also carries identifier PATTERNS
    // that nothing checked.** A pattern is an ASCII character class in a language whose identifiers
    // are Unicode's letters (`reference/lexical.md § Identifiers`), and what that produces is a page
    // where `struct Círculo` renders as an unstyled word — a thing that looks like a line with
    // little to highlight rather than like a fault, which is the same failure the reserved-word half
    // exists for, one construct over.
    //
    // Asserted against `java.util.regex`, which is what juicer compiles these with.
    "matches a declaration, a call and a type whose name is not ASCII" in {
      // **What is asserted is the WHOLE name and not that something matched**, which is the
      // difference between a real check and one that cannot fail here. An ASCII class matches
      // `struct C` and stops at the accent, so a `findFirstIn` answers `Some` on a pattern that
      // styles one letter of the name — checked by reverting each class and watching this go red.
      def spans(section: String, sample: String, name: String): Boolean =
        patterns(section).exists(_.findFirstMatchIn(sample).exists(_.matched.endsWith(name)))

      spans("declaration", "struct Círculo", "Círculo") shouldBe true
      spans("declaration", "end Círculo", "Círculo") shouldBe true
      spans("declaration", "área(ancho: real)", "área") shouldBe true
      spans("call", "área(3.0)", "área") shouldBe true
      spans("type", "Círculo", "Círculo") shouldBe true
    }

    // The other direction, so the classes above cannot be widened into matching anything at all: a
    // digit still does not begin a name, in any script.
    "and still refuses a name beginning with a digit" in {
      val started =
        patterns("declaration").exists(_.findFirstMatchIn("struct 3café").exists(_.matched.contains("3")))

      started shouldBe false
    }
  }
}
