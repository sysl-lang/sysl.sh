---
title: The command line
summary: Six subcommands, the flags they share, and what each one leaves as an exit status.
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
| `sysl targets` | list the machines sysl can build for |

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

Building one needs an `llvm-ar` as well as a `clang`, because a `.syslib` **is** an `ar` archive —
[installation](/getting-started/installation/) has the note about which `ar` and why the platform
one will not do.

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

### `targets`

The registry, one line per machine — the name to write after `--target`, the LLVM triple it stands
for, and, for a target sysl knows and cannot build for, why not:

```
aarch64-macos         arm64-apple-macosx
x86_64-macos          x86_64-apple-macosx
aarch64-linux         aarch64-unknown-linux-gnu
x86_64-linux          x86_64-unknown-linux-gnu
riscv64-linux         riscv64-unknown-linux-gnu
x86_64-windows        x86_64-pc-windows-msvc
aarch64-freestanding  aarch64-none-elf
x86_64-freestanding   x86_64-unknown-none-elf
riscv64-freestanding  riscv64-unknown-elf
thumb-freestanding    thumbv8m.main-none-eabihf
thumb-freestanding-softfp  thumbv8m.main-none-eabi
riscv32-freestanding  riscv32-unknown-elf
x86-linux             i386-unknown-linux-gnu  (no C calling convention has been measured for x86)
```

The line for the machine you are on is marked `(this machine)`, and a last line repeats what that
machine's own runtime called itself. That last line is there for the case the rest of the list
cannot help with: on a machine sysl has no entry for, it is the only place to read what the machine
actually said.

**The last three freestanding rows are 32-bit, and they are one board's two halves.** The RP2350 boots
either a pair of Cortex-M33s or a pair of RV32IMAC cores, and both are here because a microcontroller
is what *freestanding* is mostly for — the three 64-bit freestanding rows reach kernels and
hypervisors, which is a different audience. `thumb` rather than `arm` names the Arm one because a
Cortex-M executes Thumb only, so an arm written for A32 would assemble for a machine that cannot run
it.

**The Cortex-M33 has two rows because the float ABI is the C project's to pick rather than sysl's.**
`thumb-freestanding` passes floating-point arguments in VFP registers, which is what `eabihf` selects
and what clang does with `thumbv8m.main` unasked; `thumb-freestanding-softfp` passes them in core
registers, which is what `-mfloat-abi=softfp` means and what pico-sdk builds by default. The two
cannot be mixed — GNU ld refuses the link outright, saying one object *"uses VFP register arguments"*
and the other *"does not"* — so a sysl archive joining a C build has to agree with that build, and
offering only the first row meant the C had to be rebuilt to follow sysl. Pick the one your project
already uses; if you do not know, `softfp` is the pico-sdk default.

`softfp` is not `soft`, and the difference is worth a sentence because both words are in circulation:
`-mfloat-abi=soft` means no FPU instructions at all, while `softfp` uses the `fpv5-d16` this core has
and changes only the calling convention.

`x86-linux` is listed *because* it cannot be built for. The limit is the compiler's rather than the
machine's, and **it is no longer the width** — this page said so until the two rows above it arrived.
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
| `-D NAME` or `-D NAME=value` | a macro the C beside a module is compiled with; may be given more than once |
| `-O <level>` | the optimization level handed to clang |
| `-v`, `--verbose` | report what the build decided — the standard module, the files read, the command lines |
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
sysl: error: unknown target 'arm-linux' — sysl knows aarch64-macos, x86_64-macos, aarch64-linux, x86_64-linux, riscv64-linux, x86_64-windows, aarch64-freestanding, x86_64-freestanding, riscv64-freestanding, thumb-freestanding, thumb-freestanding-softfp, riscv32-freestanding, x86-linux
```

### `-v`, `--verbose`

What the build decided, on stderr — which is where `wrote <exe>` goes, so stdout stays whatever the
build was for:

```
sysl: 1 source file(s) under hello
sysl:   read hello/hello.sysl
sysl: standard module linked from ~/Library/Caches/sysl/0.0.19-…/std.syslib
sysl: link: clang --target=arm64-apple-macosx -Wno-override-module -O1 …
```

Three things, and they are the three that have actually been the answer to a question: **which
standard module** the compilation got and whether it was linked or compiled from source, the **files
it read**, and the **command lines** handed to clang together with the `--lib`, `--link-path` and
`--include-path` searches behind them. There are no phase timings: a build that is slow is diagnosed
by asking what it *did*.

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

A compiler diagnostic is printed exactly as the compiler wrote it, with its location and a caret
under the offending column. A driver error — something that went wrong *around* the compilation
rather than inside it — is prefixed `sysl: error:`, which is why every message quoted on this page
carries it and none of the ones on the language pages do.

---

Next: the [tour](/tour/), which uses `run` throughout.
