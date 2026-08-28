---
title: The process module
summary: "`sysl.process` — starting another program and waiting for it: `run`, `capture`, `Status`, and why there is no shell anywhere in it."
weight: 74
---

**Every declaration in `sysl.process`, with its signature:** [the generated API page](/api/sysl-process/#index). This page is the argument — what the module is for, and how its pieces fit; that one is the list.

`sysl.process` starts another program and waits for what it does. Two functions: `run`, which lets
the child share this program's streams, and `capture`, which collects what it wrote.

```sysl
import sysl.process.{run, capture}
import sysl.text.Search

// `true` and `false` are on every hosted system and do exactly what their names say.
print(run("true").unwrap())
print(run("false").unwrap())

val out = capture("echo", ["hello from a child"]).unwrap()

print(out.text.trim())
print(out.status.ok())
```

```output
exited
exited 1
hello from a child
true
```

It requires `os`. A freestanding target has no processes to start, and nothing here can be given a
body on one.

## A program that fails is not a failure

**`Err` is for a child that could not be *started*.** A program that ran and exited non-zero is
`Ok`, carrying a `Status` that says so — it did start, and its exit status is an answer rather than
a failure of the call.

```sysl
import sysl.process.run

run("sysl-no-such-program") match
    Ok(s) -> print("it ran, and", s)
    Err(e) -> print("it did not start:", e)

run("false") match
    Ok(s) -> print("it ran, and", s)
    Err(e) -> print("it did not start:", e)
```

```output
it did not start: no such file or directory
it ran, and exited 1
```

The error half is [`sysl.fs`](/library/fs/)'s `IoError`, so a missing program is `NotFound` and one
that is not executable is `PermissionDenied` — the same cases, from the same numbers.

**Telling those two apart is not free, and most languages do not.** A child that cannot exec has no
way to return, so the conventional answer is to exit `127` — which is indistinguishable from a
program that ran and chose to exit `127`. This module's child reports the failure through a
close-on-exec pipe instead, so `NotFound` means what it says.

## Nothing goes through a shell

The arguments are a list rather than one string, and the list is handed to the program exactly as
written. There is no quoting to get right because there is nothing to quote for.

```sysl
import sysl.process.capture
import sysl.text.Search

// Under a shell this would be three words and a second command. It is one argument.
val out = capture("echo", ["one; two", "three four"]).unwrap()

print(out.text.trim())
```

```output
one; two three four
```

A filename with a space in it is one argument, and one with a `;` in it is not a second command.

## How a child ended

`Status` has two cases, because they are not the same kind of answer: an exit status is something the
program chose, and a signal is something that happened to it.

```sysl
import sysl.process.Status

print(Status.Exited(0))
print(Status.Exited(2))
print(Status.Signalled(9))

print(Status.Exited(0).ok())
print(Status.Exited(137) == Status.Signalled(9))
```

```output
exited
exited 2
killed by signal 9
true
false
```

**A shell folds the two together as `128 + n`**, which makes a program killed by `SIGKILL`
indistinguishable from one that deliberately exited `137`. The last line is that distinction.

## Where it starts, and what it can see

Both calls take a directory and a list of variables. The directory is where the child starts —
**this program does not move** — and an empty one means wherever it already is.

```sysl
import sysl.process.{capture, Var}
import sysl.text.Search

val out = capture("printenv", ["GREETING"], "", [Var("GREETING", "hello")]).unwrap()

print(out.text.trim())
```

```output
hello
```

The variables are **added** to what this program has rather than replacing it, so the child keeps its
`PATH` and its `HOME`. They are set in the child, in the window between the fork and the exec, where
the process is single-threaded and this program's own environment is untouched — which is why
[`sysl.env`](/library/env/) has no `set` and does not want one.

**`PATH` is the one whose effect starts before the child does.** Because the variables are in place
before the program is looked up, setting it decides *where the program is looked for*. A caller
handing a child a `PATH` meant for its own children should name the program by an absolute path.

## Capture goes through a file, not a pipe

Deliberate, and worth knowing rather than hiding: a pipe has a buffer, and a parent that waits for a
child while the child waits for the parent to drain that buffer is a deadlock that only appears once
the output gets long enough. Nothing here can deadlock, and the file is removed before `capture`
returns.

**Standard error is left alone unless it is asked for.** By default it goes wherever this program's
does, which is what a shell's `$(...)` leaves it doing — a tool asking a program a question wants the
answer without a warning mixed into the middle of it, and the warning is still worth seeing.

## Reading why a child failed

A child that exits non-zero has usually said why, on the stream `capture` lets through to the
terminal — where a person may not be looking and a program cannot read it at all. `stderr = true`
collects it into `Output.err`, and takes it off the terminal.

```sysl
import sysl.process.capture
import sysl.text.Search

val quiet = capture("sh", ["-c", "echo why >&2; exit 3"]).unwrap()

print(quiet.status, quiet.err.is_none())

val loud = capture("sh", ["-c", "echo why >&2; exit 3"], "", [], true).unwrap()

print(loud.status, loud.err.unwrap().trim(), loud.text == "")
```

```output
exited 3 true
exited 3 why true
```

**`err` is an `Option[string]` and the two empty answers are different.** `None` is "this call did
not collect standard error"; `Some("")` is "it was collected and the child wrote nothing". A bare
string could say only the second, so a tool reporting a failure would have had to guess which it
had.

```sysl
import sysl.process.capture

val asked = capture("true", [], "", [], true).unwrap()

print(asked.err.is_some(), asked.err.unwrap() == "")
print(capture("true").unwrap().err.is_none())
```

```output
true true
true
```

The second stream goes through a second file, on the same mechanism and for the same reason: two
pipes is where the deadlock above gets *easier* to reach, and two files cannot deadlock at all.

## What is not here

**Process *management*.** There is no pid, no signal you can send, no process group, and no way to
hold a running child: every call starts one program and waits for it. That covers what a build tool,
an installer or a command-line front end does. A program that wants to supervise children wants a
different surface, and it would belong under `sysl.posix`, where a binding goes when it *is* POSIX
rather than merely implemented with it.

**And that is why this module is `sysl.process` rather than `sysl.posix.process`.** Starting a child
is the same idea on every hosted system — a program, its arguments, and how it ended — and only the
mechanism underneath differs. [`sysl.fs`](/library/fs/) made the same call for the same reason.
