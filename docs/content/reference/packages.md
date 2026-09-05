---
title: Packages
summary: package.hocon, dependencies on other people's code, version selection, and what a fetched package's modules are called here.
weight: 105
---

A project's configuration and its list of dependencies are **one file, `package.hocon`, at the
project root**. It says who this package is, what machines it is built for, what those machines
provide, and what it depends on.

**The file is optional.** A single-file program has none, and gets the defaults: the project root is
the directory the compiler was given, the target is the machine it is running on, and that target
provides everything. `sysl run hello.sysl` needs no ceremony.

```hocon
package {
  name    = "geom"
  version = "1.4.2"
}

targets {
  default = "aarch64-macos"
}

capabilities { heap = false }

requires { os = true }

dependencies {
  json  { git = "github.com/edadma/sysl-json", version = "1.4.0" }
  regex { git = "github.com/edadma/sysl-regex", version = "0.4.0", mount = "re" }
  local { path = "../experiment" }
}
```

## What a project is called

**`package.name` is what a directory project's executable is called.** A directory is a project
because it holds `.sysl` files, not because anybody said so, so a project has no identity of its own
unless this block gives it one. Without a name the output takes the directory's:

```
myproj/main.sysl        ->  myproj/myproj
myproj/package.hocon    ->  myproj/tool
  package { name = "tool" }
```

Requiring the file was the other way to answer this, and it is deliberately not what happens: it
would give every project an identity and charge every project the ceremony, when a scratch directory
holding one `.sysl` file is the cheapest thing in the toolchain and worth keeping cheap.

A **file** project is outside this. `sysl build foo.sysl` writes `foo` beside the caller, whatever a
`package.hocon` sitting in the same directory says — the name came from the path you typed, and a
config quietly moving the executable would be a worse surprise than anything it fixed.

The name reaches the filesystem, so it has to be a single path segment. `.`, `..`, anything holding a
separator, and the empty string are refused when the file is read, rather than being sanitized into
something that would build a differently-named executable without saying so.

**And it may not be the name of a module directory beside it**, since the binary would be written
over one:

```
error: this project builds an executable named 'slate', and 'slate' is a directory — the two
       cannot both have that name. Rename the package or the directory, or say where the binary
       goes with '-o <path>'
```

Naming the package for its own module is the obvious layout and reaches this without trying. `sysl
run` never does, writing to a temporary file of its own, so a project can carry the collision until
the first time somebody asks for a binary.

## A package may carry examples

**`examples/` at a project root is not part of the tree.** Everything else under a root compiles
*into* it, which is what makes the exclusion necessary rather than convenient: a `.sysl` file with no
`module` header is the anonymous root module wherever it sits, and a library may not have one — so
without this a package could not carry a runnable demo at all, and every one of them would be a
fenced block in a README that nothing compiles.

```
mypkg/package.hocon
mypkg/sh/sysl/mypkg/mypkg.sysl     the library
mypkg/examples/demo.sysl           a program, and not part of it
mypkg/examples/bigger/main.sysl    a program of several files
```

The directory is left out of the library build, out of the C the package carries, out of the modules
the package offers, and out of the walk `sysl test .` makes — that walk takes every `.sysl` under the
root and refuses one that declares no module, so the exclusion has to reach it too or the feature
does not work.

**A program in it compiles against the package with nothing on the command line.** `sysl run
mypkg/examples/demo.sysl` and `sysl run mypkg/examples/bigger` both find the package they sit under
and use it as a library, exactly as a `--lib` naming it would — so the manifest's own dependencies,
its C and its search paths all reach the demo.

That is a rule about the shape of a path rather than a search: exactly two levels up, only where the
intervening directory is literally `examples`, and only where a `package.hocon` is there. A manifest
is looked for **beside** the sources everywhere else in this system, deliberately, because a search
that walked upward would make a build depend on directories above the one it was given.

**The exclusion is at the root and nowhere else.** `sh/sysl/mypkg/examples/` is an ordinary module
named `sh.sysl.mypkg.examples`, so a package that wants the word still has it.

## A key this compiler does not know is reported and ignored

A manifest is read by whatever compiler is in hand, which may be older than the manifest. So an
unrecognized key is **warned about and skipped**, and the rest of the file still takes effect:

```
package.hocon: 'profiles' is not something this sysl understands, and is ignored — either it is a
               misspelling, or it was added by a newer compiler
```

That is the middle of three, and the other two are worth naming because the choice is permanent —
whatever the first stable release does with an unknown key is the compatibility floor every later
manifest has to clear. **Refusing** would mean no key could ever be added without breaking every
released compiler, which for a format whose consumers pin coordinates is a promise not to grow.
**Ignoring silently** is the failure that reads as success: a mistyped `dependencis` block does
nothing and says nothing.

A **dependency's** unknown keys are reported with the package root in front of them, because that is
the case a manifest cannot report for itself — a package written against a newer sysl, being built by
an older one:

```
/…/cache/github.com/sysl-lang/thing@0.2.0: package.hocon: 'profiles' is not something this sysl
understands, and is ignored — …
```

**Inside `dependencies` and `allocator` an unknown key is still refused**, and the asymmetry is the
point: those are a closed vocabulary rather than a growing one. Somebody who wrote
`versoin = "1.0.0"` believes they have pinned a version, and resolving whatever the default branch
happens to be while they believe it is worse than stopping. The same judgement applies to a
misspelled capability.

## The oldest compiler a package builds with

**`package.sysl` states a floor**, and a build stops there rather than somewhere inside the package:

```
package {
  name    = "sdl3"
  version = "0.2.6"
  sysl    = "0.0.62"
}
```

```
package sdl3 v0.2.6 cannot be built because it requires sysl 0.0.62 or newer,
while the compiler in hand is 0.0.61
```

**The whole of what it buys is that sentence.** A package using something the language grew builds
or does not depending on what the consumer happens to have installed, and when it does not, the
diagnostic points at a line **inside somebody else's package** with nothing to say the compiler is
what is wrong. `sdl3` is the live example: v0.2.6 writes a bare `None` as a method default, which
needs 0.0.62, and a consumer on 0.0.61 gets a type-inference error inside `video.sysl`.

It is a **version** like every other version here — three numbers, no range and no pre-release, which
is [the version rule](#the-major-version-rides-in-the-coordinate) and not a second one. A range would
be a claim about compilers that do not exist yet, and the field is a floor precisely because nobody
can make that claim.

**Both kinds of manifest are held to it**: the project being built, and every package it depends on.
Saying nothing is the ordinary case and is never an error.

**An interim compiler satisfies the floor its numbers reach.** One is stamped `0.0.66-fcf4e33a` — the
next patch, plus the commit it was built from — and it has everything the release before it shipped,
so it is read as `0.0.66`. Cargo makes the same ruling for a nightly toolchain against
`rust-version`.

**An older compiler cannot report this**, and nothing can change that: it does not know the key, so
it reads the manifest, ignores the field, and fails wherever it was going to fail. The field starts
paying from the release that understands it — which is also why adding one to a published package is
safe, since every compiler that came before simply passes over it.

**What it deliberately does not do is feed the resolver.** A dependency whose newest version is too
new is refused rather than resolved to an older one. Cargo added `rust-version` in 1.56 and only
taught the resolver about it thirteen years later, behind an opt-in; and here there is no registry to
ask — a dependency is a git coordinate plus a tag, and "the newest tag whose floor I satisfy" means
fetching and reading several tags' manifests, which is a different algorithm and a different fetch.

## Capabilities

**Whether the machine has a heap, an operating system or POSIX is a project engineering
decision, and this is where it is stated.** The compiler's registry of targets deliberately carries no
capabilities: a target's ABI is measured and its capabilities are policy, so the ABI is the registry's
and the policy is yours.

```hocon
capabilities { heap = false }
```

That is the project's own statement and it applies to **every** target the project builds for. A
capability the file does not mention is provided — the prior is that a machine can do everything,
which is what every build had before there was a file to say otherwise, so what a config records is
what a machine *cannot* do.

**A target block layers over it, per capability, for the one machine that differs:**

```hocon
capabilities { heap = false }

targets {
  default = "thumbv7em-freestanding"
  aarch64-macos { capabilities { heap = true } }
}
```

Only the capabilities a target block names are overridden; everything else still comes from the
project's own block. Writing the statement only inside target blocks — which was once the only place
it could go — keys it to a machine's name, so a project building for three targets says it three
times and cannot say it at all for a target the registry already has without a block that reads as
redefining the machine.

### `capabilities` against `requires`

The two blocks point in opposite directions and neither substitutes for the other:

| block | says | about |
|---|---|---|
| `capabilities` | this machine **has** these | the target being built for |
| `requires` | this package **cannot be built without** these | the host it needs |

`requires { heap = true }` buys one clean error when the package is built for a machine without one,
instead of an error at every `&T`. A `false` there says nothing — a package does not need a facility
*not* to exist — and is refused rather than quietly dropped, naming the two places that do mean it.

### `heap`, and the module's own `@no_alloc`

The capability is `heap`, a **facility** the machine either has or has not. A module's promise that
its own code does not use one is spelled [`@no_alloc`](/reference/modules/#capabilities-are-a-module-property),
a **conduct**. They are two words because they are two statements, and each is refused where the other
belongs.

For compatibility with packages already published, `alloc` is still accepted in this file and read as
`heap`. Write `heap`.

## One heap, and the package that names it

Having a heap is one question; **which** heap is another. A program allocates through one pair of C
functions, and a package that brings its own says which:

```hocon
allocator {
  alloc = "pvPortMalloc"
  free  = "vPortFree"
}
```

Saying it settles the pair for the whole program, not for the package that said it. Every allocation
the compilation emits calls that pair — a string concatenation, a `Buf` growing, a box the reference
counter builds — and every release gives the storage back to it. Declare nothing anywhere and the pair
is libc's `malloc` and `free`, which is what a program depending on nothing that says otherwise gets.

**It is the program's pair rather than the package's, because there is one heap.** Ownership is
settled by reference count, so which code frees a thing is not knowable when a package is written: a
`Buf` filled inside an RTOS package and handed back is freed by the application, and one built by the
application and passed in is freed by the package. Two allocators would make every one of those
crossings a heap boundary that no signature marks.

A package declares it rather than a target, and that is deliberate. The obvious alternative is a
target fact — the machine knows it is a Cortex-M — and it does not survive contact: `thumbv7em` does
not imply FreeRTOS, two RTOSes on one chip want different pairs, and a bare-metal program on that chip
wants libc's. What knows the answer is the package carrying the heap.

### Two that disagree, and two that agree

Two packages naming different pairs is refused when the dependency graph is resolved:

```
two packages name different allocators, and a program has one heap — 'freertos' names
pvPortMalloc / vPortFree; 'arena' names arena_alloc / arena_free. Drop one of the
declarations, or depend on only one of them
```

Refused there rather than at the link, because the link will not refuse it: both symbols resolve, the
program builds, and it hands one allocator's storage to the other's `free` at run time.

Two packages naming the **same** pair unify to it. That is the ordinary case rather than a coincidence
— a kernel package and a driver built on it both name the kernel's allocator, and neither has to know
whether the other did.

Both halves are said or neither is; half a pair is refused, since storage taken from one heap and
given back to another is the one outcome worse than not building. The project's own manifest may
declare a pair too, which covers an application with its own arena and no dependency that has one.

**Whichever road the package arrived by.** A package named in `dependencies` and the same package
handed over as a `--lib` source root declare the same thing and settle the same question — this is a
property of the package, not of the flag that reached it.

### A library artifact is built for one allocator

A `.syslib`'s object half is compiled code, and it calls the pair by name. So an artifact is built for
one allocator exactly as it is built for one machine, and a program that allocates another way refuses
it:

```
geom.syslib allocates through malloc / free and this program allocates through
pvPortMalloc / vPortFree — a program has one heap, so a library compiled against
another cannot be linked into it. Rebuild the library against this one
```

That refusal is sharper than the one for a mismatched target, and deliberately so: an artifact for the
wrong machine is eventually refused by a linker that cannot read the object at all, while an artifact
for the wrong allocator is refused by nothing. Recording the name is the only place it can be caught.

The standard module is under the same rule and needs nothing done about it — its cache is keyed by the
pair among the other things it is keyed by, so a program that names an allocator gets a standard
module built for that allocator, built on demand and announced on stderr.

### A freestanding target has nowhere to take the default from

Declaring nothing gets libc's `malloc` and `free`, which is the right default everywhere there is a
libc. A freestanding target has none — so a package that `requires { heap = true }`, built for one by
a command that links, is asking for two symbols nothing will define:

```
this package requires a heap and 'wasm32-freestanding' is freestanding, so nothing supplies
'malloc' and 'free' — the link would answer with an undefined symbol for each. Name a heap of
your own in package.hocon's 'allocator' block, or build for a target that has a libc
```

The `allocator` block above is the way out, and it is the whole of the way out: naming libc's own pair
explicitly is refused by the same rule, because writing `malloc` down does not make a machine that has
one.

**This is not the capability check, and cannot be.** `requires` is answered against what the *project*
says a target provides, and a capability the manifest does not mention is provided — the prior is that
a machine can do everything, and what a manifest records is what a machine cannot do. Whether an
allocator exists is exactly the kind of engineering decision that prior is right about: a Cortex-M
program linked against newlib has `malloc`, one linked against nothing has not, and the target cannot
tell you which. So what is asked here is not what the machine has. It is **who is doing the link**.

That is why `build-c` and `build-lib` are untouched. They write an archive for somebody else's build
system to link, and the allocator arrives from a `CMakeLists.txt` the compiler never sees — which is
how every board project works. Only where sysl invokes the linker itself is there nothing left to
supply the pair.

### What it does not do

Naming the pair says which functions the program uses. It says nothing about **where** the program may
use them, and that distinction matters on a real RTOS: `pvPortMalloc` suspends the scheduler and is
not usable from an interrupt handler, while sysl allocates implicitly — a string operation, a growing
buffer, a box. Code reachable from a handler is the caller's to bound, and `@no_alloc` is how it is
bounded.

## Headers a package needs and does not carry

A capability is answered by the target, and there is nothing for anybody to go and do. **A header is
answered by a path on a machine the package has never seen**, and that is the other thing a package
may need of its environment.

Most bindings carry the C they include — sqlite3, qcbor, monocypher and termbox2 all vendor their
library's source, so a relative include resolves and no flag is involved. A package binding something
the *consumer's* build system owns cannot do that. `pico2` is the case: lwIP's headers live in an
81 MB pico-sdk clone that only the consuming project has, and vendoring a copy would be vendoring the
thing the package exists not to reimplement.

```hocon
requires {
  headers { lwip = "lwIP's headers — the pico-sdk carries them at lib/lwip/src/include" }
}
```

The consumer says where they are:

```
sysl build . --include-path lwip=$PICO_SDK_PATH/lib/lwip/src/include
```

**The package names the requirement and the driver supplies the path.** That is the same split as
[`@link("png")` and `--link-path`](/reference/ffi/#where-the-library-is-where-its-headers-are-and-what-they-are-configured-with):
a path in a committed file would be one machine's directory layout published as though it were a
property of the package.

### A requirement may name the variable its path conventionally lives in

Some libraries have an agreed name for where they are installed. The pico-sdk's is `PICO_SDK_PATH` —
its own CMake reads it, and every project that uses it sets it. Where such a name exists the package
may say so, and then a consumer who has it set needs no flag at all:

```hocon
requires {
  headers {
    pico_sdk = { note = "the pico-sdk's headers", env = "PICO_SDK_PATH" }
  }
}
```

**A variable's *name* is not a path.** `PICO_SDK_PATH` is the same string on every machine in the
world, so it is a property of the library's ecosystem rather than of whoever is building — which is
exactly the test the paragraph above applies, and it passes it where a directory does not.

**A flag still wins, and an unset variable still refuses.** The variable is consulted only where
nothing on the command line named that requirement, so it can turn a failure into a success and can
never turn one success into a different one — and `--include-path` remains the way to build against
something other than what the variable points at. An empty variable counts as unset.

The `note` stays required, because it is what a consumer *without* the variable is shown, and the
refusal names both:

```
this project needs the 'pico_sdk' headers and nothing supplied them — the pico-sdk's headers.
Set PICO_SDK_PATH, which is where this package's own ecosystem looks for it, or say where they
are with '--include-path pico_sdk=<dir>'
```

**Most requirements have no such variable and take the bare string**, which means exactly what it
always meant. `FreeRTOSConfig.h` is the application's own file and no convention names it; there is
no agreed variable for lwIP either.

**The value is the reason, not a path.** It is prose for a person — what the headers are and where
they come from — quoted back at whoever has to find them. A name on its own would report that
something called `lwip` is missing and leave the reader to work out what that is.

### What it buys is the refusal

`--include-path` always worked, and a consumer who passed it always built. What did not exist was any
way for the *package* to say it needed one, so a build without the flag failed inside a C compiler
that names the header and knows nothing about sysl, the package, or the flag:

```
fatal error: 'lwip/tcp.h' file not found
```

Now the build stops before clang runs, naming all three:

```
github.com/sysl-lang/pico2 needs the 'lwip' headers and nothing supplied them — lwIP's headers,
the pico-sdk carries them at lib/lwip/src/include. Say where they are with
'--include-path lwip=<dir>'
```

A **bare** `--include-path` is not an answer, deliberately. The check is about what a build says it
has rather than what it might happen to find; reading a bare path as an answer would let a consumer
satisfy the requirement by accident and never learn they had.

It is asked only where C is actually compiled, so `emit-llvm` and `prove` are not held up by a path
they would never open.

**`build-lib` compiles C, so it is asked too — and it is asked for its own manifest and nothing
else.** That is the narrowest scope of any command here, and it follows from the same rule rather
than being an exception to it: `build-lib` compiles the C of the tree it was handed and no other, so
a `--lib` source root's declaration is not charged to a library built against it. That build never
opens the root's header, and the root is asked for it when the root is built itself.

This is the road a package is *packaged* by, so it is the one that matters most to whoever is
publishing one — and it was the last to be asked. Until it was, building a declaring package into an
artifact answered with `fatal error: 'lwip/tcp.h' file not found` out of the package's own shim, and,
worse, a **bare** `--include-path` satisfied the requirement in effect, because nothing was asking.
A requirement that can be met by accident on the machine that built the artifact and nowhere else is
exactly what the paragraph above exists to prevent.

### It is asked whichever way the package arrived

A package reaches a build by three roads, and the declaration is worth the same on each — though they
do not all need the same thing from it.

| how the package arrived | what happens |
|---|---|
| named in `dependencies` | its manifest comes with the graph, and the requirement is asked |
| handed over as a `.syslib` | nothing is asked, because nothing is needed |
| given as a `--lib` source root | its manifest is read for this, and the requirement is asked |

**The artifact needs no header at all**, which is worth knowing before going to look for a flag to
pass. `build-lib` measures a `c const` and a `c type` while it builds and stores the **answer** —
the value, and the integer a typedef turned out to be — rather than the C that produced it, so a
consumer of a `.syslib` needs neither a clang nor the library's headers. There is nothing left to
require.

**The header requirements, the allocator and the `dependencies` are read from a `--lib` root.** That
flag names a *source root*, which need not be a package at all, and one that is not has nothing to
declare — so a root with no `package.hocon` goes on building exactly as it always did.

All three are read for one reason: each is a property of the *package* rather than of the road the
package arrived by. A directory handed over with `--lib` is the same package as one named by a
coordinate, so it brings its heap, its header requirements and what it is written against either way.

The allocator used to be read only from a coordinate, and the two roads then disagreed **in silence**:
the package's own objects came out of its heap and every string, `Buf` and box in the same program out
of libc's, with nothing said at any point. A silent mixed heap is worse than a rule somebody has to
know, which is what decided it.

The `dependencies` were the last of the three. Read only by coordinate, the same directory gave a
build the package's sysl and nothing it was written against, and what came back was a page of
unresolved names pointing into a package you did not write — naming neither the missing dependency nor
a flag. They are fetched now, into the **same** graph the project's own go through, so version
selection sees every claim at once and a package two roots share is one copy at one version. What a
root's manifest binds is reachable from your own files as well, which is no more true of a
dependency's name than it always was of the root's own modules.

`build-lib` is the one command that refuses them instead, and that is not an inconsistency: it
compiles one tree into an artifact for one machine and does not reach the network, so it has nothing
to fetch **with**. It says so, and names `--lib` as what to write instead.

What is still not read is the rest of the manifest. A root's capabilities are the program's to state,
and they have none of the one-answer-per-program character that makes the allocator settle for
everybody.

## A library the machine already has, found by asking it

A `headers` requirement puts the path in your hands, which is right when the headers belong to your
build — lwIP's live in your pico-sdk clone and nothing else could know where that is. **It is more
than is needed for an ordinary installed library**, because most of them answer the question
themselves. `pkg-config` is how: a `.pc` file installed beside the library says where its headers are
and what its link line is, on this machine.

```hocon
requires {
  pkg_config { sdl3 = "SDL3 — brew install sdl3, or Debian's libsdl3-dev" }
}
```

```
sysl run .
```

That is the whole command. Before this, the same program wanted the layout of your machine typed out —
and typed out correctly, which for the box2d demo meant knowing that cairo's headers are in
`include/cairo` while SDL3's want the directory *above* `SDL3`:

```
sysl run . --link-path /opt/homebrew/lib \
           --include-path cairo=/opt/homebrew/include/cairo \
           --include-path sdl3=/opt/homebrew/include
```

**The split is the same one as before: the package names the requirement and something else supplies
the path.** What changed is who that something else is — the machine, rather than a person copying its
layout onto a command line. Nothing the package wrote is a path, and no code the package supplied is
run; the compiler asks a well-known tool a question, exactly as it already asks clang what a
[`c const`](/reference/ffi/) measures to.

**One declaration answers both halves.** A package binding an installed library needs its headers to
compile *and* its library to link, and having one of those is not a build. `--cflags` feeds every C
compilation in the tree and `--libs` feeds the link line — including the `-Wl,-rpath` that decides
whether a dynamically-linked program finds its library at **run** time.

**A hand-written `--link-path` used to leave that last part out, and no longer does.** Until 0.0.104
it emitted `-L` and nothing else, so a library whose install name is `@rpath/…` — what CMake writes
for any library given a `SOVERSION` — linked cleanly and then failed to start, naming a library
sitting exactly where the flag had said it was. It now emits the run-time search path as well. What
`pkg-config` still supplies that a typed path cannot is the rest of the answer: which library, what
its headers need, and the exact rpath *that library* asked for rather than a guess made on its
behalf.

### The name is the one pkg-config files it under

It cannot be derived, and the two things it might have been derived from are both wrong. The `@link`
directive is one: the sdl3 package writes `@link("SDL3")` and the module is `sdl3`, because `-lSDL3`
and `sdl3.pc` are two naming conventions that happen to share a word. A `headers` requirement's name is
the other, and worse: a name that happened to match some `.pc` file on your machine would satisfy a
requirement nobody answered — met by accident on the machine that built it and nowhere else.

**So it is not yours to choose, and what it may hold says so.** A `headers` name is one you invent, and
it is held to a plain word: letters, digits, `_` and `-`, starting with a letter. A `.pc` name may also
hold a `.` and a `+`, because a great many libraries file under one with a version in it — libyaml is
`yaml-0.1` on macOS, Debian and Arch alike, GLib is `glib-2.0`, GTK 3 is `gtk+-3.0`. A rule that
refused those would not be asking for a better name; it would be refusing to bind the library.

**A name with a `.` in it has to be quoted**, because an unquoted dot is HOCON's path separator and
`yaml-0.1 = "…"` would declare a `1` inside a `yaml-0`:

```hocon
requires {
  pkg_config { "yaml-0.1" = "libyaml — brew install libyaml, or Debian's libyaml-dev" }
}
```

What the name may **not** hold is a separator or an `=`, and that bound is the flag rather than
taste: the name is written back to you as `--include-path <name>=<dir>`, and one holding either
could not be told from the directory beside it.

### What happens when it cannot be answered

**Your own flags win and stop the probe.** `--include-path <name>=<dir>` answers this exactly as it
answers a header requirement, so a hermetic build, a hand-built prefix or a machine with a broken `.pc`
is never at the mercy of what happens to be installed.

**A build for another machine is not asked at all.** `pkg-config` answers for the machine it runs on,
and a cross build's headers and library are the target's. A freestanding program compiled against your
laptop's `/opt/homebrew` would link and be wrong somewhere you cannot see it, so a target that is not
this machine is refused rather than answered:

```
this project needs the 'sdl3' library and this is a build for 'thumbv7m-freestanding' rather than
for this machine, so there is nothing to ask where it is — SDL3 — brew install sdl3. Say where it
is with '--include-path sdl3=<dir>' and '--link-path <dir>'
```

**A machine without `pkg-config` is exactly where it was before**, with the refusal the previous
section describes plus a sentence naming what was looked for. The two failures are told apart, because
they send you to different places: `pkg-config` missing is one install away and says nothing about the
library, where a `pkg-config` that does not know the module means the library itself is not there.

macOS ships no `pkg-config` and the libraries do not bring one — `brew deps cairo` lists fifteen
packages and it is not among them — so `brew install sysl` installs it as a dependency of the compiler.

## Dependencies

A dependency is **a git repository and a version**. There is no registry, no account to create, and
no name to reserve.

```hocon
dependencies {
  json { git = "github.com/edadma/sysl-json", version = "1.4.0" }
}
```

The coordinate is cloned over HTTPS and the tag `v1.4.0` is what gets read. A `path` dependency names
a directory instead, for a package being developed alongside its consumer:

```hocon
dependencies {
  helper { path = "../helper" }
}
```

`sysl build` fetches whatever the machine has not got, so adding a dependency is an edit to this file
and nothing else. Fetched packages are cached under the machine's cache directory and shared by every
project on it.

**A coordinate is identity, not a URL.** `https://` on the front is refused rather than stripped:
the coordinate is what a package's module names are derived from, so two spellings of one package
would link as two incompatible copies of it.

### The major version rides in the coordinate

From the second major version on, a breaking change makes a **new coordinate**:

```hocon
dependencies {
  json { git = "github.com/edadma/sysl-json/v2", version = "2.1.0" }
}
```

A module's name is part of every symbol it emits, so two versions of a module named `json` would emit
the same symbol names for different code. One version per module is where the linker puts things
whether or not anyone plans for it, and `/v2` is what planning for it looks like. `0.x` and `1.x`
ride in the bare path.

### Which version you get

The version chosen for a package is **the highest minimum anybody asked for** — not the newest that
exists:

```text
your project     depends on json 2.1.0
       json 2.1.0 depends on buf  1.2.0
       text 3.0.0 depends on buf  1.4.0
                                  ------
                       buf resolves to 1.4.0
```

Three things follow from that, and they are the reason for it:

- **Adding a dependency cannot silently upgrade an unrelated one.** The only versions in play are
  ones some manifest names.
- **Builds are reproducible without a lockfile**, because the selection is a pure function of the
  manifests.
- **Upgrading is an edit.** Nothing quietly walks everything forward; you raise a minimum here and
  the graph is recomputed.

The cost is the honest one: you do not automatically get the newest patch release.

**When your own version is the one that got raised, the build says so:**

```text
sysl: note: 'plutovg' is named at 0.2.0 and the build selected 0.2.1, which syslui asks for
```

A note and not a refusal, because the higher version is the right answer and the build is correct.
Selection is otherwise silent by design — it raises floors constantly, and a line for each would be a
wall of them about packages nobody typed. What is different here is that the version came from *your*
manifest: you wrote one number, the build used another, and nothing in the file you are reading says
so.

**Where the note does not fire and you want to know anyway, ask**:
[`sysl deps`](/getting-started/cli/#deps) prints the whole graph — every package, the version settled
on, and every claim that was made on it, including the ones that lost. That is the only place a
losing claim survives: selection keeps the maximum and forgets the rest, so once a build is over
there is nothing left to reconstruct it from.

### Dependencies a test alone needs

A package's own suite often reaches for something no consumer of it should have to install — a second
implementation to check answers against, a driver for a database, a fixture generator.
`dev_dependencies` is where those go, and it reads exactly as `dependencies` does:

```hocon
dependencies {
  parsing { git = "github.com/sysl-lang/parsing", version = "0.5.0" }
}

dev_dependencies {
  quickjs { git = "github.com/sysl-lang/quickjs", version = "0.1.0" }
}
```

The difference is entirely in **who resolves them**:

- `sysl test` on this project resolves them, exactly like an ordinary dependency;
- `sysl build`, `sysl build-lib` and `sysl build-c` do not — every other build drops the source that
  could name one before it analyzes anything, so fetching the package would compile something the
  compilation cannot refer to;
- **anything that depends on this package does not resolve them at all**, at any command. They are
  not fetched, not built, and not on the link line.

That last one is the point of the block. A test-only dependency declared in `dependencies` is a
package every consumer clones, builds, and — for a binding to a system library — has to have
installed before your package will build for them at all.

**A dev dependency may only be imported from a `@tests` file or a `@test` function**, which is the
same line the resolution is drawn along: those are what an ordinary build strips, so they are exactly
the source a consumer never compiles. An ordinary module importing one is refused:

```text
error: 'sh.sysl.quickjs' comes from 'sh.sysl.quickjs', which this project declares in
'dev_dependencies' — that is not fetched for anything depending on this project, so only a
'@tests' file or a '@test' function may import it. Move the import into the tests, or declare
the package in 'dependencies' if the library itself needs it
```

Without that check the mistake is invisible to the person making it: `sysl test` resolves the package,
the ordinary module importing it compiles, the suite passes, and every consumer is refused at a module
that was never fetched for them.

**A package may not be named by both blocks.** They say opposite things about one package — that a
consumer gets it, and that a consumer does not — and there is no reading under which both are meant,
so it is refused the way a `git` beside a `path` is.

[`sysl deps`](/getting-started/cli/#deps) lists them with the rest, marked `(dev)`, since what a
project takes is a property of its manifests rather than of any one build.

## What a dependency's modules are called

A package is a tree of modules, and **its modules come in under their own names**. A module is a
directory of source files, so a package holding `sqlite/` is reached exactly as its own documentation
shows it:

```text
sqlite.open("db.sqlite")
```

The name is the *module's*, not the package's — sqlite3's package is called `sqlite3` and its module
is `sqlite`, and reaching the second does not mean saying the first.

**A name is a module path, not a first segment.** Every package published under `sysl-lang` puts its
source under a reverse-DNS prefix, so what it offers is a dotted path:

```text
sh/sysl/table/table.sysl       →  sh.sysl.table
```

`sh/` and `sh/sysl/` hold no source, so neither is a module and neither is a name that package
offers. Two packages laid out this way therefore do not collide, which is the point of the
convention: a project may depend on `sqlite3`, `linenoise` and `table` at once and import all three
under the names their own documentation shows.

A binding covers the module it names and everything below it, so `sh.sysl.table.Style` reaches the
same package and keeps its tail.

### Local names are a decision, and the alternative is a real one

**Nothing in an import line names a coordinate**, and that is chosen rather than incidental. The
alternative — modules qualified by where the package came from, as Go's import paths and Java's
reverse-DNS packages both do — is a serious design and not a strawman: it makes a collision
impossible by construction rather than by refusal.

What it costs is that the coordinate then appears in **every import line in every file**, so
moving a package, forking it, or vendoring it edits every consumer's source instead of one line of
its manifest. sysl takes the half of that design which pays and leaves the half that does not: the
reverse-DNS *layout* convention above is what keeps `sh.sysl.table` and `sh.sysl.sdl3` apart, and it
costs nothing at the import, because the prefix is the module's own name rather than an address.

**What makes a local name safe is the rest of this section.** A collision is refused rather than
resolved silently; a nearer name wins over a name nobody asked for; and `mount` renames a package
for the one consumer that needs it. Those are what a global scheme would have bought, obtained a
different way.

**The cost of the choice is the transitive import**, stated where it arises above: a program may
import through a package that never promised to keep depending on what it depends on. That is the
price of not writing addresses down, and it is the one being paid deliberately.

### Imports are transitive

**A package reached through another is importable too.** Naming one dependency brings its own
dependencies with it, and theirs, however far down the graph they are — so a manifest names what a
project *takes* rather than everything it can see:

```hocon
dependencies {
  syslui-sdl { git = "github.com/sysl-lang/syslui-sdl", version = "0.1.0" }
}
```

is enough to `import sh.sysl.ui`, `import sh.sysl.plutovg` and `import sh.sysl.sdl3`, because the
driver depends on all three.

The reason is that **a package's public surface is made of its dependencies' types**. `syslui-sdl`
hands out a `&Fn() -> &View` and `View` belongs to the toolkit it is built on, so a program that
could not name the toolkit could not call the one function that package exists for. Declaring it
anyway is a line that says nothing the build could not work out.

**Three levels of precedence, and only a tie inside one of them is an error:**

1. your own modules, and every `--lib` source root's;
2. what your manifest declared;
3. what arrived through something else.

A nearer name wins, quietly — a project with its own `json/`, or a dependency it mounted as `json`,
keeps that name however many packages three levels down offer one. A name nobody asked for never
takes one somebody wrote, and refusing there would mean a package you have never heard of could break
your own module names.

**Two packages at the *same* level wanting one name is the collision below**, and it is refused
whether they were declared or inherited. Naming one of them yourself is what settles an inherited
pair, since a declared name beats an inherited one.

**A `mount` does not travel.** It is a name its writer chose for their own import lines, so what an
inherited package offers is what its own documentation shows.

The cost is stated rather than hidden: a program may import through a package that never promised to
keep depending on what it depends on, so a library dropping one of its own dependencies can break a
consumer that never named it. Every language with a class path has this, and the ceremony of the
alternative is what people actually complain about.

**Two packages cannot quietly share a name.** If two dependencies both offer a `json`, or one offers
a `json` and your own project has a `json/` directory of source, the build stops and says so rather
than picking one. So does one offering a path *inside* another's — a package offering `sh.sysl` and
one offering `sh.sysl.table` share no name, but an import of `sh.sysl.table` could be read as either,
and resolving it to the longer would be a rule nobody wrote down.

**"Your own modules" includes every `--lib` source root**, since a root's modules are filed under your
project's names rather than under a prefix of their own. So a root that declares a dependency offering
a name the root itself declares is refused in the same words, and the message names the root as you
gave it. Without that, the root's own module answered, its dependency's was unreachable, and the build
was green — the silent winner the whole rule exists to refuse.

Write a `mount` to say what one of them is called here:

```hocon
dependencies {
  theirs { git = "github.com/edadma/sysl-json", version = "1.4.0", mount = "ejson" }
}
```

which hangs that whole package under one segment, so its `json` is `ejson.json` and your own `json`
is untouched. A mount is yours alone: another project may mount the same package differently, and
both still link one copy of it.

**Two major versions of one library are named as such**, because that collision reads very
differently from an ordinary one:

```text
'json' and 'json' cannot both be imported — github.com.e.json and github.com.e.json.v2 are two major
versions of one library and both are in this graph, and their modules have the same names
```

Selection cannot fold those together — a major above the first is a different coordinate, which is
the whole point of the suffix — while their module names are identical, because a module's name is
its directory. A `mount` is still the answer where you genuinely want both.

## `sysl.sum`

`sysl.sum` sits beside `package.hocon` and **should be committed**. It records a content hash for
each package and version the project resolved, and a fetch whose content does not match is refused:

```text
github.com/edadma/sysl-json v1.4.0 sha256:6f1b…
```

What it protects against is the class of change a version number cannot describe — a tag moved to
point at different commits, a repository rewritten, a mirror serving something other than what the
author published. In all three the version number is exactly what it was.

It is **not a lockfile**: version selection is already a function of the manifests, so there is
nothing to record about which versions were chosen. The first time a package is seen it is trusted
and recorded; reviewing the line that appears is the part a person does. A `path` dependency gets no
entry, because a directory beside you is expected to change.

## Adding one, and vendoring the lot

A dependency is a line in `package.hocon` and you may write it yourself, but `sysl add` is what
settles the two things you would otherwise go and look up — how the coordinate is spelled and what
the newest tag is:

```text
sysl add github.com/sysl-lang/sdl3
sysl add github.com/sysl-lang/sdl3@0.3.1
```

The version comes from `git ls-remote --tags`, so it works for any host a build can clone from. The
manifest is rewritten one run of bytes at a time, which is why your comments and layout survive it,
and the result is read back before it is written — a rewrite that produced something unreadable
leaves the file exactly as it was. Nothing is fetched; the next build does that.

`sysl vendor` puts everything the project resolves to into a `vendor/` directory beside the manifest:

```text
sysl vendor .
```

**It is the machine's package cache moved into the project** — the same layout, the same resolution,
the same `sysl.sum`. A project that has a `vendor/` builds with the network off, and the directory
being there is the whole of what turns that on: nobody has to be told, and no flag has to be passed.
It is not part of the project's own source, exactly as [`examples/`](#a-package-may-carry-examples)
is not, so nothing in it is compiled as one of your modules.

Commit it where you want a build that an upstream disappearing cannot break; leave it out where you
would rather fetch. A `path` dependency is not vendored and cannot be — it is a directory you are
editing beside this one, which is why nothing keeps a sum for it either.

## No build scripts, ever

**A package cannot run code at build time.** Not a hook, not a script, not a plugin. `sysl add` and
`sysl build` read and write files and run nothing.

Most of what other ecosystems need build scripts for is compiling vendored C, and sysl already
compiles a library's C declaratively — the linker inputs a package needs are `@link` attributes in
its source, not a program that computes them. What that buys is most of the supply-chain story: a
package that cannot execute during installation cannot exfiltrate anything during installation.

### What the vendored C is compiled with

A C library almost always has compile-time options, and they are the **package author's** decision
rather than the consumer's. Which of miniz's four `MINIZ_NO_*` switches are set is what makes that
package a caller-owned codec with no allocator in it; a program that depends on it has no more
business choosing them than choosing its warning flags.

A `defines` block says so. Each sub-block is a `.c` file **the package carries**, named relative to
the package root, and what follows is what that file is compiled with:

```hocon
defines {
  "sh/sysl/miniz/c/miniz.c" {
    MINIZ_NO_MALLOC       = true
    MINIZ_NO_STDIO        = true
    TDEFL_LESS_MEMORY     = 1
  }
}
```

`true` is a bare `-DNAME`, which is what an option tested with `#ifdef` wants. Any other scalar is
`-DNAME=value`, for one tested with `#if`. **`false` is refused**, because a reader would have to
guess between *do not define this* and `-DNAME=0` — and those differ under `#ifdef`. Whichever was
meant can be said exactly: leave the line out, or write `0`.

This is not a general flags channel. It reaches the C a package carries and nothing else: no include
paths, no warning flags, nothing for an installed library's headers. `--define` remains what a
*build* says and applies to every C compilation in it; a `defines` block is what a *package* says
and applies to the file it names. Where both name the same macro the package wins, since the whole
point is that its configuration is not the consumer's to change by accident.

### One key, several files

A key may name its files with braces, as a shell writes them:

```hocon
defines {
  "sh/sysl/miniz/c/{miniz,shim}.c" {
    MINIZ_NO_MALLOC   = true
    TDEFL_LESS_MEMORY = 1
  }
}
```

Several groups multiply out — `"{a,b}/{x,y}.c"` is four files — and expansion happens when the
manifest is read, so nothing further on sees anything but one path and its macros.

**The point is not brevity.** A package whose C shares a configuration would otherwise carry one copy
of the list per file, and every macro in such a list changes a struct's size or deletes a
declaration. Two copies that drift apart are precisely the silent skew this block exists to prevent,
and duplication is how drift starts.

**There is no `*`, and that is deliberate.** A wildcard picks up a `.c` added later without anybody
deciding — the same failure by another road, since the new file joins a set that changes struct
layouts. A brace still names every file it configures; it only says the shared part once.

Nesting (`{a,{b,c}}`) and empty alternatives (`{a,}`) are refused, both saying nothing a flat list
does not. So is a file configured from two blocks, which has no sensible merge — the later would
silently win.

### The path has to name C the package actually carries

A key that matches no carried file stops the build:

```
package.hocon: 'defines."sh/sysl/miniz/c/typo.c"' names a file this package does not carry —
the block configures the C the package itself holds, and there is no such C file in this tree
```

This is the one mistake a `defines` block can make that reading the manifest cannot catch. Every
other way of getting it wrong — a key that is not a `.c` file, a macro name the preprocessor would
not take, a `false` — is refused when the file is read. A path that is merely *wrong* would compile
perfectly, under the library's defaults, and only a `c const` measuring a configured struct would
ever notice.

It also catches a subtler case: a directory holding no sysl is not a module, so
[the C walk](/reference/ffi/) never collects its `.c` files and nothing compiles them. A block
configuring one is configuring nothing, and now says so.

The path is matched against the files the walk found rather than joined to the package root, which
is what makes the check possible at all — and is also what makes the block work when a package is
built from its own tree with `sysl test .`, where the root as typed and the path as walked are two
spellings of one directory.

### A `c const` block inherits from the C beside it

A `c const` block is measured by compiling a small program the compiler writes itself, so there is
no file in the package for a `defines` key to have named. It reads the headers under **the union of
what the carried C in its own directory is compiled with**.

That is not a convenience. Every option worth setting is one that changes a struct's size or deletes
a declaration, so a probe reading the defaults while the object beside it was built with the options
does not fail — it answers a *different number*:

```
sizeof(tdefl_compressor)     167800   what the object file holds
sizeof(tdefl_compressor)     319352   what a probe under the defaults measures
```

A package exporting the second while linking the first is wrong by 151,552 bytes, and nothing in the
build says a word. Inheriting from the directory is what makes the three translation units of an
ordinary binding — the implementation, the shim and the `c.sysl` — agree by construction.

Two C files in one directory compiled with different macros give their union, which is a shape to
avoid rather than to rely on: a probe cannot be measured under two disagreeing configurations at
once, and nothing can tell which of them the block meant.

### What it does not fix

An upstream header that defines its own option **unguarded** cannot be configured from outside at
all. miniz's does:

```c
#define TDEFL_LESS_MEMORY 0
```

Any definition made before including that header — from a `defines` block, from `--define`, from
anything — is overridden there, with a `macro redefined` warning and no other effect. A vendored
copy has to guard the line. That is one line of difference from upstream rather than a wrapper
header, but it is not nothing, and no mechanism here can remove it.
