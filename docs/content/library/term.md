---
title: sysl.term
summary: The escape sequences a terminal understands — colour, emphasis, and the screen — as constants a program with no allocator can still name.
weight: 85
---

`sysl.term` is forty-odd `const string`s and nothing else. Each one is an ANSI escape sequence, and
writing one into the output stream is how a terminal is told to change colour, start underlining, or
clear itself.

```sysl
import sysl.term.*

print(f"${red}${bold}error${reset}: the file was not there")
```

A sequence is written **where the text it affects is written**, because that is what it is — a mark
in the stream rather than a property of a string. There is no coloured-string type here and nothing
to wrap: `red` is text that happens to be invisible, it concatenates like any other text, and
`reset` is how you stop.

## Why constants, and what that buys

A string literal is [immortal](/reference/strings/) — it lives in the program's own image with no
owner and no reference count — so naming forty of them costs nothing at run time and nothing in
storage. That is what lets this module declare `@no_alloc`, and it is the point of the whole design:
colouring a line is exactly what a program that has given up its allocator most wants to do, and a
facility such a program could not use would be no facility at all.

```sysl
@no_alloc
@no_os

import sysl.term.*

main()
    print(f"${green}ok${reset}")
```

The module requires no capability at all, so an interrupt handler can name a colour.

## The colours

Eight, each with a bright variant and a background form. The arithmetic between them is the
specification rather than a coincidence: **a background is its foreground plus ten, and a bright
colour is its ordinary one plus sixty.**

```sysl
import sysl.term.*

// An escape is invisible, so this reads the parameter back out of one.
code(s: string) -> int
    var n = 0

    for b in s.bytes
        if b >= u8('0') && b <= u8('9') then n = n * 10 + int(b - u8('0'))

    n

main()
    print(code(red), code(bright_red), code(on_red), code(on_bright_red))
```

```output
31 91 41 101
```

| foreground | bright | background | bright background |
|---|---|---|---|
| `black` | `bright_black` | `on_black` | `on_bright_black` |
| `red` | `bright_red` | `on_red` | `on_bright_red` |
| `green` | `bright_green` | `on_green` | `on_bright_green` |
| `yellow` | `bright_yellow` | `on_yellow` | `on_bright_yellow` |
| `blue` | `bright_blue` | `on_blue` | `on_bright_blue` |
| `magenta` | `bright_magenta` | `on_magenta` | `on_bright_magenta` |
| `cyan` | `bright_cyan` | `on_cyan` | `on_bright_cyan` |
| `white` | `bright_white` | `on_white` | `on_bright_white` |

`default_color` and `on_default` put each back to whatever the terminal was using.

## Emphasis, and why `reset` is not enough

| name | what it does |
|---|---|
| `bold` | heavier, or brighter on a terminal with no bold face |
| `dim` | fainter |
| `italic` | slanted, where the terminal has it |
| `underline` | underlined |
| `blink` | blinking, where the terminal allows it |
| `reverse` | foreground and background swapped |
| `hidden` | not shown, but still selectable |
| `strike` | struck through |
| `reset` | **all of the above, and the colours, off at once** |

ANSI has no way to end one attribute and leave the others — `reset` ends everything there is. So a
program that wants its colour back without losing an emphasis writes `default_color` rather than
`reset`, and one that has ended a colour inside an underlined field has to open the underline again
afterwards.

## The screen and the cursor

| name | what it does |
|---|---|
| `clear_screen` / `clear_line` | the whole screen, the whole line — neither moves the cursor |
| `clear_below` / `clear_to_line_end` | from the cursor onwards |
| `home` | the top left corner |
| `hide_cursor` / `show_cursor` | a program that hides it owns showing it again, including on the way out |
| `save_cursor` / `restore_cursor` | one remembered position, the terminal's own — these do not nest |

`clear_screen` is nearly always written with `home` after it, since clearing does not move anything.

## What is deliberately not here

**Anything that asks whether the terminal is a terminal.** Whether escapes should be emitted at all —
output redirected to a file, `NO_COLOR` set, a terminal that cannot do any of this — is a question
about the process and its environment, and answering it needs `os`. Putting the answer in this module
would take the module away from every program that only wanted to name a colour. So the decision
stays the caller's: ask once, keep the answer, and write the escape or the empty string.

```sysl
import sysl.term.*

paint(colour: bool, escape: string) -> string = if colour then escape else ""
```

**Anything that takes a number.** Moving the cursor to a row and column means building
`ESC [ row ; col H`, and building a string is an allocation — the one thing the module is arranged to
avoid. The sequences above are the ones whose text is fixed; a program that wants the others writes
`f"\u{1b}[${row};${col}H"` and knows what it is spending.
