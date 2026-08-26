---
title: sysl.posix.tty
layout: api-module
headingShift: 0
slugStyle: github
module: sysl.posix.tty
summary: "Getting the kernel out of the way, so a program can read a terminal a keystroke at a time."
requires: "requires { posix }"
---

**This is what makes one program run at a macOS terminal and over a serial cable to a board.** At a
shell the line discipline echoes what is typed, honours backspace, and releases a whole line at
Enter -- so `sysl.term.edit` would echo a second copy of every character and never see a keystroke
until it was too late to act on one. Over a USB CDC port there is no line discipline to begin with,
which is why the editor exists. These two functions are the difference between those situations,
and they are the *only* difference: with them the same program serves both.

A program that wants whole lines and no editing wants none of this. `sysl.io.console_lines` reads a
cooked terminal, the kernel does the work, and that is the right facility for anything that is not
drawing its own line.

## It is `termios`, through a shim, and it used to be `stty`

This file argued at length for `system("stty …")`, and the argument had two halves. The first still
stands: `struct termios` is caller-allocated and laid out differently on every platform, so a
binding needs either its size or somewhere to keep it, and transcribing it into sysl is the thing
`library/sysl` refuses everywhere.

**The second half was that a shim was impossible here, and it is not any more.** It read: *a `.c`
here would need a C compiler and a sysroot on every target that did not prune it.* Pruning is
what a per-OS directory does (`reference/modules.md § Platform selection`) --
`__macos__/termios.c` and `__linux__/termios.c` are compiled where there is a terminal to
configure and are absent everywhere else, so a freestanding build never sees a `#include
<termios.h>` it could not answer. The guarantee the paragraph was protecting is intact and the
convenience is no longer traded away for it.

What that buys, in order of how much it matters:

- **No shell spawn.** `system` forked a shell twice a session to set two flags.
- **`cooked` restores what was actually there.** The shell version had to name flags to put back
-- `icanon echo isig` -- which restores a *different* terminal from the one it found if
anything else had been changed. The shim saves the whole `struct termios` on the way in.
- **It works when input is not the shell's.** `stty` acted on its own standard input, inherited
through `system`; the shim is handed a descriptor.

## What it actually sets: `-icanon -echo -isig`, and what it deliberately leaves alone

**Output translation is asserted rather than assumed**, which is the one thing full raw mode takes
that is worth keeping. `opost onlcr` says `\n` becomes `\r\n` on the way out, so every `print` in
the program goes on working; without it a hosted program's output walks diagonally down the screen,
each line starting where the last one ended, and the fix for *that* is auditing every write in the
program rather than this one call.

**Naming them is not redundant, and this cost a session.** Nothing here turns output processing
off, so leaving the two out looked safe — and it is, on a terminal that still has them. A terminal
does **not** always still have them: a program killed before it could restore, a `stty` run
earlier, an emulator's own defaults. Under a fresh pty the omission is invisible, which is exactly
why the tests were green while a real terminal stair-stepped. A mode this sets should not depend on
the mode it found.

`sysl.term.edit` writes `\r\n` outright, which a board needs and which costs a redundant carriage
return here — the cursor is moved to a column it is already in. That is also why the editor's own
output looked right while everything printed around it did not.

**Signals go, and that is not the obvious choice.** Leaving `isig` alone would keep Ctrl-C
interrupting, which reads like a feature for a REPL. It cannot be had: a program interrupted in
cbreak mode must restore the terminal from a signal handler, and restoring means allocating a
command string and forking a shell, neither of which is async-signal-safe. Tried, and it did not
merely risk a deadlock — it hung every time.

So `-isig`, and Ctrl-C arrives as **byte 3** for `sysl.term.edit` to decide about. What that costs
is real and should be said plainly: a program that has stopped responding can no longer be
interrupted from its own terminal, and the escape is `kill` from another one. What it buys is that
the terminal is never left broken, and that **a Mac behaves exactly like the board** — which has no
signals to disable in the first place.

## Index

[`color`](#color) [`color_err`](#color_err) [`color_on`](#color_on) [`color_wanted`](#color_wanted) [`cooked`](#cooked) [`flush`](#flush) [`is_tty`](#is_tty) [`raw`](#raw) [`tty_writer`](#tty_writer) [`TtyWriter`](#ttywriter) [Fallible for TtyWriter](#fallible-for-ttywriter) [Writer for TtyWriter](#writer-for-ttywriter)

## Functions

### `color`

```sysl
color() -> bool
```

Standard output, which is where a program that prints its results writes them.

### `color_err`

```sysl
color_err() -> bool
```

Standard error, which is where a program that prints diagnostics writes those -- and the two
genuinely differ, since `prog > file` leaves one a file and the other a terminal.

### `color_on`

```sysl
color_on(fd: int) -> bool
```

All three questions, about whichever descriptor is asked about.

### `color_wanted`

```sysl
color_wanted() -> bool
```

The environment's half of the answer: whether colour is wanted here at all, said without reference
to any descriptor.

It is public rather than folded into `color_on` because the two halves are genuinely separate
questions and a program can want this one alone -- a `--color=always` flag overrides the
descriptor and not the user's `NO_COLOR`, which is exactly this function. It is also the half that
can be asked without a terminal, which is what makes it answerable in a test.

### `cooked`

```sysl
cooked()
```

Put back what `raw` changed, and only what it changed.

**Exactly what was there**, which is what the shim's saved `struct termios` holds. The shell
version could not do this: it named `icanon echo isig` to put back, which restores a *different*
terminal from the one it found if anything else had been changed in between -- and `stty sane`,
the other option, resets settings this program never chose and has no business having an opinion
about. Doing nothing when `raw` was never called or has already been undone is the same rule.

### `flush`

```sysl
flush()
```

Push out everything the C library is holding, on every stream.

**A caller needs this for its own prompt.** The editor's sink below flushes what the *editor*
writes; a prompt printed by the program before handing over is the program's own output, and
nothing has written a newline after it. `prints("ogol> ")` then `flush()` is the whole idiom.

### `is_tty`

```sysl
is_tty(fd: int) -> bool
```

Whether a descriptor is attached to a terminal.

This is the whole of what C answers, and it is separate from the colour question because a program
can want it on its own: a progress bar, a spinner, a prompt, and a full-screen redraw are all worth
suppressing when output is a pipe, and none of them is about colour.

### `raw`

```sysl
raw() -> bool
```

Put the terminal into cbreak mode: keystrokes arrive as they are typed and nothing is echoed.

**Answers whether it worked**, and a caller should look. It fails where there is no terminal to
change -- input redirected from a file or a pipe -- and that is not an error so much as a different
situation, in which `sysl.io.console_lines` is the facility that fits and no mode has to change.

if raw()
var ed = editor(&input, &output)
for line in ed do ...
cooked()

`min 1 time 0` is what makes a read block until at least one byte arrives instead of returning
immediately with none; without `icanon` the terminal has no other rule about when to answer, and a
reader spinning on empty reads is the shape that gets mistaken for a hung program.

Calling it twice is not an error and does nothing the second time.

### `tty_writer`

```sysl
tty_writer() -> *Writer
```

## Types

### `TtyWriter`

```sysl
struct TtyWriter
end TtyWriter
```

Standard output as a sink that **flushes what it is given**, which is what an editor must write
through on a hosted terminal.

It carries no state for the reason `Stdout` does not: a destination fixed at compile time keeps
none, so the pointer the sink is built on addresses nothing and `write` never looks at `self`.

A program on a board passes its own port's writer and needs none of this. That asymmetry is real
and is the only one in the whole facility: the board has no buffer to defeat.

## Implementations

### Fallible for TtyWriter

```sysl
impl Fallible for TtyWriter
```

### Writer for TtyWriter

```sysl
impl Writer for TtyWriter
```
