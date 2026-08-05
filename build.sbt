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
// repository (for the standard library's source). No version satisfies that yet: 0.0.1-0.0.3 are
// tagged and released but were never published to Central, and 0.0.4 is on Central with no tag and
// no GitHub release. 0.0.5 is the first that will be both, and until it is cut this build cannot
// resolve.
val syslVersion = "0.0.5"

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
      "io.github.edadma" %% "sysl"      % syslVersion,
      "org.scalatest"    %% "scalatest" % "3.2.20" % Test,
    ),

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
