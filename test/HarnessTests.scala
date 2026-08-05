package sh.sysl

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/** The harness checking itself, rather than the pages.
 *
 * **`target=` cannot be tested by a page.** A page that carries it is checked on whatever machine
 * the suite is running on, and on the author's that is `aarch64-macos` — the very target the pages
 * name — so the option would appear to work just as well if it were being thrown away. The failure
 * it exists to prevent shows up only on a different machine, which is where nobody is looking when
 * they write the page.
 *
 * So the discriminating test is here: one program, compiled twice, and the two machines have to
 * disagree about it. That is a claim about the option rather than about either answer, and it holds
 * on any host.
 */
class HarnessTests extends AnyFreeSpec with Matchers with DocsSupport {

  /** Refused on both machines and for unrelated reasons: AArch64 has no interrupt concept at all,
   * while x86-64 has one whose handler must take the frame the processor pushed. A page quoting
   * either diagnostic is wrong on the other machine, which is the whole problem.
   */
  private val interrupt =
    """interrupt timer()
      |    print("tick")
      |""".stripMargin

  private val aarch64Says = "'interrupt' is not something aarch64 has"

  private def snippet(claim: Claim, target: Option[String]) =
    Snippet("<test>", 1, interrupt, claim, target)

  "a block that names a target" - {

    "is compiled for that machine and not for this one" in {
      // The aarch64 diagnostic, demanded of a build for aarch64 -- which passes wherever this runs.
      check(snippet(Claim.Refused(aarch64Says), Some("aarch64-macos"))) shouldBe None
    }

    "is really compiled for it, because another machine refuses the same program differently" in {
      // The half that makes the test above mean something. If `target=` were ignored, both of these
      // would answer the same way and the suite would be pinning nothing at all.
      val complaint = check(snippet(Claim.Refused(aarch64Says), Some("x86_64-linux")))

      complaint should not be empty
      complaint.get should include("not in the words the page quotes")
      complaint.get should include("x86-64")
    }

    "is refused outright when the block has to run, rather than quietly checked against the host" in {
      // Compiling for another machine is honest; running on it is not something this machine can do.
      // Ignoring the option here would check the program against the runner while the page said it
      // was about somewhere else, which is the confusion `target=` exists to remove.
      val complaint = check(snippet(Claim.Prints("tick"), Some("aarch64-macos")))

      complaint should not be empty
      complaint.get should include("'target=' belongs on a block with an 'error' under it")
    }

    "reports an unknown name as the page's mistake, with what is on offer" in {
      val complaint = check(snippet(Claim.Refused(aarch64Says), Some("pdp11-unix")))

      complaint should not be empty
      complaint.get should include("unknown target 'pdp11-unix'")
    }
  }

  "a block that names no target" - {

    "is compiled for the machine the suite is running on, as every page always has been" in {
      // The default has to stay the host: nearly every `error` block on the site is a fact about the
      // language rather than about a processor, and none of them says a target.
      val complaint = check(snippet(Claim.Refused(aarch64Says), None))

      if Target.default == Target.aarch64MacOS then complaint shouldBe None
      else complaint.get should include("not in the words the page quotes")
    }
  }
}
