---
title: The command line
summary: The subcommands, the flags they share, and what each one leaves as an exit status.
weight: 30
---

Every subcommand takes a **path**, and a path is either a project root or a single file. That is not
two modes bolted together — a module is a directory and its name is that directory's path relative
to the root, so naming a directory compiles the whole tree under it, one module per directory, and
naming a file compiles that file alone. The [modules reference](/reference/modules/) has the rule
the path is standing on.

## The subcommands

| | what it does |
|---|---|
| `sysl run <path>` | compile and execute |
| `sysl build <path> -o <exe>` | compile to a native executable |
| `sysl build-lib <path> -o <artifact>` | compile a library to a linkable artifact |
| `sysl build-c <path> -o <archive>` | compile to a static archive and a C header, for a C project |
| `sysl test <path>` | run the `@test` functions |
| `sysl emit-llvm <path>` | print the generated LLVM IR |
| `sysl emit-header <path>` | print the C header for what a module exports |
| `sysl weave <path>` | render a literate source as an HTML document |
| `sysl tangle <path>` | print the program a literate source holds |
| `sysl targets` | list the machines sysl can build for |

`sysl prove` is an eleventh, and it has a page of its own — see
[verification](/reference/verification/#sysl-prove).

A subcommand is required; sysl with none exits 2 and prints its usage.

### `run`

```bash
sysl run hello.sysl
```

Four things in a row: the source is parsed and checked, textual LLVM IR is emitted, `clang`
assembles and links it, and the binary runs. The executable goes to a temporary file and is removed
afterwards — `run` leaves nothing behind.

**Everything after a bare `--` belongs to the program**, not to sysl:

```bash
sysl run report.sysl -- --verbose report.txt
```

The split is made *before* sysl's own options are parsed, which is the point: `--verbose` really is
one of sysl's own options, so without the `--` sysl would have taken it — after it, it belongs to the
program, and neither side has to know what the other's flags are called. What arrives at
[`main(args: []string)`](/library/args/) is the executable's own path followed by those two words,
so `args.len` is 3 — `args[0]` is the program, exactly as C's `argv[0]` is.

**`run` exits with the status the program exited with.** It is running your program, so its status
is your program's — a compilation that failed is what exits 1 on sysl's own behalf.

**The program's input is sysl's input**, so a program that reads works under `run` exactly as the
built binary does:

```bash
printf 'one\ntwo\n' | sysl run count.sysl
```

What the program writes comes out as it writes it rather than all at once when it finishes, which is
what makes a program that prompts usable here at all.

### `build`

```bash
sysl build hello.sysl -o hello
```

The same compilation, stopping at the executable instead of running it. `wrote hello` goes to
stderr so that stdout stays whatever the build was for.

**`-o` is optional, and where it writes depends on what you named.** Given a **file**, the name is
that file's, with its extension dropped, in the current directory: `src/tools/fmt.sysl` becomes
`fmt` beside you.

Given a **directory**, the executable goes *inside* it, named after it. `sysl build .`,
`sysl build fmt` and `sysl build ../fmt` are three ways of naming one project, and all three write
`fmt/fmt` — so the answer does not depend on where you were standing when you asked. `build-lib`
follows the same rule, writing `fmt/fmt.syslib` into the root it was built from.

**A project whose `package.hocon` names dependencies gets them fetched here**, if this machine has
not got them already — see [packages](/reference/packages/). `run` and `test` do the same; there is
no separate step to remember, and a project with no dependencies does none of it.

### `build-lib`

```bash
sysl build-lib mylib -o mylib.syslib
sysl run prog.sysl --lib mylib.syslib
```

A library compiled once into the two halves a program links against. See
[modules](/reference/modules/) for what an artifact holds and why the generic half of it travels as
trees rather than as object code.

**A library may itself be built on another one**, and `--lib` is what says so — it takes an artifact
or a source root here exactly as it does for a compilation:

```bash
sysl build-lib sdl3 -o sdl3.syslib
sysl build-lib sdl3-ttf --lib sdl3.syslib -o sdl3-ttf.syslib
```

**Unlike `build`, `run` and `test`, this does not fetch.** A package with a `dependencies` block is
refused rather than resolved over the network, and the message names the dependency and points at
`--lib`. A command whose whole job is to compile one tree into an artifact for one machine should not
be the thing that goes looking; the cost is that such a package writes its dependency down twice.

Building one needs an `llvm-ar` as well as a `clang`, because a `.syslib` **is** an `ar` archive —
[installation](/getting-started/installation/) has the note about which `ar` and why the platform
one will not do.

**It compiles the package's C, so it asks what that C needs.** A package that declares its
[header requirements](/reference/packages/#headers-a-package-needs-and-does-not-carry)
is refused here without `--include-path <name>=<dir>`, exactly as it is for a `build` — this being
the command a package is *published* by rather than merely built by. It is asked for the package's
own manifest and nothing else: the C of a `--lib` source root is not compiled here, so that root's
declaration is not charged to a library built against it.

### `build-c`

```bash
sysl build-c mylib -o libmylib.a
sysl build-c mylib -o libmylib.a --header include/mylib.h
```

`build-lib`'s shape with a different destination: a **static archive** an existing C project links,
and a **C header** declaring whatever the module marked `@export`. The compilation is the ordinary
one rather than a library build — what is wanted is a module lowered for this target with its calls
resolved — and what differs from `build` is that no entry point is emitted, since the C side supplies
its own `main`.

The header goes beside the archive with `.h` appended unless `--header` names somewhere else. Both
paths are announced on stderr, along with the archives the C project's own link line will still need
— an unresolved sysl symbol over there reads as a missing definition rather than as a missing
archive, so it is worth being told before you meet it. `--no-std-lib` folds the standard library into
the object and the archive then stands alone.

Like `build-lib`, this needs an `llvm-ar` as well as a `clang`. [FFI](/reference/ffi/) has `@export`
itself — what may be exported, what a symbol is named, and why a computed module `val` cannot be
reached from one.

### `emit-header`

```bash
sysl emit-header mylib
```

The same header `build-c` writes, on stdout and with nothing built, for a project that generates its
headers as a build step.

### `test`

```bash
sysl test <path>
sysl test <path> --filter <text>
sysl test <path> --fail-fast
sysl test <path> --std
```

The tree is compiled once, into a binary that runs one named test per process, and the runner starts
it once per test. [Attributes](/reference/attributes/) has `@test` itself — what a test may be, what
every other build does with one, and why the process per test is the mechanism rather than a cost.

**It takes the search-path flags too** — `--link-path`, `--include-path` and `-D`, exactly as `build`
does, and it needs them for the same reasons. A tree whose C includes a header the toolchain does not
already know about, or whose constants come from a [`c const`](/reference/ffi/) block over one, is a
tree whose *tests* have to compile that C as much as its programs do. A package binding a system
library is the ordinary case rather than a corner of one, so a `test` that could not be given those
directories would be a `test` most packages could not run.

`--std` says the tree **is** the standard module, which is how sysl's own library is tested. The
compiler supplies `sysl` to every compilation, so without it the library arrives twice — once as the
tree being compiled and once as the copy handed over — and every declaration is already declared.
Nothing infers it: a program with a `sysl` directory of its own is nearly always a mistake, and a
build that guessed would turn that refusal into a collision at the link.

The report groups by the file each test was written in, keeps source order inside a file, and shows
a test's output **only** where it failed:

```
running 4 tests

clamp.sysl
  ok    clamps below the low bound   6ms
  ok    clamps above the high bound  5ms
  FAIL  leaves a value in range      5ms
        did not return — exit status 1
        at clamp.sysl:31
        > panic: clamp(4, 1, 3) should be 3
  ok    is idempotent                5ms

3 passed, 1 failed — 21ms
```

A failure's own line is one of three sentences and never more: **`did not return — exit status n`**,
which is what a failed `assert` looks like since `assert` prints and exits; **`returned, and was
expected to trap`**; and **`trapped, but printed nothing holding "…"`**, for a
`@test(should_trap: "…")` whose run trapped without saying it. Everything the run printed follows
underneath, prefixed `>`.

`--filter` keeps the tests whose name **or module** holds the text, and the header says how many of
how many are running. `--fail-fast` stops the loop rather than the report: what ran is still
reported and what never ran is simply absent, because "skipped" would be a third verdict for
something that is not a verdict.

**Exit status is 0 if and only if every test that ran passed.** A tree with no tests at all, and a
filter that matched none of the tests there are, both exit 0 — neither is a failure, and each says
which of the two happened rather than leaving one empty report to mean both.

**None of that mechanism exists on a microcontroller.** A process per test, a name in `argv`, a
verdict in an exit status: a board has none of the three, so `sysl test` cannot follow the code onto
one. [`sysl.harness`](/library/harness/) is the other half — a framework linked *into* the image,
which names its tests, locates a failure and prints a tally through a writer you hand it. Use this
command for everything that runs on the machine you are typing on, and that for the checks that only
exist on the target.

### `emit-llvm`

```bash
sysl emit-llvm hello.sysl
```

The IR to stdout, the same text `run` and `build` hand to clang. Nothing is assembled and no
toolchain is needed for it.

### `weave`

```bash
sysl weave guide/lisp/lisp.lsysl -o lisp.html
sysl weave library/sysl/regex -o documents/
```

A **literate** source rendered as an HTML document. A `.lsysl` file is a Markdown document whose
four-column-indented part is the program, which is what makes one readable with nothing rendering it
— and an indented code block carries no *language*, so nothing can highlight it. `weave` tells the
renderer that an indented block is sysl, which is the whole of the transformation: the source reaches
the renderer exactly as written, and prose, tables, illustrations and heading levels are its own
business.

What comes out is one file that opens by itself. It carries its own styling, in a light and a dark
palette; its code is coloured by the same grammar this site highlights with; and its mathematics is
set by KaTeX, which the page links. That last is the one thing a woven document needs the network
for — the prose and the code are markup in the file, so a document read offline loses its equations
to TeX source and nothing else.

The output goes to standard output, or to what `-o` names. **A path holding several literate sources
writes one document each**, and `-o` then names a directory: a woven document is something somebody
opens, so the unit is the file that was written rather than the tree. The ordinary `.sysl` files
alongside are passed over, and a tree with no literate source at all is refused rather than producing
an empty page.

It is a **source-level** command: no target, no standard module, no libraries. A package's prose is
worth reading on a machine that could not build it. What it does share with a compilation is the
reading, so a file the compiler would refuse — a tab in an indent, a fence that is never closed — is
refused here too, with the same message.

This is not an API reference generated from declarations. sysl has no documentation comment yet, so
there is nothing for such a thing to read, and `doc` is left unclaimed for the day there is.

### `tangle`

```bash
sysl tangle guide/lisp/lisp.lsysl
sysl tangle guide/lisp/lisp.lsysl -o lisp.sysl
```

The other half of a literate system: the program, with the prose stripped. A build tangles anyway —
that is how a `.lsysl` file compiles at all — so what this adds is a way to **see** it.

That is worth having when a literate file misbehaves. A block indented that should not have been, a
fence that swallowed a function: the question is always what the compiler actually read, and this is
how to ask. It also hands the program to anything that does not know the format — a tool, a paste, a
bug report.

**The prose is replaced by blank lines rather than removed**, so line 100 of the output is line 100
of the source. That is what lets a diagnostic about the program point into the document it was
written in, and it is why the output is not as short as the program looks.

### `targets`

The registry, one line per machine — the name to write after `--target`, the LLVM triple it stands
for, and, for a target sysl knows and cannot build for, why not:

```
aarch64-macos                arm64-apple-macosx
x86_64-macos                 x86_64-apple-macosx
aarch64-linux                aarch64-unknown-linux-gnu
x86_64-linux                 x86_64-unknown-linux-gnu
riscv64-linux                riscv64-unknown-linux-gnu
x86_64-windows               x86_64-pc-windows-msvc
aarch64-android              aarch64-linux-android24
aarch64-freestanding         aarch64-none-elf
x86_64-freestanding          x86_64-unknown-none-elf
riscv64-freestanding         riscv64-unknown-elf
thumb-freestanding           thumbv8m.main-none-eabihf
thumb-freestanding-softfp    thumbv8m.main-none-eabi
thumb-freestanding-soft      thumbv8m.main-none-eabi
thumbv6m-freestanding        thumbv6m-none-eabi
thumbv7m-freestanding        thumbv7m-none-eabi
thumbv7em-freestanding       thumbv7em-none-eabihf
thumbv7em-freestanding-soft  thumbv7em-none-eabi
riscv32-freestanding         riscv32-unknown-elf
wasm32-freestanding          wasm32-unknown-unknown
craft-freestanding           craft
x86-linux                    i386-unknown-linux-gnu  (no C calling convention has been measured for x86)
```

The line for the machine you are on is marked `(this machine)`, and a last line repeats what that
machine's own runtime called itself. That last line is there for the case the rest of the list
cannot help with: on a machine sysl has no entry for, it is the only place to read what the machine
actually said.

**`aarch64-android` is the one row whose triple carries a version number, and the one that needs
something set in your environment.** The `24` is an Android API level — which of Bionic's declarations
exist — and it is in the triple because clang requires it there: without a level, no `__ANDROID_API__`
is defined and the first system header that guards a declaration on it refuses to compile. Its C
calling convention is `aarch64-linux`'s, because AAPCS64 is AAPCS64; what differs is everything above
the ABI, which is why it is a system of its own and `#if android` is a symbol distinct from
`#if linux`.

What it needs is *which clang*. Every other target here is served by a compiler that has the right
back end, and having the back end is not the same as having the toolchain: Android's headers and
libraries are the NDK's, and no clang outside it carries them. So sysl asks the environment.
`ANDROID_NDK_ROOT` or `ANDROID_NDK_HOME` names an NDK outright; otherwise `ANDROID_HOME` names the
SDK and the newest `ndk/<version>` under it is used.

```
export ANDROID_HOME=~/Library/Android/sdk
sysl build --target aarch64-android hello.sysl
```

`ANDROID_SDK_ROOT` is read too and means the same thing, but Android's own documentation marks it
deprecated in favour of `ANDROID_HOME` — so set that one, and if you already have both, keep them
pointing at the same directory, which is what Android Studio and the Gradle plugin check.

Nothing is guessed at. An NDK sits wherever you installed it, so a compiler that went looking through
your home directory would find one on the machine it was written on and the wrong one — or none —
anywhere else, and building against the wrong platform headers is a failure you would not see. With
nothing set, the build stops and says what to set:

```
sysl: error: building for Android needs the NDK's own clang, and nothing here says where it is — no clang outside the NDK carries Bionic's headers, so one picked for having the back end fails at the first '#include'. Set ANDROID_HOME to the Android SDK (the directory holding 'ndk/'), or ANDROID_NDK_ROOT to one NDK directly
```

That message exists because the alternative was worse: pick the host's clang for having `aarch64`, and
the build dies on a missing `dirent.h` inside the standard library's own C — which reads as a broken
library rather than as the wrong compiler.

Naming the NDK is the whole of what is required. Its clang resolves a sysroot from its own location,
so there is no `--sysroot` to pass and no include or library path to set up: the result is an ordinary
position-independent Android executable, which is what an APK loads.

**Eight of the freestanding rows are 32-bit microcontrollers.** The RP2350 — the Pico
2 — boots either a pair of Cortex-M33s or a pair of RV32IMAC cores; the RP2040, the original Pico, has
a pair of Cortex-M0+; the Armv7E-M rows are ST's parts; and Armv7-M is the Cortex-M3. All are here
because a microcontroller is what *freestanding* is mostly for: the three 64-bit freestanding rows
reach kernels and hypervisors, which is a different audience. `thumb` rather than `arm` names the Arm
ones because a Cortex-M executes Thumb only, so an arm written for A32 would assemble for a machine
that cannot run it.

**The Cortex-M33 has three rows, because neither the float ABI nor the FPU's presence is sysl's to
pick.** `thumb-freestanding` passes floating-point arguments in VFP registers, which is what `eabihf`
selects; `thumb-freestanding-softfp` passes them in core registers, which is what
`-mfloat-abi=softfp` means and what pico-sdk builds by default. The two
cannot be mixed — GNU ld refuses the link outright, saying one object *"uses VFP register arguments"*
and the other *"does not"* — so a sysl archive joining a C build has to agree with that build, and
offering only the first row meant the C had to be rebuilt to follow sysl. Pick the one your project
already uses; if you do not know, `softfp` is the pico-sdk default.

**Both of those rows use the M33's own unit, which is single precision.** `f32` arithmetic is
instructions and `f64` arithmetic is a call into the board's runtime — `__aeabi_dmul` and its family
— because an `fpv5-sp-d16` has no double-precision instructions to select. That is the part rather
than a setting, and it is worth knowing before a `f64` goes into an inner loop.

**`thumb-freestanding-soft` is the third, and it is for a board with no unit at all.** `softfp` is not
`soft`: `-mfloat-abi=soft` means no FPU instructions whatever, while `softfp` uses the `fpv5-sp-d16`
this core has and changes only the calling convention. That distinction is not something a triple can
carry — both rows are `thumbv8m.main-none-eabi` — so sysl says which it is on every clang command
line, with the convention beside it: `-mfloat-abi=soft -mfpu=none` for the `soft` row,
`-mfloat-abi=softfp -mfpu=fpv5-sp-d16` for `softfp`, and `-mfloat-abi=hard -mfpu=fpv5-sp-d16` for the
first. None of that is left to the compiler's default, because the default is not the same one twice:
the same triple reports a floating-point unit under some clangs and none under others, so a row that
said nothing would mean a different machine depending on which was installed. **Reach for it when your board's headers say the FPU is off**, which is
where the difference announces itself: CMSIS refuses the build with *"Compiler generates FPU
instructions for a device without an FPU (check `__FPU_PRESENT`)"*, and every Zephyr MPS2 board is
configured that way. Getting it wrong the other way is worse than a refusal — the image links, boots,
and takes a fault at whatever arithmetic reached a VFP instruction first.

`thumbv7em-freestanding` and `thumbv7em-freestanding-soft` are that same pair for Armv7E-M — an
STM32 F4 or H7 with the FPU on, and one with it off.

**`thumbv6m-freestanding` and `thumbv7m-freestanding` are different architectures, not further
conventions.** The first is the RP2040's Cortex-M0+ — Armv6-M, which came before Armv8-M rather than
being a subset of its options — and the second is the Cortex-M3. Build an original Pico's program for
`thumbv6m`; building it for a `thumb-` row produces instructions the core cannot execute, and the
failure is a fault at whatever ran first rather than a refusal at the link. Neither core has a
floating-point unit in the architecture, so neither needs a `-soft` sibling: there was never a second
answer to give.

Build a Cortex-M3's program for `thumbv7m` rather than for `thumbv6m`, even though Armv6-M code runs
on an M3. What does not survive the substitution is the *headers*: a real project reads its own
configuration, so it takes `CONFIG_CPU_CORTEX_M3` to mean Armv7-M and reaches for `BASEPRI`, while
CMSIS reading an Armv6-M triple hands it an intrinsic set that has none.

**One thing needs the board's help on that target, and only one.** Armv6-M has no atomic
instructions, so a program using `&sync` — the shared counted reference — compiles to calls the
toolchain does not supply, and the link fails naming `__atomic_fetch_add_4`. Every other program is
unaffected, because those calls are emitted only for a program that holds a `&sync`. The
[`pico`](https://github.com/sysl-lang/pico) package supplies them over one of the chip's hardware
spinlocks, which is what a dual-core part needs — masking interrupts is per-core and would leave the
other core free to lose the update.

**`wasm32-freestanding` is the odd row: 32-bit, freestanding, and not a board at all.** It is
WebAssembly — a browser, `wasmtime`, or whatever else embeds a module — and `unknown-unknown` in the
triple is the literal truth, so it comes with no libc, no loader and nothing that runs before an
exported function is called. Sysl links it with `-nostdlib` and names `main` as the module's entry,
which is what makes `main` reachable and exports it under that name; a program that does not print
comes out as a couple of hundred bytes of `.wasm` that `wasmtime` will run, and a program that does
print fails at the link naming `putchar`, exactly as on any other bare target.

It needs a clang with the WebAssembly back end. Apple's has eleven back ends and this is not among
them, so on a Mac sysl reaches for Homebrew's LLVM by itself — the same fallback it already makes for
the RISC-V rows — and says so if it cannot find one.

**`craft-freestanding` is 16-bit, and it is the one row sysl will not drive a build for.** It is
CRAFT — a teaching ISA with eight registers, a 64 KiB virtual address space and a
software-managed TLB — whose LLVM back end lives out of tree, so what exists is an `llc` rather than
a compiler driver. The machine has no libc, no object format and **no linker**: its assembler reads
one file and resolves every label inside it. So sysl writes the LLVM and stops, and the rest is two
commands of your own:

```
sysl emit-llvm hello.sysl --target craft-freestanding > hello.ll
llc -march=craft hello.ll -o hello.s
craft as hello.s
```

Every other subcommand refuses this target and says that. It is not a target sysl cannot *lower*
for — `emit-llvm` produces an ordinary module — it is one with nothing for a driver to call.

**Sixteen bits is the part that shows up in your code.** `usize` is pointer-width by definition, so
a slice's length is a `u16` and the address space is the bound on everything. An `int` is still 32
bits and a `long` still 64, because a width is the language's answer rather than the machine's — so
ordinary arithmetic here is multi-word, and the back end expands it, exactly as every 32-bit target
already expands a `long`. Indexing with an `int` needs no conversion for it: an index wider than an
address is checked against what an address can name and then narrowed, so `for i in 0..<4 do b[i] …`
means what it means everywhere else.

`x86-linux` is listed *because* it cannot be built for. The limit is the compiler's rather than the
machine's, and **it is no longer the width** — this page said so until the 32-bit rows above arrived.
What is missing is a C calling convention measured against clang, which is the only way a target's
answers are allowed to be arrived at. A reader who names it is better told that than told the name is
unknown.

**Freestanding does not mean self-contained.** A program built for a bare board still names C symbols
its runtime has to define — `putchar` wherever anything prints, `free` wherever a reference count can
reach zero, `memcpy` and `memset` for a structure assignment you never wrote — and on a 32-bit
machine it names a 64-bit division helper as well, because a `long` is sixty-four bits everywhere and
neither RP2350 core has the instruction. None of that is a sysl dependency the compiler could warn
about: it is what any C compiler emits for the same code, and a real project never meets it because
its SDK has linked `libgcc` or compiler-rt already. Meeting it looks like `undefined symbol:
__aeabi_ldivmod` at the link, which is the one place anybody will come looking for this paragraph.

## Flags every subcommand takes

| | |
|---|---|
| `--target <name>` | the machine to build for; defaults to this one |
| `--lib <path>` | a library to compile against; may be given more than once |
| `--std-lib <path>` | a prebuilt standard module |
| `--no-std-lib` | compile the standard module from source rather than linking a prebuilt one |
| `--ar <path>` | the `llvm-ar` to build a library with |
| `--link-path <dir>` | where to look for a library a `link` directive named; may be given more than once |
| `--include-path <dir>` | where to look for a header the C beside a module includes; may be given more than once |
| `--include-path <name>=<dir>` | the same, and it answers the header requirement a package declared under that name |
| `-D NAME` or `-D NAME=value` | a macro the C beside a module is compiled with; may be given more than once |
| `-O <level>` | the optimization level handed to clang |
| `-v`, `--verbose` | report what the build decided — the standard module, the files read, the command lines, and where `build-lib` staged |
| `--explain-escapes` | report every local array promoted to the heap |

The standard-module flags and `-O` are covered in
[installation](/getting-started/installation/), including why the default is `-O1` and not off.

### `--link-path`, `--include-path` and `-D` are three steps of one thing

A module that binds a C library carries C of its own, and that C has to be found, compiled and
linked. The three flags answer the three ways it fails, in the order it fails them:

- **`--include-path`** — the shim `#include`s the library's header and cannot find it. This is the
  first failure and the one that surprises, because it happens before anything reaches a linker.
- **`-D`** — the header is found and refuses, because a C project of any size configures its own
  headers with macros and has not been asked. pico-sdk's `pico/cyw43_arch.h` `#error`s rather than
  guessing which architecture variant is meant, which is the shape to expect.
- **`--link-path`** — everything compiled and the archive is not where the linker looks.

Nothing is guessed at and nothing is defaulted. sysl does not add `/opt/homebrew/lib`, and it does
not invent a macro: a compiler that ruled on where a platform keeps its libraries, or on how a
project configures its headers, would be wrong on a machine nobody here has — and being wrong there
costs a build that fails somewhere its author cannot reach. What a project defines is the project's.

A build system that already knows these will have them: reading them out of CMake is a matter of
asking the target for its `INCLUDE_DIRECTORIES` and `COMPILE_DEFINITIONS` and handing each along.

A **package** whose C includes headers it does not carry can say so, and then the first failure above
stops being a surprise: written `--include-path <name>=<dir>`, the flag answers a requirement the
package declared, and a build that is missing one is refused by name before clang runs. See
[packages](/reference/packages/#headers-a-package-needs-and-does-not-carry).

### `--lib` takes either a source tree or an artifact

Which one a path names is read off the name: a `.syslib` is decoded, anything else is walked as
source. That is deliberate — how a library was shipped is the shipper's business, and a program
depending on one should not have to write down which it got. `build-lib` is what turns the first
into the second, and the only difference downstream is what the compilation *cost*: an artifact is a
linear decode where source is a parse.

### `--target`, and the one thing `run` will not do

Given no `--target`, a build is for the machine it is running on. If that is a machine sysl has no
entry for, it says so and stops rather than guessing, because a wrong guess produces a module that
looks right and is not.

`run` executes what it builds, and only this machine can do that, so a cross target is refused
before the build rather than after it:

```
sysl: error: 'run' executes what it builds, and 'x86_64-linux' is not this machine — use 'sysl build --target x86_64-linux'
```

A name the registry does not have is answered with the names it does:

```
sysl: error: unknown target 'arm-linux' — sysl knows aarch64-macos, x86_64-macos, aarch64-linux, x86_64-linux, riscv64-linux, x86_64-windows, aarch64-android, aarch64-freestanding, x86_64-freestanding, riscv64-freestanding, thumb-freestanding, thumb-freestanding-softfp, thumb-freestanding-soft, thumbv6m-freestanding, thumbv7m-freestanding, thumbv7em-freestanding, thumbv7em-freestanding-soft, riscv32-freestanding, wasm32-freestanding, craft-freestanding, x86-linux
```

### `-v`, `--verbose`

What the build decided, on stderr — which is where `wrote <exe>` goes, so stdout stays whatever the
build was for:

```
sysl: 1 source file(s) under hello
sysl:   read hello/hello.sysl
sysl: standard module linked from ~/Library/Caches/sysl/<version>-…/std.syslib
sysl: link: clang --target=arm64-apple-macosx -Wno-override-module -O1 …
```

Three things, and they are the three that have actually been the answer to a question: **which
standard module** the compilation got and whether it was linked or compiled from source, the **files
it read**, and the **command lines** handed to clang together with the `--lib`, `--link-path` and
`--include-path` searches behind them. There are no phase timings: a build that is slow is diagnosed
by asking what it *did*.

`build-lib` adds a fourth, because it is the one command that writes anywhere but the artifact it
was asked for:

```
sysl: members staged in /var/folders/…/sysl-lib-1729384756
```

The members are archived under names of their own rather than under whatever a temporary file was
called, which is what the directory is for, and it is removed whether the build succeeded or gave up
partway. The line is there for the run that is interrupted in between: what is left behind is a
directory nothing else would have named.

### `--explain-escapes`

On stderr, one line per local array the compiler moved to the heap, and the view that forced the
move:

```
$ sysl build --explain-escapes tty.sysl
tty.sysl:31:12: 'buf' is promoted to the heap, because this view of it is returned
```

The position is the **view**, not the declaration, because that is the half a reader cannot work out
for themselves. Where nothing was promoted it says so, in as many words, rather than printing
nothing and leaving you to wonder whether the flag took. [Memory](/reference/memory/) has what
promotion is and when it happens instead.

### `-O2` is written the way clang writes it

A short option normally takes its value as the next argument, and clang's optimization flag has been
written joined since cc. So `-O2` is rewritten into `-O 2` before the options are parsed — only that
letter, and only where something follows it, so a bare `-O` still takes the next argument and
`--optimize` is untouched. Nothing in that rewrite can reach the program's own arguments, which were
already split off at the `--`.

## Two combinations that are refused

Neither is resolved by precedence, because whichever precedence were chosen would silently discard
half of what was asked for:

```
sysl: error: --core-lib compiles against the standard module, and 'build-lib --core' is what builds it
sysl: error: --no-core-lib and --core-lib ask for different standard modules
```

The first is `build-lib --core --core-lib x`, which cannot mean anything: the declarations being
compiled are the ones the artifact holds. The second is the pair of near-identical spellings that a
typo produces.

## Exit statuses

| | |
|---|---|
| **0** | it worked — and for `test`, every test that ran passed |
| **1** | a compiler diagnostic, a driver error, or a failing test |
| **2** | the command line did not parse |
| *the program's* | `run` only, once the program has started |

A compiler diagnostic is printed exactly as the compiler wrote it: the message, the location, the
line it happened on, and the offending token underlined beneath it.

```
error: 'b' of 'add' is int, but string was given
 --> hello.sysl:7:14
  |
7 | print(add(x, "two"))
  |              ^^^^^
```

A driver error — something that went wrong *around* the compilation rather than inside it — is
prefixed `sysl: error:`, which is why every message quoted on this page carries it and none of the
ones on the language pages do.

---

Next: the [tour](/tour/), which uses `run` throughout.
