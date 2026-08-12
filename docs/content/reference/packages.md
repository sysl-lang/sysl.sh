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

## Capabilities

**Whether the machine has a heap, an operating system, POSIX or threads is a project engineering
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
property of the package, and an environment variable read out of the consumer's shell would be a
build that works for whoever wrote it.

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

**Two packages cannot quietly share a name.** If two dependencies both offer a `json`, or one offers
a `json` and your own project has a `json/` directory of source, the build stops and says so rather
than picking one. So does one offering a path *inside* another's — a package offering `sh.sysl` and
one offering `sh.sysl.table` share no name, but an import of `sh.sysl.table` could be read as either,
and resolving it to the longer would be a rule nobody wrote down.

Write a `mount` to say what one of them is called here:

```hocon
dependencies {
  theirs { git = "github.com/edadma/sysl-json", version = "1.4.0", mount = "ejson" }
}
```

which hangs that whole package under one segment, so its `json` is `ejson.json` and your own `json`
is untouched. A mount is yours alone: another project may mount the same package differently, and
both still link one copy of it.

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

## No build scripts, ever

**A package cannot run code at build time.** Not a hook, not a script, not a plugin. `sysl add` and
`sysl build` read and write files and run nothing.

Most of what other ecosystems need build scripts for is compiling vendored C, and sysl already
compiles a library's C declaratively — the linker inputs a package needs are `@link` attributes in
its source, not a program that computes them. What that buys is most of the supply-chain story: a
package that cannot execute during installation cannot exfiltrate anything during installation.
