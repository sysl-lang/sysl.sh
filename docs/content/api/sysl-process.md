---
title: sysl.process
layout: api-module
headingShift: 0
slugStyle: github
module: sysl.process
requires: "requires { os }"
---

## Index

[`capture`](#capture) [`run`](#run) [`Output`](#output) [`Status`](#status) [`Var`](#var) [Display for Status](#display-for-status) [Eq for Status](#eq-for-status)

## Functions

### `capture`

```sysl
capture(program: string, args: []const string = [], dir: string = "", env: []const Var = []) -> Result[Output, IoError]
```

Run a program, wait for it, and collect what it wrote to its standard output.

For asking a program a question -- what version it is, where something lives, what devices are
attached. The output is collected through a file rather than a pipe, which is not an
implementation detail worth hiding: a pipe has a buffer, and a parent that waits for a child
while the child waits for the parent to drain that buffer is a deadlock that only appears once
the output gets long enough. Nothing here can deadlock.

The file is removed before this returns, whether the child succeeded or not.

### `run`

```sysl
run(program: string, args: []const string = [], dir: string = "", env: []const Var = []) -> Result[Status, IoError]
```

Run a program, wait for it, and say how it ended.

**The child shares this program's streams**, so what it prints appears as it prints it and
anything it reads comes from the same place. That is what makes this the call for a build, an
install or anything else whose output a person is watching go by.

`dir` is where the child starts, and an empty one means wherever this program is. The child's
directory is its own -- this program does not move.

The error half is for a child that could not be *started*: a program that is not there reports
`NotFound`, one that is not executable reports `PermissionDenied`. **A program that ran and
failed is `Ok`**, carrying a non-zero `Status`, because it did start and its exit status is an
answer rather than a failure of this call.

## Types

### `Output`

```sysl
struct Output
    status: Status
    text: string
```

A child's standard output, and how it ended.

`text` is what the program wrote to its standard output and nothing else. **Standard error is not
captured**: it goes wherever this program's does, which is what a shell's `$(...)` leaves it
doing. A tool asking a program a question wants its answer without a warning it printed mixed
into the middle of it, and a warning is still worth seeing.

### `Status`

```sysl
enum Status
    Exited(code: int)
    Signalled(signal: int)
```

How a child ended.

Two cases rather than one number, because they are not the same kind of answer: an exit status is
something the program chose and a signal is something that happened to it. Collapsing them --
which is what a shell's `$?` does, reporting `128 + n` for a signal -- makes a program killed by
`SIGKILL` indistinguishable from one that deliberately exited 137.

| Member | Signature | Description |
|---|---|---|
| `ok` | `ok(self) -> bool` | Whether this is the answer a caller was hoping for: exited, and exited zero. |

### `Var`

```sysl
struct Var
    name: string
    value: string
```

One environment variable a child is to be started with.

**This is on the call rather than in `sysl.env`, and that module says why**: `setenv` mutates
state a whole process shares and is not safe against a concurrent read, so a program that wants a
child to see something different asks for it here. The variables are *added* to what this program
already has rather than replacing it -- a child that lost `PATH` and `HOME` because its parent
wanted to set one thing is a surprise, and the caller who genuinely wants an empty environment is
rare enough to be told this is not the call for it.

They are set in the child, in the window between the fork and the exec, where the process is
single-threaded by construction and this program's own environment is untouched.

**`PATH` is the one whose effect starts before the child does.** Because the variables are in
place before `execvp` runs, setting it decides where the program itself is looked for -- so a
caller handing a child a `PATH` meant for *its* children should name the program by an absolute
path. Consistent, and surprising exactly once.

## Implementations

### Display for Status

```sysl
impl Display for Status
```

### Eq for Status

```sysl
impl Eq for Status
```
