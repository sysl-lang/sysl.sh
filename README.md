# sysl.sh

The documentation site for [sysl](https://github.com/sysl-lang/sysl), and the tests that keep it
honest.

**A fenced block on a page is not a picture of a program — it is the program.** Every ```sysl block
in `docs/content/` is compiled by the real compiler, and what it claims comes from what follows it:

| followed by | what happens |
|---|---|
| ` ```output ` | compiled, linked, run — its stdout must match |
| ` ```error `  | compiled, and must be **refused**, with a diagnostic containing that text |
| neither       | a fragment: highlighted, not run |

So a page cannot quietly rot into describing a language that no longer exists. That is the whole
reason the tests live beside the pages rather than in the compiler's repository.

## Layout

```
docs/       the site — content, static assets, the juicer theme, the highlighting grammar
test/       the suites that run it
lib/        the standard library's source, unpacked by CI (not committed)
```

There is no `main/`. The compiler is a dependency, so the only Scala here is tests.

## The version this site documents

`build.sbt` names one build of the compiler in `syslVersion`, and the pages are checked against
**that**. Which build depends on the branch, and the two branches here are the whole of the split:

- **`dev`** may name an **interim** — `0.0.41-abc1234`, published to GitHub Packages from a push to
  the compiler's own `dev`, whose suffix is the commit it was built from. That is what lets a page be
  written and verified as a feature lands rather than at a release.
- **`stable`** names a **release**, and is the branch Pages deploys. A website documents the released
  language; a page demonstrating unreleased behaviour would be wrong for the reader, which is why it
  is not the branch anybody reads.

Keeping up is therefore a step of every sysl release *and* of every feature: raise `syslVersion` on
`dev` with the pages that needed it, and at a release raise it to the release and merge `dev` into
`stable`.

## Running the tests

They need a toolchain, because they build and run real programs:

- `clang` and `llvm-ar`
- the standard library's source in `lib/`. The published jar carries the compiler and not the
  library, which the compiler reads off disk — so take it from the compiler's repository **at the
  commit `syslVersion` names**, which for a release is the tag and for an interim is the sha in the
  version's own suffix:

```sh
VERSION=$(sed -n 's/^val syslVersion = "\(.*\)"/\1/p' build.sbt)
case "$VERSION" in *-*) REF="${VERSION#*-}" ;; *) REF="v$VERSION" ;; esac

git clone --filter=blob:none --sparse https://github.com/sysl-lang/sysl.git sysl-src
git -C sysl-src checkout "$REF"
git -C sysl-src sparse-checkout set library lib
mv sysl-src/library lib 2>/dev/null || mv sysl-src/lib lib
```

The directory is `library/` in the compiler's tree and was `lib/` before the rename, which is why
both are asked for. What it is called **here** is this repository's own business: `build.sbt` points
`SYSL_LIB` at `lib/`, and that variable names a root outright.

Or, if you have a sysl checkout already, point `SYSL_LIB` at its `library/` — just be aware that a
checkout on a branch is whatever that branch says now, not what `syslVersion` said.

Then:

```sh
sbt test
```

## Building the site

The site is built by [juicer](https://github.com/edadma/juicer), which has no standalone launcher
yet, so it runs from its own checkout pointed at this directory:

```sh
cd ~/dev/juicer
sbt 'juicerJVM/run serve -s ~/dev/sysl-lang/sysl.sh/docs -L'      # live preview
sbt 'juicerJVM/run build -s ~/dev/sysl-lang/sysl.sh/docs -d ~/dev/sysl-lang/sysl.sh/_site'
```

`sass` and `esbuild` must be on PATH or the asset pipeline degrades to the committed `static/`
copies and skips fingerprinting.

Production builds and deploys from `dev` — see `.github/workflows/docs.yml`.

## What is not here

The **specification** — the numbered design chapters — stays with the compiler, at
[`design/`](https://github.com/sysl-lang/sysl/tree/dev/design). It is not the website: nothing renders
it, and a chapter belongs in the same commit as the code that implements it.

The **guide programs** stay there too, in `guide/` and `examples/`, where they are gated against the
compiler's dev branch rather than against a release.
