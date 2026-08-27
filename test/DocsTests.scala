package sh.sysl

import io.github.edadma.cross_platform.*

import org.scalatest.{ParallelTestExecution, Suite}
import org.scalatest.freespec.AnyFreeSpec

/** The documentation site's programs, compiled and run out of the markdown a reader is looking at
 * (see `DocsSupport`).
 *
 * Two things are asserted, and the second is what makes the first mean anything. Every program on a
 * page does what the page says it does — and the **number** of programs on each page is the number
 * written down here. A fence that stopped being recognised, a page that lost its `output` block in
 * an edit, a chapter quietly reduced to prose: each of those turns a checked program into an
 * unchecked one, and without the count they all look exactly like a page whose programs pass.
 *
 * That is the same reason `GuideTests` counts the `ok` lines rather than only looking for `FAIL`,
 * and it was learned there the hard way.
 */
class DocsTests extends AnyFreeSpec with DocsSupport with ParallelTestExecution {

  /** Only the JVM can supply this by reflection; on JS and Native it is abstract, so a suite that
   * runs its tests in parallel has to say how one of itself is made.
   */
  override def newInstance: Suite & ParallelTestExecution = new DocsTests

  /** How many `sysl` blocks of each kind a page carries: runnable programs, refusals, fragments.
   *
   * Written here rather than derived, because deriving it from the page would assert that the page
   * agrees with itself — which it always does.
   */
  private val expected: Map[String, (Int, Int, Int)] = Map(
    "docs/content/_index.md"                          -> (0, 0, 1),
    "docs/content/getting-started/_index.md"          -> (0, 0, 0),
    "docs/content/getting-started/installation.md"    -> (0, 0, 0),
    "docs/content/getting-started/first-program.md"   -> (1, 0, 1),
    "docs/content/getting-started/cli.md"             -> (0, 0, 0),
    "docs/content/getting-started/from-c.md"          -> (7, 2, 1),
    "docs/content/getting-started/stability.md"       -> (0, 0, 0),
    "docs/content/tour/_index.md"                     -> (0, 0, 0),
    "docs/content/tour/values.md"                     -> (5, 1, 0),
    "docs/content/tour/control-flow.md"               -> (8, 0, 0),
    "docs/content/tour/functions.md"                  -> (9, 0, 0),
    "docs/content/tour/structs.md"                    -> (5, 0, 0),
    "docs/content/tour/memory.md"                     -> (7, 0, 0),
    "docs/content/tour/arrays.md"                     -> (7, 1, 0),
    "docs/content/tour/strings.md"                    -> (8, 1, 0),
    // One more runnable: a variant may be written with the enum's name left off where the context
    // says which enum is meant, and the tour is where a reader meets the spelling first.
    "docs/content/tour/enums.md"                      -> (8, 1, 0),
    "docs/content/tour/errors.md"                     -> (6, 1, 2),
    "docs/content/tour/traits.md"                     -> (8, 1, 0),
    "docs/content/tour/modules.md"                    -> (3, 0, 3),
    "docs/content/tour/contracts.md"                  -> (7, 1, 0),
    "docs/content/tour/capstone.md"                   -> (1, 0, 1),
    "docs/content/reference/_index.md"                -> (0, 0, 0),
    // Two more runnable and one more refusal: a line may now continue the one above it by starting
    // with a dot, so the page shows a chain broken before the dot, refuses the trailing spelling,
    // and shows the `match` whose arms the rule's second half is what keeps.
    "docs/content/reference/lexical.md"               -> (16, 3, 3),
    // One more runnable: the two callable spellings differ in what a call costs and in what the
    // declaration becomes, which is a rule about *types* — and a trait's member may write either,
    // so the page shows one taken on a value and through a bound.
    "docs/content/reference/types.md"                 -> (20, 3, 0),
    // Six more runnable and two more refusals: `with` — a struct again with some of its fields
    // changed — is a postfix tail of its own, and the two refusals are the readings that would
    // otherwise be silently wrong (a reference rather than a struct) and silently pointless (one
    // field changed twice). Three more runnable and two more refusals for the trailing block, and
    // one more of each again for the identities: every integer type is a member of `Zero` and `One`
    // now, so a generic sum runs over an `int` and over a `real` from one body, and a constrained
    // subtype is refused by name because a range need not hold the value.
    // And one more of each again for ranges: a range with both ends written is a value now, so the
    // section shows one bound, passed and returned, and refuses the two readings that have no value
    // — bounds that disagree, and an end left open.
    "docs/content/reference/expressions.md"           -> (41, 22, 0),
    // One more runnable and one more refusal: a `for` may take its element apart with the pattern a
    // binding takes, and the comma spelling is refused because a three-clause header already begins
    // that way.
    "docs/content/reference/statements.md"            -> (17, 4, 1),
    // Two more runnable: a default is read at the type its parameter declares, which is what lets a
    // method take a bare `None` and a callable parameter default to a closure — so the section shows
    // one of each being taken and then written over.
    "docs/content/reference/declarations.md"          -> (33, 11, 1),
    // Two more runnable and one more refusal: a binding a match makes is written once, so the page
    // refuses the write, runs the 'var' taken from the binding — which is what makes the copy
    // visible — and runs the edge where the payload is a '&T' and the store goes through after all.
    "docs/content/reference/patterns.md"              -> (14, 13, 0),
    "docs/content/reference/memory.md"                -> (34, 22, 0),
    // One more runnable: an `error` block's diagnostic names a spelling to write, and the page now
    // runs that spelling instead of leaving it as prose nothing compiles.
    // One more running and one more refused: the two views of a slice meet at the read-only one
    // wherever two types have to agree, so the page shows the meeting, and refuses the write through
    // what it met at — since a meeting that produced a writable view would be the hole the bit exists
    // to stop.
    "docs/content/reference/arrays.md"                -> (24, 10, 1),
    "docs/content/reference/vectors.md"               -> (14, 8, 0),
    // One more runnable: an `error` block's diagnostic names a spelling to write, and the page now
    // runs that spelling instead of leaving it as prose nothing compiles.
    "docs/content/reference/strings.md"               -> (23, 7, 1),
    // One more of each: a member that declares type parameters of its own is left out of the table
    // rather than out of the object, so the page shows an object forming on such a trait and
    // dispatching everything else, and refuses the one member on it.
    //
    // And the associated-type section on top of that: four more running and seven more refused,
    // since a feature whose whole point is what an implementation may leave unwritten owes its
    // edges as programs.
    //
    // And the two views of a slice on top of that: two more running and one more refused, since a
    // block for `[]T` reaches a `[]const T` receiver now — so the page shows the covering, the write
    // that the read-only instance refuses, and the same member writing freely on a `[]int`.
    "docs/content/reference/traits.md"                -> (39, 34, 0),
    // One more runnable: a type parameter is solved to the type that was written, so a transparent
    // subtype reaches one and the two routes that say which type a call is at agree on the page.
    // One more again, and one more refusal, for the same rule seen from here: a trait's member may
    // declare type parameters of its own now, so the pack section's refusal became a program — and
    // the two things that rule costs, the member on an object and an object standing at a bound on
    // its own trait, are each refused by name.
    // Two more runnable and one more refusal again: the list is the parameters the author wrote, so
    // the arrow's own is not among them — shown with a call that writes both of a two-arrow
    // declaration's, and with the address form, which is refused by name because it has nothing to
    // read the callable's type off.
    // And two more runnable for the other half of the same machinery: a callable is held back like a
    // 'null' and contributes on the way back, so one arrow's result is what another takes — written
    // both ways round, since neither order is privileged.
    "docs/content/reference/generics.md"              -> (32, 17, 0),
    // One more of each: a constant may be declared at a transparent subtype now, so the `const`
    // section shows one in range and refuses one outside it.
    // Two more fragments: the shipping file and the `@tests` file of one module, which is a shape no
    // single block can be — a page block is one file, and the rule is about two.
    "docs/content/reference/modules.md"                -> (17, 15, 16),
    // Every block on this page is `hocon` or `text` — what a manifest says and what a resolution
    // comes to are not sysl, and the one sysl-shaped line on it is a fragment of a call.
    "docs/content/reference/packages.md"               -> (0, 0, 0),
    // One more runnable: an `error` block's diagnostic names a spelling to write, and the page now
    // runs that spelling instead of leaving it as prose nothing compiles.
    "docs/content/reference/errors.md"                 -> (25, 27, 2),
    // One more of each: `c type` measures a typedef, so the page gained a program that uses one, a
    // refusal for the pointer it will not resolve, and the FreeRTOS extern it exists for. One more
    // again for a `c const` declared *at* a measured type, which is the pair the two blocks are, and
    // the range refusal that comes with a constrained one.
    // An `@export` publishes a C-convention entry rather than renaming the definition, so an
    // aggregate crosses: one more refusal, for the struct field that still does not, and two more
    // fragments — the `extern` and the `@export` whose addresses a C library may now be handed.
    // A struct now names itself in the header: one more runnable, showing that the sysl side is
    // unaffected by the name it chose, and three more refusals — the namespace C shares between a
    // typedef and a function, a generic struct, and a private one.
    "docs/content/reference/ffi.md"                    -> (21, 31, 12),
    "docs/content/reference/inline-assembly.md"        -> (3, 3, 6),
    // One more runnable: a `volatile` bitfield is a volatile access of its container, so the block
    // that asserted a refusal is now a register written through and read back. One more refusal:
    // that a member takes no annotation is a sentence now, so the page can show it being said.
    // Two more runnable again: a bound asked through a type parameter answers the subtype the
    // parameter was solved to — the one answer a transparent subtype and its base differ on — and a
    // subtype that narrows nothing answers its base's, which is what a measured typedef is.
    "docs/content/reference/attributes.md"             -> (25, 32, 6),
    "docs/content/reference/verification.md"           -> (15, 5, 1),
    "docs/content/library/_index.md"                   -> (0, 0, 0),
    "docs/content/guides/_index.md"                    -> (0, 0, 0),
    "docs/content/guides/ring.md"                      -> (0, 0, 0),
    "docs/content/guides/slab.md"                      -> (0, 0, 1),
    // One more runnable: an `error` block's diagnostic names a spelling to write, and the page now
    // runs that spelling instead of leaving it as prose nothing compiles.
    "docs/content/library/core.md"                     -> (31, 6, 11),
    "docs/content/library/text.md"                      -> (18, 6, 4),
    "docs/content/library/regex.md"                      -> (16, 0, 0),
    "docs/content/library/buf.md"                       -> (11, 6, 3),
    "docs/content/library/container.md"                -> (5, 0, 3),
    // One more runnable: `bytes_reader` and `bytes_writer`, the in-memory pair that arrived with the
    // line editor and made anything taking a `*Reader` testable without a descriptor.
    "docs/content/library/io.md"                        -> (7, 3, 3),
    "docs/content/library/fs.md"                        -> (7, 5, 3),
    // One more runnable: an `error` block's diagnostic names a spelling to write, and the page now
    // runs that spelling instead of leaving it as prose nothing compiles.
    "docs/content/library/math.md"                      -> (23, 10, 3),
    "docs/content/library/complex.md"                   -> (8, 1, 1),
    // One refusal became a runnable program when `Sub` grew an `Out`: the difference of two instants
    // is the operator now, so the block that asserted it was refused runs instead. The clock section
    // then added one of each: the two readings, and the refusal that says a `Duration` is not a date.
    // One more runnable: a zone is anything callable, so a closure answers `resolve` as a declaration
    // does — which is what a fixed offset, or one read out of a captured table, is written as.
    "docs/content/library/time.md"                       -> (22, 4, 4),
    "docs/content/library/env.md"                        -> (1, 0, 0),
    "docs/content/library/process.md"                    -> (5, 0, 0),
    "docs/content/library/sync.md"                       -> (9, 7, 2),
    // One refusal became a runnable program when `null` learned to wait for the argument that
    // settles the parameter: a thread body with nothing of its own is now passed one.
    "docs/content/library/threads.md"                     -> (9, 7, 2),
    // Two more runnable: taking the terminal over (`raw`/`cooked`, whose program takes the *declining*
    // branch here, since these run with input closed) and the line editor over an in-memory pair.
    "docs/content/library/term.md"                        -> (4, 0, 2),
    "docs/content/library/slices.md"                      -> (9, 0, 0),
    // The fragment is the trait itself, listed rather than run: what `sysl.seq` is, is its ten
    // signatures, and a page that only showed calls would never show the two members whose type
    // parameter is their own.
    // One more runnable and one more refusal: `generate` makes a sequence out of a count, which is
    // the one thing in the module that is not a member — a creator has no receiver — and the
    // refusal is the spelling a reader reaches for first, since `(0..<n).map(f)` cannot be written
    // at all: a range is not a value.
    // Two more runnable and one refusal fewer: `(0..<n).map(f)` is a program rather than the thing
    // the page said could not be written, and the block that asserted a range is refused is what it
    // replaced.
    "docs/content/library/seq.md"                         -> (10, 2, 1),
    "docs/content/library/encoding.md"                    -> (6, 0, 0),
    "docs/content/library/crypto.md"                   -> (7, 0, 0),
    "docs/content/library/rand.md"                        -> (5, 0, 0),
    "docs/content/library/args.md"                        -> (10, 4, 1),
    // The four runnable ones are real suites whose report is checked to the byte, which is possible
    // only because the file a snippet compiles as is `<page>` and the line is the block's own: a
    // harness report is a file, a line, a name and a verdict, and three of those are the page's.
    "docs/content/library/harness.md"                     -> (4, 0, 2),
    "docs/content/library/sys.md"                          -> (4, 3, 1),
  )

  "every page on the site is accounted for" in {
    assume(isDirectory("docs/content"), "the docs tree is not reachable from the working directory")

    // A new page with no entry is not a failure of that page — it is a page nobody decided the
    // shape of, and the decision is the point. Left to default, an unlisted page would contribute
    // its programs to the run and none of the counts that keep them honest.
    written.toSet shouldBe expected.keySet
  }

  /** One test per **program**, rather than one test over every page — which is what lets the suite
   * run them at the same time.
   *
   * **The cost here is not the checking, it is the toolchain.** A page's `output` block makes its
   * program a whole compile, link and run: a clang invocation, a linker invocation and a process,
   * for a program of six lines. There are close to four hundred of those across the site, and as one
   * test they were four hundred of them in a row on one std while the rest of the machine idled.
   * They are completely independent — separate temporary files, separate processes, nothing shared —
   * so the only thing that made them sequential was being written inside a single `in`.
   *
   * **The unit is a program and not a page, because a page is not small enough.** Splitting by page
   * first was the obvious step and it stalled at sixty seconds, all but one of which was
   * `reference/errors.md` — forty-seven programs on one page, nineteen of them a full build, taking
   * forty-nine seconds on whichever single thread drew it while the rest of the machine finished
   * and waited. A run is only as short as its longest test, so the longest test has to be one
   * program. The `error` blocks come along at no cost either way: a refusal never reaches the
   * toolchain at all (`check`).
   *
   * A failure still names the page, because that is what a reader goes and fixes; the ordinal beside
   * it is the same one the census counts in, so it says which block without anyone counting fences.
   */
  for
    page <- if isDirectory("docs/content") then written else Nil
    s    <- snippets(page)
  do
    s"${s.page} program ${s.nth} does what the page says" in {
      assume(Toolchain.clangAvailable, "clang not available")

      check(s).foreach(fail(_))
    }

  "each page carries the programs it is supposed to" in {
    assume(isDirectory("docs/content"), "the docs tree is not reachable from the working directory")

    val counted = written.map { page =>
      val found = snippets(page)

      page -> (
        found.count(_.claim.isInstanceOf[Claim.Prints]),
        found.count(_.claim.isInstanceOf[Claim.Refused]),
        found.count(_.claim == Claim.Fragment),
      )
    }.toMap

    counted shouldBe expected
  }
}
