package io.github.edadma.sysl

import io.github.edadma.cross_platform.*

import org.scalatest.Assertions
import org.scalatest.matchers.should.Matchers

/** Runs the programs printed on the documentation site, which are source on disk like `guide/` is —
 * except that here the file the reader sees and the file the compiler is handed are the *same
 * bytes*.
 *
 * A tutorial's examples rot silently. Nothing in an ordinary build reads them, so a page goes on
 * claiming what the language did a year ago and the first person to find out is a reader who typed
 * it in and got a diagnostic. That failure is worse than no page at all: it costs the reader their
 * confidence that anything else on the site is true either.
 *
 * So a fenced block is not a picture of a program, it *is* the program. Which one it is comes from
 * what follows it, which is also how it reads to somebody scrolling past:
 *
 *   - a `sysl` block followed by an `output` block is a whole program, and the output block is what
 *     it prints — compiled, run, and compared;
 *   - a `sysl` block followed by an `error` block is a program the compiler **refuses**, and the
 *     error block is what it says — compiled, required to fail, and the diagnostic required to
 *     contain that text;
 *   - a `sysl` block with neither under it is a fragment, quoted for its shape, and is not run.
 *
 * The refusals matter as much as the acceptances. A language is defined as much by what it will not
 * let you write, and a page that shows only working code teaches half of it — so the surface that
 * checks a refusal is here from the start rather than bolted on once somebody wants it.
 */
trait DocsSupport extends Matchers { this: Assertions =>

  /** What a `sysl` block on a page claims about itself. */
  enum Claim {
    /** A whole program, and what running it prints. */
    case Prints(output: String)

    /** A program the compiler refuses, and what its diagnostic says. */
    case Refused(message: String)

    /** A fragment, quoted to make a point about shape. Nothing is asserted about it. */
    case Fragment
  }

  /** One `sysl` block, with where it came from — a failure has to be able to name the page and the
   * position on it, because a reader of the failure has only the page to go and fix.
   *
   * `target` is the machine the block is *about*, from a `target=` on the fence, and is `None` for
   * the ordinary case of a block about the language rather than about a processor.
   */
  case class Snippet(page: String, nth: Int, source: String, claim: Claim, target: Option[String]) {
    def display: String = s"$page, sysl block $nth"
  }

  /** A fenced block as the markdown holds it: the word after the backticks, whatever else was on
   * that line, and the body.
   */
  private case class Fence(lang: String, info: String, body: String) {

    /** The `key=value` pairs after the language word. Anything else on the line is ignored, since
     * the info string is also where a highlighter's own words go.
     */
    def options: Map[String, String] =
      info.split("\\s+").filter(_.contains('=')).map(_.split("=", 2)).collect { case Array(k, v) =>
        k -> v
      }.toMap
  }

  /** Every markdown page under `docs/content`, deepest last, so a failure list reads in the order a
   * reader would meet the pages.
   */
  protected def pages: List[String] = {
    def walk(dir: String): List[String] = {
      val entries = listFiles(dir).toList.sorted

      entries.filter(f => isFile(f) && f.endsWith(".md")) :::
        entries.filter(isDirectory).flatMap(walk)
    }

    walk(Root).map(relative)
  }

  private val Root = "docs/content"

  /** A page named the way the site names it, whatever the platform handed back.
   *
   * `listFiles` answers in absolute paths, and an absolute path is the working directory of
   * whoever ran the suite — so a failure would quote a path meaningless to anyone else, and the
   * expected-page list in `DocsTests` would be a list of one machine's directory layout.
   */
  private def relative(path: String): String = {
    val at = path.replace('\\', '/').indexOf(Root)

    if at >= 0 then path.replace('\\', '/').substring(at) else path
  }

  /** The `sysl` blocks of one page, each paired with what the page claims about it. */
  protected def snippets(page: String): List[Snippet] = {
    val fences = fencesOf(readFile(page))

    // `zipWithIndex` over the sysl fences alone, not over every fence: the number a failure quotes
    // is the number a reader counts on the page, and they do not count the bash and output blocks.
    fences.zipWithIndex
      .filter { case (f, _) => f.lang == "sysl" }
      .map { case (f, i) => (f, i) }
      .zipWithIndex
      .map { case ((fence, position), nth) =>
        Snippet(page, nth + 1, fence.body, claimAfter(fences.drop(position + 1)), fence.options.get("target"))
      }
  }

  /** What the fences after a `sysl` block say about it: the first `output` or `error` block to
   * appear before the next `sysl` block.
   *
   * Not simply the very next fence, because a page reads *program, how to run it, what it prints* —
   * with a `bash` block in the middle — and a rule that could not see past that would push authors
   * into writing the pages in an order that suits the harness. What the rule does refuse is an
   * `output` block that belongs to a *later* program: the search stops at the next `sysl` block, so
   * a claim can never be attached to the wrong one.
   */
  private def claimAfter(rest: List[Fence]): Claim =
    rest.takeWhile(_.lang != "sysl").find(f => f.lang == "output" || f.lang == "error") match {
      case Some(Fence("output", _, body)) => Claim.Prints(body)
      case Some(Fence("error", _, body))  => Claim.Refused(body)
      case _                           => Claim.Fragment
    }

  /** The fenced blocks of a markdown document, in order.
   *
   * Only a fence at the left margin opens a block. An indented one is inside a list item or a block
   * quote, where the surrounding prose owns it — and a program indented under a bullet is being
   * quoted by the prose rather than offered as something to run.
   */
  private def fencesOf(text: String): List[Fence] = {
    val lines   = text.linesIterator.toList
    val fences  = List.newBuilder[Fence]
    var open    = false
    var lang    = ""
    var rest    = ""
    val body    = StringBuilder()

    for (line <- lines)
      if line.startsWith("```") then
        if open then
          fences += Fence(lang, rest, body.toString)
          body.clear()
          open = false
        else
          val info = line.stripPrefix("```").trim
          lang = info.takeWhile(!_.isWhitespace)
          rest = info.drop(lang.length).trim
          open = true
      else if open then body ++= line + "\n"

    fences.result()
  }

  /** Checks one snippet, returning the complaint if it has one.
   *
   * Every snippet answers rather than throwing, so one run reports **every** page that has drifted
   * instead of the first. A tutorial breaks in groups — one language change invalidates the six
   * pages that lean on it — and a suite that stops at the first would take six runs to find them,
   * each one six minutes long.
   */
  protected def check(s: Snippet): Option[String] = s.claim match {
    case _ if s.target.isDefined && !s.claim.isInstanceOf[Claim.Refused] =>
      // A `target=` says "this block is about that machine", and the only honest thing that can be
      // done with a block about another machine is compile it. Running it would need that machine.
      // So this is refused rather than ignored: a page that pinned a target on something it expects
      // to print would otherwise be silently checked against the runner instead, which is exactly
      // the confusion `target=` exists to remove.
      Some(s"${s.display}: 'target=' belongs on a block with an 'error' under it, which is compiled and not run")

    case Claim.Fragment => None

    case Claim.Prints(expected) =>
      compileAndRun(s.source) match {
        case Left(err) =>
          Some(s"${s.display}: expected it to run, but it did not compile:\n$err")

        case Right((code, _)) if code != 0 =>
          Some(s"${s.display}: expected it to run, but it exited with $code")

        case Right((_, out)) if normalize(out) != normalize(expected) =>
          Some(
            s"${s.display}: what it printed is not what the page says.\n" +
              s"page:\n${indent(expected)}\nprogram:\n${indent(out)}"
          )

        case Right(_) => None
      }

    case Claim.Refused(expected) =>
      refusalTarget(s) match {
        case Left(why) => Some(s"${s.display}: $why")
        case Right(target) =>
          Compiler.compileToLlvm(s.source, target = target) match {
            case Right(_) =>
              Some(s"${s.display}: the page says the compiler refuses this, and it compiled")

            case Left(diagnostic) if !diagnostic.contains(expected.trim) =>
              Some(
                s"${s.display}: the compiler refused it, but not in the words the page quotes.\n" +
                  s"page:\n${indent(expected)}\ncompiler:\n${indent(diagnostic)}"
              )

            case Left(_) => None
          }
      }
  }

  /** The machine a refusal is about: what the fence named, or the one this suite is running on.
   *
   * **This exists because some refusals are facts about a processor rather than about the
   * language.** `interrupt` is refused on AArch64 because AArch64 has no such concept, and refused
   * on x86-64 for the unrelated reason that its handler must take the frame the hardware pushed —
   * two correct diagnostics, and a page quoting either one is wrong on the other machine. Before
   * `target=`, such a page passed on its author's laptop and failed in CI, which reads as a broken
   * page rather than as a page that never said which machine it meant.
   *
   * An unknown name is a failure of the page and not of the compiler, so it is reported the same way
   * a wrong diagnostic is — `Target.named` already answers with the list of what it knows.
   */
  private def refusalTarget(s: Snippet): Either[String, Target] =
    s.target.fold(Right(Target.default))(Target.named)

  /** The compilation an ordinary `sysl run` performs — against the prebuilt standard module where
   * the toolchain can build one.
   *
   * It goes through `Stdlib.resolve`, which is the call the driver itself makes, so a page is
   * compiled against the artifact an ordinary build would find, at the path an ordinary build would
   * find it. That is the whole of what this harness needs from the compiler, and it is why these
   * pages can live in a repository the compiler knows nothing about.
   */
  private def compileAndRun(src: String): Either[String, (Int, String)] =
    Stdlib.resolve(Stdlib.Choice.Default(), Target.default) match {
      case Right(Stdlib.Resolved(std, precompiled, Some(archive))) =>
        Toolchain.compileAndRun(List(Source("<page>", src)), Nil, Nil, Some(std), precompiled, List(archive))
      case _ =>
        Toolchain.compileAndRun(List(Source("<page>", src)), Nil, Nil, None, Set.empty, Nil)
    }

  /** Trailing whitespace is not a claim a page is making. A markdown fence keeps a final newline
   * that the author did not type and cannot see, so comparing raw text would fail every snippet for
   * a reason no reader could act on.
   */
  private def normalize(s: String): String = s.linesIterator.map(_.replaceAll("\\s+$", "")).mkString("\n").trim

  private def indent(s: String): String = s.linesIterator.map("    " + _).mkString("\n")
}
