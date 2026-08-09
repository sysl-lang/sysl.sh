// The sysl documentation site: the pages, and the suites that run every program on them.
//
// **There are no main sources here, and that is the point.** The compiler is a dependency rather
// than something this repository builds, so the only Scala in it is tests. `Compile`'s source
// directories are emptied rather than left to default at `src/main/scala`, so that sbt does not
// silently start compiling something somebody drops there.
//
// **JVM only.** The compiler cross-publishes for JS and Native, but nothing here wants either: the
// suites compile sysl programs and run them through clang, which is one machine's toolchain.
//
// **The pages are tested against the compiler that is PUBLISHED, not against its dev branch**, and
// that is the semantics rather than a limitation — a website documents the released language, so a
// page demonstrating unreleased behaviour would be wrong for a reader. Keeping the two in step is a
// release step: cut the release, raise `syslVersion` below, run these tests, fix what moved.

ThisBuild / scalaVersion := "3.8.4"
ThisBuild / organization := "io.github.edadma"

// The compiler and two of its own dependencies were built against different patch releases of
// `cross_platform`, and sbt's default is to refuse that outright. It is a warning in the compiler's
// own build for the same reason it is one here: the versions are semver-compatible, the highest
// wins, and what would otherwise happen is that this site cannot be built until three unrelated
// libraries have been re-released in lockstep.
ThisBuild / evictionErrorLevel := Level.Warn

// The compiler release this site documents. Raising it is how the docs follow the language.
//
// It must be a release that is **both** on Maven Central (for the jar) and tagged in the compiler's
// repository (for the standard library's source). 0.0.5 was the first to satisfy that; 0.0.1-0.0.3
// were tagged and released without ever reaching Central, and 0.0.4 reached Central with no tag and
// no GitHub release.
//
// **0.0.6 was the first release these pages could pass on**, which is not a coincidence: the site's
// own CI is what found the bug it fixes. Every page that prints failed to link on Linux, because the
// standard-module artifact and the program each defined `sysl$stdout` and GNU `ld` refuses that
// where `ld64` quietly takes the first. Nothing about it was macOS-specific except the consequence,
// which is why a suite that had only ever run on the author's machine was green throughout.
val syslVersion = "0.0.36"

lazy val root = project
  .in(file("."))
  .settings(
    name           := "sysl.sh",
    publish / skip := true,

    // No main sources — see the header.
    Compile / unmanagedSourceDirectories   := Nil,
    Compile / unmanagedResourceDirectories := Nil,

    // `test/` at the root rather than `src/test/scala`, so that the two things this repository holds
    // — the site and what checks it — are its two top-level directories.
    Test / scalaSource                  := baseDirectory.value / "test",
    Test / unmanagedResourceDirectories := Nil,

    libraryDependencies ++= Seq(
      "sh.sysl"       %% "sysl"      % syslVersion,
      "org.scalatest" %% "scalatest" % "3.2.20" % Test,
    ),

    // A second place to find the compiler, because Central can take hours to propagate and these
    // pages cannot be written until one resolves. `~/dev/sysl-lang/sysl` publishes the same
    // artifacts to GitHub Packages as well (`SYSL_PUBLISH_GITHUB=1 sbt publish`), which answers in
    // minutes.
    //
    // **Listed after Central deliberately.** Once Central has the version it is what resolves, so
    // this is a head start and not a fork in the road — the ordinary state of this build is that
    // the resolver below is never reached. What it must never become is the reason a release looks
    // finished: a Central upload that silently failed would still build green here, so the check
    // against `maven-metadata.xml` stays a step of the release rather than something this implies.
    resolvers += "GitHub Packages" at "https://maven.pkg.github.com/sysl-lang/sysl",

    // GitHub Packages authenticates every request, a public package included, so a build here needs
    // a token with `read:packages`. Two places to find one, because the two machines that run this
    // keep it differently: a workstation has a file, and **CI is given `GITHUB_TOKEN` and cannot
    // have a file**.
    //
    // **"CI has `GITHUB_TOKEN`" is true of the workflow and false of the step**, which is the whole
    // of why this did not work for the first three releases it existed for. Actions does not put the
    // token in a step's environment the way it does `GITHUB_ACTOR`; the workflow has to pass it, and
    // `.github/workflows/test.yml` now does, along with the `packages: read` permission that lets it
    // be used. Found on 2026-08-06 releasing 0.0.9 — the first release whose push landed inside the
    // propagation window, and so the first time anything reached this resolver at all.
    //
    // CI is the case this exists for. The resolver above is only reached in the window where Central
    // has not propagated a new release yet — which is precisely when someone has bumped
    // `syslVersion` and pushed, so a CI run with no credentials would 401 on the one occasion the
    // fallback was supposed to help.
    //
    // The file wins where it exists, so a workstation set up before this behaves as it did.
    credentials ++= {
      val f = Path.userHome / ".sbt" / "github-credentials"

      if (f.exists)
        Seq(Credentials(f))
      else
        sys.env.get("GITHUB_TOKEN").toSeq.map { token =>
          Credentials(
            "GitHub Package Registry",
            "maven.pkg.github.com",
            sys.env.getOrElse("GITHUB_ACTOR", "edadma"),
            token,
          )
        }
    },

    // Forked because the suites set `SYSL_LIB`, and because a compilation shells out to clang and
    // leaves temporary files — neither belongs in sbt's own JVM.
    Test / fork := true,

    // Where the standard library's *source* is. The published jar does not carry it: since the
    // library left the binary, the compiler reads it off disk and finds it through `SYSL_LIB`, an
    // installed prefix, or a `lib/` in the tree. CI puts the compiler's own `lib/` **at the release
    // tag** here, which is an exact version match — and not the release tarball, whose assets are
    // per-platform while the library is plain source with no platform at all.
    Test / envVars += "SYSL_LIB" -> (baseDirectory.value / "lib").getAbsolutePath,
  )
