package sh.sysl

import io.github.edadma.cross_platform.*
import io.github.edadma.highlighter.Highlighter

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

    // **The reconciliation above is about WORDS. The grammar also carries identifier PATTERNS, and
    // nothing checked those at all** — so an ASCII character class in a language whose identifiers
    // are Unicode's letters was invisible here, and what it produces is a page where `struct
    // Círculo` renders as an unstyled word: a line that looks like it has little to highlight rather
    // than like a fault.
    //
    // **Asked of the renderer**, which is the only way the question is well posed. A TextMate
    // grammar is matched by Oniguruma — juicer takes `highlighter`, which takes
    // `io.github.edadma:oniguruma` — and that engine disagrees with `java.util.regex` about `\b`,
    // about which Unicode properties exist, and about lookaround. Compiling these patterns with some
    // other engine tests that engine and says nothing about what a reader sees.
    def styled(code: String, name: String): List[String] =
      Highlighter.fromJson(readFile(grammarPath)) match
        case Left(why) => fail(s"the grammar did not load, so nothing could be styled: $why")
        // Trimmed, because a capture may take the space in front of the name with it — `end`'s
        // second group is `\s+<name>` — and the scope is still the one on that name.
        case Right(h)  => h.tokens(code).flatten.filter(_.text.trim == name).flatMap(_.scopes).distinct

    // **THE ONE THAT MATTERS MOST, AND WHICH NOTHING ASKED.** A pattern the engine cannot compile
    // does not stop the grammar loading: `fromJson` still answers `Right`, the pattern is dropped,
    // and everything it would have styled comes out bare. A grammar that highlights **nothing**
    // loads exactly as cleanly as one that works, and the only record is a list nobody read.
    "loads with every pattern compiled, since one that does not is dropped in silence" in {
      Highlighter.fromJson(readFile(grammarPath)) match
        case Left(why) => fail(s"the grammar did not load at all: $why")
        case Right(h)  =>
          withClue(s"patterns the engine refused:\n  ${h.loadWarnings.mkString("\n  ")}\n") {
            h.loadWarnings shouldBe empty
          }
    }

    "styles a declaration whose name is not ASCII, and a name whose tail is not" in {
      styled("struct Círculo", "Círculo") should contain("entity.name.type.sysl")
      styled("end Círculo", "Círculo") should contain("entity.name.type.sysl")
      styled("área(ancho: real) -> real = ancho", "área") should contain("entity.name.function.sysl")
      styled("val c: Círculo = q", "Círculo") should contain("entity.name.type.sysl")
      styled("val x = area(3.0)", "area") should contain("entity.name.function.call.sysl")
    }

    /** **A name whose FIRST letter is not ASCII is styled where the rule names a declaration and not
      * where it depends on case**, and that is a limit of the engine rather than a decision: the
      * Oniguruma port supports the general Unicode categories and not the subcategories, so `\p{Lu}`
      * and `\p{Ll}` are refused and there is no compilable way to say "an uppercase letter".
      *
      * So the two case-dependent rules keep an ASCII first character and take the Unicode class for
      * the tail — `Círculo` styles, `Ómnibus` does not — while the rules naming a declaration are
      * Unicode throughout. Pinned rather than left as an absence, so whoever adds the subcategories
      * to the engine finds the case that says what changes.
      */
    "and does not style one whose first letter is not ASCII where the rule is about case" in {
      styled("val x = área(3.0)", "área") should not contain "entity.name.function.call.sysl"
      styled("val c: Ómnibus = q", "Ómnibus") should not contain "entity.name.type.sysl"
    }

    // The other direction, so the classes above cannot be widened into styling anything at all: a
    // digit still does not begin a name, in any script. `source.sysl` is on every token, so what is
    // asserted is the absence of the *type* scope rather than of all scopes.
    "and still refuses to style a name beginning with a digit as a type" in {
      styled("struct 3café", "3café") should not contain "entity.name.type.sysl"
    }
  }
}
