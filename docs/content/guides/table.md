---
title: table
summary: Text measured for display — a column is as wide as its widest cell on screen, and both a byte count and a character count are the wrong unit.
weight: 150
---

A text table: cells of any type that renders, laid out in columns that line up. Borders, three
alignments, a header row. It is not here to be a table library. It is here because a table is the
program that cannot get away with measuring text the way every other program in the set measures it.

**The axis: text measured for display.** Every other guide program treats a string as bytes to be
copied or compared. This one has to know how wide one *looks*, because a column is a promise about
where the next border falls — and being wrong by one is a border that does not line up. Nothing else
in the set asks.

Deliberately not modelled: multiple border styles, Markdown and TSV output, row spanning, and
wrapping a cell wider than its column. Each is more of the same layout; none of them asks the
language anything the program does not already ask.

## Three measurements, and two of them are wrong here

```sysl
import sysl.text.*

var words = ["cafe", "café", "日本", "naïveté"]

for s in words
    print(s.len, s.chars.count(), columns(s.bytes))
```

```output
4 4 4
5 4 4
6 2 4
9 7 7
```

`café` is the first row that separates them, and `日本` is the row that shows the two wrong answers
err in **opposite directions**: six bytes and two characters, and four columns, which is neither. A
byte count is over; a character count is under. So neither is a correction of the other, and a
program that picked one and adjusted afterwards would still be wrong.

The error is not even a constant. It is one column per accented character, so two cells in one
column are over by different amounts:

```sysl
import sysl.text.*

print("naïveté".bytes.len - columns("naïveté".bytes))
print("café".bytes.len - columns("café".bytes))
```

```output
2
1
```

**A field padded by bytes is therefore ragged rather than merely narrow** — which is the worst way to
be wrong, because it is right for ASCII and a table of English text would ship looking correct.

## What that cost the language

**A `FormatSpec` width could not answer it.** The [specifier's](/library/core/) width counts bytes, as
C's does, and `display_pad` pads to that. This program is the one that minds, so it ignores the
specifier's width entirely, hands every cell the neutral spec, and does its own padding over a count
it asks [`sysl.text`](/library/text/) for.

That is the right division — only the caller knows where the next border falls — but it means the one
field in `FormatSpec` a table would have used is the one it cannot use. Decided and left as it is:
the specifier keeps counting bytes and keeps its equivalence with `snprintf`, because a specifier is
for `printf`-shaped output. Laying anything out asks `columns` instead.

**A character is not a column either — found here, and fixed in the library.** The first version of
this program counted characters, which is right for `café` and `Zürich` and wrong for `日本`. It was
recorded as a boundary rather than solved, on the grounds that the East Asian Width table from
[UAX #11](https://www.unicode.org/reports/tr11/) is data rather than language and had nowhere to
live.

**That reason was already false when it was written, and that is the more useful half of the
finding.** Module-level `const` and `val` had landed the day before, and other programs in the set
were already using them — [datetime](/guides/datetime/) carries a transition table built by a
function at module level, which is the exact shape a width table needs. An absence is worth checking
against the language as it is *today* rather than as it was when the habit formed; this one cost a
paragraph explaining why something could not be done that could.

So `columns` is `sysl.text`'s now, backed by the Unicode Character Database, and the program measures
`日本` and a decomposed `café` as correctly as it measures ASCII. **What the program keeps is the
call, not the table.**

**A correction rather than a finding, and the same one [png](/guides/png/) recorded: ask the library
before reporting an absence.** This program was most of the way to writing up "a rendered value
cannot become a `string`" — `ByteSink.text()` answers a `[]u8`, and two earlier programs had wanted
`from_utf8` and not had it. It was there all along. What that would have cost was not just a false
note: the checks were written twice, once comparing bytes by hand and printing them back through
`putbytes`, before the conversion was looked for.

## The payoff: a heterogeneous row is ordinary code

```sysl
t.add(["totals", 7, 340282366920938463463374607431768211455u128, true])
```

An array literal of four unrelated types coercing to `[]const &Display`, each boxed and erased at the
element. Until every built-in reached `Display` through an `impl`, an integer could not be erased at
all — so a table of numbers, which is what a table is usually for, could not be spelled. **This
program is what asked for that, and it is now what shows it.**

A smaller positive beside it: a fixed array does *not* decay to a slice at a call, so a row built as
a binding is passed `row[..]` — but an array literal written **at** the argument coerces directly,
which is why the rows above read the way they do.

## How it is built

One `Cell` is the rendered bytes and their width, both kept because the rendering is what produces
them:

```sysl
struct Cell
    text: &Buf[u8]
    width: usize
end Cell
```

A table cannot lay out a column until it knows the widest cell in it, so it has to see every cell
before it writes the first. Rendering each value a second time at that point would mean holding the
*values* instead — the same storage with a dispatch on the end of it.

The parameter that makes a mixed row work is a `&Display` rather than a `T: Display`: a bound is a
promise about **one** type, and a row is a sequence of different ones.

```sysl
cell_of(v: &Display) -> Cell
    var sink = byte_sink()
    var out: *Writer = &sink

    v.display(out, FormatSpec(0, -1, false))

    Cell(sink.bytes, columns(sink.text()))
end cell_of
```

The cells are **one flat buffer** rather than a buffer of rows: a row is `ncols` consecutive entries,
so a column is a walk with a stride and neither shape needs a nested container.

## The one thing left open

**A `require` cannot carry its message onto the next line.** Continuation covers an operator that
cannot finish an expression, and brackets already join — but `require cond, "message"` has no
brackets, so its comma cannot carry the line and a long message has nowhere to go.

Decided and left as it is, and the reason is the rule rather than the effort: a comma **can** finish
an expression, since the message is optional, so continuing there would need the rule to consult
which construct it is inside — which is what the whole design avoids. What would fix it without
touching the rule is bracketing the operands, and that is a change to how a contract is spelled.

---

Next: back to [the fifteen](/guides/) — or to [`sysl.text`](/library/text/), which is where this
program's finding ended up.
