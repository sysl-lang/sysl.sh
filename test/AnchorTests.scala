package sh.sysl

import io.github.edadma.cross_platform.*
import io.github.edadma.markdown.{EmojiConfig, MarkdownConfig, parseDocumentContent}

import sh.sysl.doc.MarkdownWriter

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/** Every in-page anchor the pages link to, checked against the headings that produce them.
 *
 * A deep link is the one kind of cross-reference that rots without saying so. A link to a page that
 * is gone gives the reader a 404, which somebody notices; a link to `#guards` on a page whose
 * heading was reworded to *Guarded arms* lands them at the **top of the right page**, reading the
 * wrong section and with nothing at all to tell them the link was stale. `DocsTests` cannot see it —
 * it compiles fenced programs and says nothing about prose — and the site builder renders a fragment
 * that matches no element without a word.
 *
 * So this walks the markdown rather than the rendered site, and that is deliberate. The rendered
 * pages carry the theme's own links as well as the author's, and the theme is the site builder's
 * business — a check over the output would report its links as this repository's failures, and
 * this repository could not fix them. **What is checked here is what somebody wrote in a page**,
 * which is exactly the set that can be fixed by editing one.
 *
 * The ids come from the same library that emits them, at the site's own settings (`config` below),
 * rather than from a slug function written a second time here. That matters more than it looks:
 * `smartPunctuation` turns `--` into an en dash and an apostrophe into `’` before the slug is taken,
 * so `## Checking a C struct's layout` is `checking-a-c-struct-s-layout` and not
 * `checking-a-c-structs-layout`, and an em dash in a heading contributes **one** hyphen and not two.
 * Both of those had a link written the way it reads rather than the way it renders.
 *
 * **Only links carrying a `#` are checked.** Whether every page a link names exists is a wider sweep
 * and a different job; an anchor is the half that is free to check here, because the page it is on
 * is the page whose headings are being read anyway.
 */
class AnchorTests extends AnyFreeSpec with Matchers with DocsSupport {

  private val Root = "docs/content"

  /** The markdown settings the site is built with.
    *
    * A second statement of the site builder's own configuration, in the way `GrammarTests` is a
    * second statement of the lexer's reserved words — and wrong in the same way if it drifts. What
    * keeps it honest is that every one of these flags changes a heading's text before it is
    * slugified, so a mismatch shows up as an anchor this suite cannot find and the site can.
    */
  private val config: MarkdownConfig =
    MarkdownConfig.default.copy(
      autoHeadingIds    = true,
      tables            = true,
      strikethrough     = true,
      taskListItems     = true,
      extendedAutolinks = true,
      footnotes         = true,
      smartPunctuation  = true,
      attributes        = true,
      callouts          = true,
      definitionLists   = true,
      emoji             = EmojiConfig.Unicode,
      math              = true,
    )

  /** Where a content file is published.
    *
    * `_index.md` is a section's own page and takes the directory's URL; everything else takes its
    * filename as a directory of its own. Both end in a slash, because that is how the pages link to
    * each other and comparing the two spellings of one address is not a thing worth doing twice.
    */
  private def urlOf(page: String): String =
    page.stripPrefix(s"$Root/").stripSuffix(".md") match {
      case "_index"                          => "/"
      case s if s.endsWith("/_index")        => s"/${s.stripSuffix("/_index")}/"
      case s                                 => s"/$s/"
    }

  /** The `id` of every heading on a page, which is the whole set an anchor may name.
    *
    * Nothing else on these pages emits one: no heading carries an explicit `{#id}`, no page defines
    * a footnote, and none writes raw HTML with an `id` on it. Should one start to, this is where it
    * would have to be taught about — a link to it would otherwise be reported as broken.
    */
  private def headingIds(source: String): Set[String] =
    deduped(parseDocumentContent(body(source), configFor(source)).headings.flatMap(_.attrs.flatMap(_.id)))

  /** The site builder gives a repeated heading id a numeric suffix, and so must this.
    *
    * Two headings with the same text slug the same, and two elements sharing an `id` is invalid
    * HTML — the second is unreachable — so juicer's `dedupeHeadingIds` renames it the way GitHub
    * does: the first `Buf` keeps `buf`, the second becomes `buf-1`. **Ids are assigned at parse time
    * and that pass runs afterwards**, so reading them straight off the parsed document gives the
    * pre-dedup set, in which `buf` appears twice and `buf-1` not at all.
    *
    * It went unnoticed until the generated API section, because a page needs two headings with the
    * same slug for the two answers to differ, and the hand-written pages have none — where a
    * *generated* one has them by construction: a type and the function that constructs it
    * conventionally share a name, which is `buf()` beside `Buf` and `map()` beside `Map`. The
    * suffixed link was correct on the site the whole time; it was this suite that could not see the
    * heading it named.
    *
    * Walking forward past an existing suffix rather than taking `id-1` outright is juicer's rule and
    * matters for the same reason it does there: a document carrying `buf` twice *and* a literal
    * `buf-1` must not hand out `buf-1` to both.
    */
  private def deduped(ids: List[String]): Set[String] = {
    val seen = scala.collection.mutable.HashMap.empty[String, Int]
    val out  = Set.newBuilder[String]

    for id <- ids do
      seen.get(id) match {
        case None => seen(id) = 0; out += id
        case Some(n) =>
          var next = n + 1

          while seen.contains(s"$id-$next") do next += 1

          seen(id) = next
          seen(s"$id-$next") = 0
          out += s"$id-$next"
      }

    out.result()
  }

  /** The settings one page is rendered with, which is `config` plus whatever its frontmatter says.
    *
    * **A page may name its own slug algorithm, and this suite has to slug the way that page is
    * slugged or it is checking a scheme nothing emits.** juicer's `slugStyle` is a per-page
    * frontmatter key as of 0.4.1, and the generated API section under `content/api/` sets it to
    * `github` on every page — because the same Markdown is read here and in the repository the
    * library lives in, and a symbol index that links to its own headings is dead in one of the two
    * if they disagree.
    *
    * **Note there is no `slugStyle` field to look for in `MarkdownConfig`.** The mechanism is
    * `slugify`, a function; juicer's key selects which one is passed in. Anybody grepping this file
    * for `slugStyle` before this existed found nothing and concluded there was no problem.
    *
    * **Calling `MarkdownWriter.slug` avoids writing a THIRD copy of the algorithm, and no more than
    * that — be precise about which agreement it buys.** It is the function the *generator* used to
    * write the anchors being checked, so the suite agrees with the links. What a reader needs is
    * agreement with the *renderer*, which is juicer's own `githubSlugify`, and that is a separate
    * implementation in a separate repository on a separate release cycle.
    *
    * **The two are identical today, condition for condition, and nothing reconciles them** — checked
    * by reading both, which is the `config` val's relationship with the site's settings rather than
    * `GrammarTests`' with `SyslLexical`. `GrammarTests` reconciles; this agrees by inspection. The
    * same is true a second time for the `-1` numbering below, which `MarkdownWriter` implements and
    * juicer's dedup pass implements again.
    *
    * If either copy moves, this suite follows the compiler's and the site does something else — and
    * it is green while the pages are wrong. `sysl.sh` cannot call juicer's copy today, since it
    * depends on the compiler and not on juicer-core; whoever next touches either algorithm owes the
    * other one a look.
    */
  private def configFor(source: String): MarkdownConfig =
    if slugStyleOf(source).contains("github") then config.copy(slugify = MarkdownWriter.slug)
    else config

  /** A page's `slugStyle`, if its frontmatter names one.
    *
    * Read with a line match rather than a YAML parser, because the frontmatter here is flat and one
    * key deep — the same reason `body` below reads it by fences.
    */
  private def slugStyleOf(source: String): Option[String] = {
    val lines = source.linesIterator.toList

    if !lines.headOption.contains("---") then None
    else
      lines.tail
        .takeWhile(_ != "---")
        .collectFirst { case l if l.startsWith("slugStyle:") =>
          l.stripPrefix("slugStyle:").trim.stripPrefix("\"").stripSuffix("\"")
        }
  }

  /** A page without its frontmatter.
    *
    * Not cosmetic: the `---` fences read as a thematic break and a setext underline, so a page left
    * whole gains a heading made out of its own metadata — `title: Traits` — with an id to match.
    */
  private def body(source: String): String = {
    val lines = source.linesIterator.toList

    if lines.headOption.contains("---") then lines.tail.dropWhile(_ != "---").drop(1).mkString("\n")
    else source
  }

  /** A page's prose, with the fenced blocks taken out.
    *
    * A program that prints `](#0)` is not a link, and neither is a shell transcript. The same
    * left-margin rule `DocsSupport` uses opens a block, for the same reason.
    */
  private def prose(source: String): String = {
    val kept = List.newBuilder[String]
    var open = false

    for line <- source.linesIterator do
      if line.startsWith("```") then open = !open
      else if !open then kept += line

    kept.result().mkString("\n")
  }

  /** One link written on a page, as its destination reads. */
  private case class Reference(page: String, dest: String) {
    def display: String = s"$page: [..]($dest)"
  }

  private val destination = """\]\(\s*([^)\s]+)""".r

  private def referencesOn(page: String): List[Reference] =
    destination.findAllMatchIn(prose(readFile(page))).map(m => Reference(page, m.group(1))).toList

  /** The page a destination names, as a URL, resolved the way a browser would.
    *
    * Three forms appear: a fragment alone, which is this page; a root-relative path, which is what
    * the site's cross-page links are written as; and a path relative to the current page.
    */
  private def target(ref: Reference): String = {
    val path = ref.dest.takeWhile(_ != '#')
    val here = urlOf(ref.page)

    if path.isEmpty then here
    else {
      val absolute = if path.startsWith("/") then path else here + path

      // `..` and `.` are folded here rather than left to a comparison against a key that has neither.
      // An excess `..` is kept rather than clamped, so a path that walks off the top fails as a page
      // that is not published — which is what it is.
      val walked = absolute.split("/").filter(s => s.nonEmpty && s != ".").foldLeft(List.empty[String]) {
        case (head :: rest, "..") if head != ".." => rest
        case (up, segment)                        => segment :: up
      }

      if walked.isEmpty then "/" else walked.reverse.mkString("/", "/", "/")
    }
  }

  private lazy val idsByUrl: Map[String, Set[String]] =
    pages.map(page => urlOf(page) -> headingIds(readFile(page))).toMap

  /** What is wrong with a reference, if anything. */
  private def complaint(ref: Reference): Option[String] = {
    val anchor = ref.dest.dropWhile(_ != '#').drop(1)

    if !ref.dest.contains('#') then None
    else if anchor.isEmpty then Some(s"${ref.display} names no heading at all")
    else
      idsByUrl.get(target(ref)) match {
        case None => Some(s"${ref.display} is on no page — ${target(ref)} is not published")

        case Some(ids) if !ids(anchor) =>
          val near = ids.toList.sorted.filter(id => id.replace("-", "").contains(anchor.replace("-", "")) ||
            anchor.replace("-", "").contains(id.replace("-", "")))

          Some(
            s"${ref.display} names no heading on ${target(ref)}" +
              (if near.isEmpty then "" else near.mkString(" — did you mean ", ", ", "?")),
          )

        case Some(_) => None
      }
  }

  "every anchor a page links to is a heading on the page it names" in {
    assume(isDirectory(Root), "the docs tree is not reachable from the working directory")

    // Every page is walked and every complaint collected, rather than failing at the first. One
    // reworded heading breaks the links from several pages at once, and a suite that stopped at the
    // first would take as many runs to find them as there were links.
    val broken = pages.flatMap(referencesOn).flatMap(complaint)

    if broken.nonEmpty then
      fail(broken.mkString(s"${broken.length} anchors land nowhere:\n\n    ", "\n    ", "\n"))
  }

  /** The other half, and the reason the half above means anything.
    *
    * A check over links alone passes on a site with no links in it, and it would go on passing while
    * a page's cross-references were deleted one at a time. The count is what notices — and the
    * headings count with it, because a run that found no headings anywhere would report every anchor
    * as fine.
    */
  "the pages carry the cross-references and the headings they are supposed to" in {
    assume(isDirectory(Root), "the docs tree is not reachable from the working directory")

    // 59 and 765 when this was written. The floors are well under both, because their job is to
    // catch collection breaking outright rather than to record a census — a number that has to be
    // raised whenever a page gains a link is a number somebody raises without reading.
    val anchored = pages.flatMap(referencesOn).count(_.dest.contains('#'))
    val headings = idsByUrl.values.map(_.size).sum

    withClue("anchored links found: ") { anchored should be >= 40 }
    withClue("headings found: ")       { headings should be >= 600 }
  }
}
