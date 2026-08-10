---
title: Standard Library
summary: What ships with the compiler, module by module — the core every program has, and the layers a target may not.
weight: 40
---

The [reference](/reference/) is the *language*: what the compiler reads, what a type is, what a
declaration binds. This section is the other half of what a program has — **the modules that ship
with the compiler**, and what each of them offers.

They are kept apart on purpose. Nothing in this section is a language feature: every type here is an
ordinary struct or enum, every function is ordinary sysl, and a program could have written any of it.
`Option` is a generic enum, `unwrap` is a member that calls `exit`, `print` is a library function
reached by a desugaring. The line matters because it is the language's own rule about itself —
**there are no functions built into the compiler that a program could not have written**, and the one
exception is the seam out to C.

So a page in the reference tells you what the compiler will accept. A page here tells you what
somebody already wrote for you, and where it will not be there.

## The library is a tree, and the tree is the point

`sysl` is one module with submodules under it, and each is a directory. A program reaches the core
without asking; everything below it is [imported](/reference/modules/) by name.

| module | holds | requires |
|---|---|---|
| [`sysl`](/library/core/) | the core — `Option`, `Result`, `Display`, the operator traits, `print`, `assert` | — |
| [`sysl.text`](/library/text/) | the whole text surface — validation, the character cursors, `Ascii` and `Search`, splitting and joining, `StrBuilder`, the parsers, `CString` | — |
| [`sysl.regex`](/library/regex/) | POSIX Extended Regular Expressions — `regex`, `Regex`, `Match` | — |
| [`sysl.buf`](/library/buf/) | `Buf[T]`, the growable sequence, and `ByteSink` | — |
| [`sysl.io`](/library/io/) | `Reader`, `stdin()`, `lines()` and `console_lines()`, and the in-memory `bytes_reader()` / `bytes_writer()` | — |
| [`sysl.fs`](/library/fs/) | files and paths — `read_text`, `write_bytes`, `exists`, `rename`, and `IoError` | `os` |
| [`sysl.math`](/library/math/) | `max`, `min`, `pi`, the float functions, the integer traits `Signed` and `Bits`, and the integer arithmetic above them — `pow`, `gcd`, `lcm`, `divmod`, `is_power_of_two`, `next_power_of_two` | — |
| [`sysl.math.complex`](/library/complex/) | `Complex[F: Float]` — the operators at two argument lists each, the transcendental set, and the branch cuts | — |
| [`sysl.time`](/library/time/) | `Instant` and `Duration` — with `5.ms` and `5.hours` on any integer — the civil calendar — `LocalDate`, `LocalTime`, `LocalDateTime`, `Offset` — the fixed-offset conversions, and the ISO 8601 renderers and parsers | — |
| [`sysl.sync`](/library/sync/) | `Atomic[T]`, `SpinLock`, and the five memory orderings | — |
| [`sysl.thread`](/library/thread/) | `spawn`, `Thread.join`, `yield_now`, and `Mutex[T]` | `threads`, `posix` |
| [`sysl.term`](/library/term/) | the escape sequences a terminal understands — colour, emphasis, and the screen | — |
| [`sysl.term.tty`](/library/term/#whether-to-write-escapes-at-all--sysltermtty) | whether to write them at all — `is_tty`, `color_wanted`, `color`, `color_err` — and taking the terminal over: `raw`, `cooked`, `flush`, `tty_writer` | `posix` |
| [`sysl.term.edit`](/library/term/#reading-a-line--syslterm-edit) | a line editor for a terminal with no line discipline — echo, editing, history, over a `Reader` and a `Writer` | — |
| [`sysl.slices`](/library/slices/) | what a program does *to* a `[]T` — searching, comparing, `reverse`, two sorts that neither allocate, `binary_search`, and `as_ptr` for a C binding | — |
| [`sysl.encoding`](/library/encoding/) | hexadecimal and base64 both ways, fixed-width integers to and from bytes at either byte order, and `DecodeError` | — |
| [`sysl.rand`](/library/rand/) | PCG32, seeded by the caller and reproducible — `below` without modulo bias, `range`, `unit`, `shuffle` | — |
| [`sysl.rand.sys`](/library/rand/#taking-a-seed-from-the-host--syslrandsys) | `seed_from_os`, kept apart so the generator stays freestanding | `posix` |
| [`sysl.args`](/library/args/) | command-line options — `Scan`, `Cli`, and `args_of` for a raw `argv` | — |
| [`sysl.harness`](/library/harness/) | a test framework that runs **on the target** — `run`, `check`, `check_eq`, `check_slice_eq`, `skip`, and a tally | — |
| [`sysl.sys`](/library/sys/) | the platform seam — what a freestanding target replaces | — |

**The split is by capability, not by taste.** `sysl.fs` is `requires os`, because a filesystem is
something the environment either has or does not; `sysl.thread` is `requires threads` and `posix`,
because pthreads is what it is built on; `sysl.term.tty` is `requires posix`, because `isatty` is; and
`sysl.rand.sys` is `requires posix`, because entropy comes from the kernel. Those four are the whole
of the column, and a module a target cannot support is therefore not one that fails to link — it is
one a [capability clause](/reference/modules/) will not let that program import in the first place.

It is also why `sysl.term.tty` and `sysl.rand.sys` are modules of their own rather than functions in
`sysl.term` and `sysl.rand`. **A requirement is module-wide**, so one function asking for `posix`
beside the escape sequences would have taken all forty constants away from the allocator-free programs
that most want to colour a line — and one asking for it beside the generator would have taken PCG32
away from every target that has no operating system to seed it from. Two instances of one shape, and
the shape is worth naming: where a module is portable except for how it gets started, the *getting
started* goes in a submodule.

That is why the atomics live apart from the threads: `sysl.sync` requires **nothing**, so a kernel
can have a spinlock without acquiring a scheduler along with it.

**`alloc` is not in that column, and the omission is the point.** No module requires it, because
allocation is refused at the **call** rather than at the import — so a program under `no alloc` still
imports `sysl.text` and still gets `from_utf8`, the cursors, and `Search`, and is refused only where
it reaches for `join` or a `StrBuilder`. A capability that gated whole modules would have cost the
allocator-free subset most of the library it can actually use.

## Where some of this already is

Three pieces of the library are documented in the reference instead, because the language has
machinery that only makes sense beside them:

- **`Option`, `Result` and the `Fallible` latch** are on [errors and
  contracts](/reference/errors/), because `?` is a language form and it is what those types are for.
- **`assert` and `panic`** are on [attributes and compile time](/reference/attributes/), beside the
  `@test` protocol they exist to serve.
- **The operator traits** — which trait a `+` or a `<` reaches — are on
  [expressions](/reference/expressions/), because dispatch is a rule about the operator rather than
  about the trait.

This section links to them rather than repeating them.
