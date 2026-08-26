package sh.sysl

import io.github.edadma.cross_platform.*

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import sh.sysl.doc.{ApiModel, MarkdownWriter}

/** The generated API section under `docs/content/api/`, checked against the library it was generated
 * from.
 *
 * **`DocsTests` does not look at these pages and cannot.** They are `sysl-doc`'s output, and what
 * they are full of is signature fragments — `starts_with(s: []const u8) -> bool` with no body, which
 * the compiler correctly refuses. So they are excluded there by path, and this is what replaces the
 * coverage: the claim a generated page makes is not that its programs run, it is that **it still
 * agrees with the source it came from**.
 *
 * That is the claim that rots. A doc comment edited in the compiler repository leaves every page
 * here saying what the library used to say, and nothing on the site would know — the pages are
 * committed, so they go stale silently and stay stale until somebody regenerates for an unrelated
 * reason.
 *
 * **It regenerates from `lib/`, which is the library at the commit `syslVersion` names**, so the
 * pages are pinned to the same compiler every fenced block on the site is compiled with. A `lib/`
 * that is stale fails this the way it fails everything else here, and for once loudly.
 *
 * **The command-line equivalent is `sysl doc --check`**, and this is deliberately not that. Running
 * the binary would need one on the runner — a release tarball the site cannot use, since its `dev`
 * pins an interim — where the generator itself is on the classpath already, because the site depends
 * on the compiler. Same comparison, no new infrastructure, and it runs in the suite that is already
 * green or not.
 */
class ApiSectionTests extends AnyFreeSpec with Matchers {

  /** Where the generated pages are committed. */
  private val Out = "docs/content/api"

  /** The library source, which `build.sbt` also points `SYSL_LIB` at. */
  private val Lib = "lib"

  /** The index page's title, and the line under it.
   *
   * **These are arguments to the generator, so they live with the thing that checks them.** They are
   * the site's text rather than the generator's — where the written pages live, and what they are
   * for, is not something a generator could know — and writing them here means the regeneration
   * command cannot drift from what the committed pages were made with without this failing.
   *
   * The note's link is anchored, and at a heading that exists: `library/_index.md` has no `Modules`
   * section, and an anchor that lands nowhere is what `AnchorTests` is for.
   */
  /** **Not "Standard library", which is what `docs/content/library/` is called.** Two sections of
   * that name is what the nav showed, differing only in a capital L — and the generated one is not a
   * second copy of the written one, it is the signatures. The note below says as much in prose; the
   * title has to say it in the one place a reader looks first.
   */
  private val Title = "Library API"

  /** Where the generated section sits in the navigation, passed to the generator like the two above.
   *
   * **45 puts the signatures directly after the prose that explains them** — `library` is 40 and
   * `guides` is 50 — which is the relationship the note already describes: start with the written
   * section to learn a module, come here when you know what you want.
   *
   * It was the only section index on the site with no `weight` at all, so its place was whatever the
   * unweighted default happened to be. That is plausibly why the duplicate title read as a duplicate:
   * the generated section landed beside the hand-written one it shares a subject with.
   */
  private val Weight = 45

  private val Note =
    "These pages are generated from the library's own doc comments: every declaration, with its " +
      "signature and nothing else. The [written library section]" +
      "(/library/#the-library-is-a-tree-and-the-tree-is-the-point) is the other half — what each " +
      "module is *for*, why its pieces are shaped as they are, and worked examples you can run. " +
      "Start there to learn a module; come here when you know what you want and need its exact " +
      "signature."

  /** The pages `sysl-doc` would write for the library as it stands. */
  private def regenerated: List[MarkdownWriter.Page] = {
    val sources = Project.collect(Lib, None)

    sources should not be empty

    val parsed = sources.map(SyslParser.checked(_))

    parsed.collectFirst { case Left(ds) => ds } shouldBe None

    MarkdownWriter.pages(
      ApiModel.build(parsed.collect { case Right(p) => p }),
      Title,
      note = Some(Note),
      weight = Some(Weight),
    )
  }

  "the generated API section" - {

    "is what the library it was generated from would produce today" in {
      assume(isDirectory(Lib), "lib/ is not staged; see the site's CLAUDE.md")
      assume(isDirectory(Out), "the API section is not present")

      val stale = regenerated.filter { page =>
        val committed = s"$Out/${page.path}"

        !isFile(committed) || readFile(committed) != page.text
      }

      // Naming them rather than counting them: a reader of this failure has to go and regenerate,
      // and which pages moved is what says whether the change was the one they expected.
      withClue(s"stale or missing: ${stale.map(_.path).mkString(", ")} — regenerate the section. ") {
        stale shouldBe empty
      }
    }

    "carries no page the library no longer declares" in {
      assume(isDirectory(Out), "the API section is not present")

      val expected = regenerated.map(_.path).toSet
      val present  = listFiles(Out).toList.map(f => f.replace('\\', '/').split("/").last).filter(_.endsWith(".md"))

      withClue(s"left behind by a module that was renamed or removed: ") {
        present.filterNot(expected).toSet shouldBe empty
      }
    }

    "sets both per-page keys on every page it carries" in {
      // The two settings that decide whether the pages are *correct* rather than merely styled, and
      // neither is visible in a rendering that happens to look right. `headingShift: 0` keeps a
      // generated `##` an `<h2>` here as it is in the repository; `slugStyle: github` is what makes
      // a page's own symbol index resolve in both places.
      //
      // Asserted over the committed files rather than over `regenerated`, because what is served is
      // what is committed — the generator having the right behaviour is `DocGeneratorTests`' claim.
      assume(isDirectory(Out), "the API section is not present")

      for
        f <- listFiles(Out).toList.filter(_.endsWith(".md"))
        text = readFile(f)
      do
        withClue(s"$f: ") {
          text should include("headingShift: 0")
          text should include("slugStyle: github")
        }
    }

    "weighs its index and nothing else" in {
      // A weight orders sections against each other, and the module pages are inside one — a weight
      // on each of them would order them against their own index. Checked over the committed files
      // because that is what the site reads, and because the key's absence is the easy half to lose:
      // regenerating with `weight = None` would quietly drop it and break nothing else here.
      assume(isDirectory(Out), "the API section is not present")

      readFile(s"$Out/_index.md") should include(s"weight: $Weight")

      for
        f <- listFiles(Out).toList.filter(_.endsWith(".md")).filterNot(_.endsWith("_index.md"))
      do withClue(s"$f: ") { readFile(f) should not include "weight:" }
    }
  }
}
