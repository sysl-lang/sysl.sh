---
title: sysl.term.edit
layout: api-module
headingShift: 0
slugStyle: github
module: sysl.term.edit
---

## Index

[`editor`](#editor) [`Editor`](#editor-1) [Iterate for Editor](#iterate-for-editor)

## Functions

### `editor`

```sysl
editor(r: *Reader, w: *Writer) -> Editor
```

An editor over a byte source and a place to echo to.

The two are separate parameters because on a host they are separate things -- a reader on one
descriptor and a writer on another. A caller whose console is one value that is both hands it over
twice.

**It prints no prompt and is not told one.** Every movement here is relative, so the editor never
needs to know how far along the row the line starts, and a caller goes on printing its own prompt
exactly as it would before `console_lines`. That is what makes a continuation prompt -- a REPL
holding an unfinished expression -- a property of the caller rather than a field here.
**The history belongs to the editor and so outlives a line but not a session**, which is the right
lifetime for it: one console, one history, and nothing to save or load. A program wanting it to
survive a restart would have to write it somewhere, and where is a question about that program.

## Types

### `Editor`

```sysl
struct Editor
    src: *Reader
    sink: *Writer
    line: Buf[char]
    at: usize
    chunk: []u8
    have: []const u8
    pos: usize
    owed_lf: bool
    done: bool
    hist: Deque[string]
    browse: usize
    draft: string
```

A line editor over a byte source and a place to echo to.

**It borrows both rather than owning them**, for the reason `sysl.io.Lines` gives: a `for` loop
iterates a *copy* of its iterator, so an owned reader would latch its failure on a copy the caller
cannot reach, and `failed` would be decorative. Two parameters rather than one value implementing
both traits, because a source and a sink are usually two different things -- a file descriptor each
on a host -- and a caller with one value that is both hands it over twice.

**Three pieces of state outlive a line, which is why this is a struct rather than a function.** The
CRLF debt straddles two lines by construction; the tail of the last read holds bytes that have been
taken from the reader and not yet looked at, which an escape sequence arriving in two pieces
depends on; and the `Buf[char]` is reused, so a session allocates once rather than once per line.

| Member | Signature | Description |
|---|---|---|
| `next_byte` | `next_byte(*self) -> Option[u8]` | The next byte of input, refilling from the reader when what was read is spent. |
| `char_from` | `char_from(*self, b0: u8) -> char` | The character a byte begins, read the rest of the way off the source. |
| `getline` | `getline(*self) -> Option[string]` | One line, without its terminator, echoed as it is typed. |
| `finish` | `finish(*self) -> string` | The characters gathered back into a `string`, which is one `StrBuilder` and no validation — **and the one place a finished line enters the history**, so that every way of ending a line records it and no future way can forget to. |
| `edit` | `edit(*self, b: u8)` | One keystroke's worth of editing. |
| `escape` | `escape(*self)` | An escape sequence -- **both of the two forms a terminal sends for the same key**. |
| `numbered` | `numbered(*self, first: u8)` | A parameterised sequence -- `ESC [ 3 ~` for Delete, and the numbered spellings of Home and End that some terminals send instead of the lettered ones. |
| `final_key` | `final_key(*self, c: u8)` | The last byte of a sequence, where the key is when it is not a number. |
| `put` | `put(*self, bytes: []const u8)` |  |
| `put_char` | `put_char(*self, c: char)` |  |
| `back` | `back(*self, n: usize)` | `n` columns back, which is `n` backspaces. |
| `blank` | `blank(*self, n: usize)` | `n` columns painted over. |
| `new_line` | `new_line(*self)` | CRLF, so that whatever the program prints next starts on a line of its own. |
| `width_at` | `width_at(self, i: usize) -> usize` | How wide the character at `i` is drawn. |
| `columns_from` | `columns_from(self, from: usize) -> usize` | How many columns the line occupies from `from` to its end. |
| `put_from` | `put_from(*self, from: usize)` | The line from `from` to its end, echoed where the cursor stands. |
| `left` | `left(*self)` |  |
| `right` | `right(*self)` |  |
| `to_start` | `to_start(*self)` |  |
| `to_end` | `to_end(*self)` |  |
| `redraw` | `redraw(*self, blanks: usize)` | ## Changing After an edit at the cursor, everything to its right has moved, so the tail is written out again and the cursor walked back to where it belongs. |
| `insert` | `insert(*self, c: char)` |  |
| `backspace` | `backspace(*self)` |  |
| `delete_at` | `delete_at(*self)` |  |
| `kill_line` | `kill_line(*self)` | Ctrl-U -- everything gone. |
| `kill_to_end` | `kill_to_end(*self)` | Ctrl-K -- everything from the cursor on. |
| `replace` | `replace(*self, text: string)` | The whole line replaced by `text`, redrawn where it stands. |
| `text_of` | `text_of(self) -> string` | The line as text, for putting into the history or keeping as the draft. |
| `earlier` | `earlier(*self)` | Up: one step towards the oldest. |
| `later` | `later(*self)` | Down: one step towards the line being typed, and then to the draft itself. |
| `remember` | `remember(*self, line: string)` | What a finished line leaves behind. |

## Implementations

### Iterate for Editor

```sysl
impl Iterate for Editor
```
