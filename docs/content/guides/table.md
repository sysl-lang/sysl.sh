---
title: table
summary: Text measured for display — a column is as wide on screen as its widest cell, and both a byte count and a character count are the wrong unit.
weight: 150
---

Cells of any type that renders, laid out in columns that line up. A column is a **promise about where
the next border falls**, so the program has to know how wide text *looks* — and that is a different
question from how many bytes it is and from how many characters it is.

**The axis: text measured for display.** Every other program in the set treats a string as bytes to
be copied or compared. Nothing else asks how wide one appears, and the moment something does, two
plausible answers turn out to be wrong.

Deliberately not modelled: multiple border styles, Markdown and TSV output, row spanning, and
wrapping a cell wider than its column. Each is more of the same layout, and none of them asks the
language anything the program does not already ask.

## What it found

**A `FormatSpec`'s width is a byte count, so it cannot lay out a column.** The specifier's width and
precision count bytes, as C's do, and `display_pad` pads to that. A table is the program that minds:
`café` is five bytes and four columns, so a field padded to a byte count is short by one — and short
by **one per non-ASCII character**, so two cells of one column come out wrong by different amounts
and the column is ragged rather than merely narrow. There is no correction to apply afterwards.

So the program ignores the specifier's width entirely, hands every cell the neutral spec, and does
its own padding over a count it asks the library for. That division is right — only the caller knows
where the next border falls — but it means the one field a table would have used is the one it
cannot use, and a program that trusted it would produce a table that is wrong **only for text that is
not ASCII**, which is the worst way to be wrong.

Settled in the [core traits](/reference/traits/): the specifier keeps counting bytes and keeps its
equivalence with `snprintf`, because a specifier is for `printf`-shaped output. Laying anything out
asks [`columns`](/library/text/) instead.

**A character is not a column either.** The first version of this program counted characters, which
is right for `café` and `Zürich` and wrong for `日本` — two characters occupying four terminal
columns. It was recorded as a boundary rather than solved, on the grounds that the East Asian Width
table from UAX #11 is *data* rather than language and had nowhere to live.

**That reason was already false when it was written, and that is the more useful half of the
finding.** Module-level `const` and `val` had landed the day before, and four other programs in the
set were already using them — [datetime](/guides/datetime/) carries a transition table built by a
function at module level, which is the exact shape a width table needs. An absence is worth checking
against the language as it is *today* rather than as it was when the habit formed. This one cost a
paragraph explaining why something could not be done that could.

`columns` now lives in [`sysl.text`](/library/text/), backed by the Unicode Character Database, and
the program measures `日本` and a decomposed `café` as correctly as it measures ASCII. **What the
program keeps is the call, not the table.**

**Ask the library before reporting an absence** — the same correction [png](/guides/png/) recorded.
This program was most of the way to writing up "a rendered value cannot become a `string`": the sink
answers a `[]u8`, and two earlier programs had wanted `from_utf8` and not had it. It was already
there. What that would have cost was not just a false note — the checks were written twice, once
comparing bytes by hand, before the conversion was looked for.

## What the assertion is

The program checks the property that actually matters, which a byte count would have passed:

```sysl
even(text: []const u8) -> bool
    val want = columns(line(text, 0usize))
    var i = 1usize

    while i < usize(lines(text))
        if columns(line(text, i)) != want then return false

        i += 1usize

    true
end even
```

Every line the same width **on screen** is the whole of what it means for a table to line up. A
program that measured wrongly would still pass a byte-equality check and produce a table nobody could
read, so the assertion is written in the same unit the renderer is.
