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

`build.sbt` names one release of the compiler in `syslVersion`, and the pages are checked against
**that**, not against the compiler's dev branch. A website documents the released language; a page
demonstrating unreleased behaviour would be wrong for the reader.

Keeping up is therefore a step of every sysl release: cut the release, raise `syslVersion`, run the
tests, fix whatever the language moved under.

## Running the tests

They need a toolchain, because they build and run real programs:

- `clang` and `llvm-ar`
- the standard library's source in `lib/`. The published jar carries the compiler and not the
  library, which the compiler reads off disk — so take it from the compiler's repository **at the
  release tag**, which is an exact version match:

```sh
VERSION=$(sed -n 's/^val syslVersion = "\(.*\)"/\1/p' build.sbt)
git clone --depth 1 --branch "v$VERSION" --filter=blob:none --sparse \
  https://github.com/sysl-lang/sysl.git sysl-src
git -C sysl-src sparse-checkout set lib
mv sysl-src/lib lib
```

Or, if you have a sysl checkout already, point `SYSL_LIB` at its `lib/` — just be aware that a
checkout on a branch is whatever that branch says now, not what the release said.

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
