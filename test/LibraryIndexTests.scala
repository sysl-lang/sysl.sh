package sh.sysl

import io.github.edadma.cross_platform.*

import sh.sysl.doc.ApiModel

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/** The written library section's index, checked against the modules the library actually declares.
 *
 * **`ApiSectionTests` already does this for the generated half, and nothing did it for this one** —
 * which is not a symmetry worth having, because the two rot from the same cause. A module removed or
 * renamed in the compiler repository leaves the generated pages stale, which that suite reports by
 * name; it leaves `library/_index.md` pointing at a page for something that no longer exists, and
 * until now the only complaint came from the *other* section.
 *
 * That is exactly how `sysl.math.matrix` survived leaving the standard library for
 * `sh.sysl.linalg`: the generated `sysl-math-matrix.md` was caught the moment the pin moved, and the
 * hand-written page and its row in this table were caught by nobody. `DocsTests` compiled the page's
 * programs and could only say they had stopped compiling — which reads as a language regression
 * rather than as a module that is gone.
 *
 * **Only the direction that can be checked is checked.** Every module path this table names has to
 * be a module; a module does *not* have to appear in the table, because which modules earn a written
 * page is an editorial decision and several deliberately share one — `sysl.posix.time` is a section
 * of `time.md`, and `sysl` itself is `core.md`.
 */
class LibraryIndexTests extends AnyFreeSpec with Matchers {

  private val Index = "docs/content/library/_index.md"
  private val Lib   = "lib"

  /** Every module the library declares, by name. */
  private def declared: Set[String] = {
    val sources = Project.collect(Lib, None)

    sources should not be empty

    val parsed = sources.map(SyslParser.checked(_))

    parsed.collectFirst { case Left(ds) => ds } shouldBe None

    ApiModel.build(parsed.collect { case Right(p) => p }).map(_.name).toSet
  }

  /** The module path each row of the table is about, which is its first cell.
   *
   * Read with a regex rather than a table parser because the shape is fixed and one cell deep: a row
   * opens with a link whose text is the module path in a code span. A row that is not about a module
   * — the header, the rule under it — matches nothing and is skipped, which is the whole of the
   * parsing.
   */
  private val row = """^\|\s*\[`(sysl(?:\.[a-z_]+)*)`\]""".r

  private def named: List[String] =
    readFile(Index).linesIterator.flatMap(l => row.findFirstMatchIn(l).map(_.group(1))).toList

  "the written library index" - {

    "names no module the library no longer declares" in {
      assume(isDirectory(Lib), "lib/ is not staged; see the site's CLAUDE.md")
      assume(isFile(Index), "the library section is not present")

      val modules = declared

      withClue("named in the index and not declared by the library: ") {
        named.filterNot(modules).toSet shouldBe empty
      }
    }

    /** The half that makes the half above mean anything: a table nobody could parse passes it. */
    "carries a row for the modules it has always carried" in {
      assume(isFile(Index), "the library section is not present")

      withClue("module rows found: ") { named.length should be >= 20 }

      named should contain("sysl.math")
      named should contain("sysl.text")
    }
  }
}
