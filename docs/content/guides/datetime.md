---
title: datetime
summary: A conversion that can succeed twice — wall clocks, timelines, daylight saving, and the operator whose result no row can name.
weight: 100
---

Instants, durations, a calendar, and zones with daylight-saving rules — the whole point being the
conversion between a wall clock and a timeline, which can fail *and* can succeed twice.

**The axis: a conversion with three answers.** A local date-time is a reading on a wall clock. It is
not a point on the timeline and does not become one until somebody says where the wall is. On the
night a clock goes back, one reading names two instants; on the night it goes forward, one reading
names none. A library that returns a single answer there is wrong twice a year.

The reference values were computed against the real tz database with Python's `zoneinfo`, and that was
worth doing for its own sake: Python subtracts two aware date-times in one zone by **wall clock**, so
the first attempt at the reference values reported the week across the spring change as 168 hours. The
confusion this program is about is not a hypothetical one.

## What it found

**The difference of two moments could not be spelled `-` — answered.** Every operator row was
`op(self, rhs: Rhs) -> Self`, so an implementation said what it *took* and never what it *produced*.
`Instant + Duration`, `Instant - Duration` and `Duration ± Duration` all wrote themselves;
`Instant - Instant -> Duration` was refused, because `Sub`'s result was fixed to the type on the
left. Three quarters of an algebra was spellable, and the quarter that was not was the operation the
library exists to provide.

An operator trait carries its result as `Out` now, as well as its operand as `Rhs` — the same change
[matrix](/guides/matrix/) was written for. A vector space needs `Vector * Vector -> real` and a
timeline needs `Instant - Instant -> Duration`; they are one feature asked for by two problems.
`sysl.time` writes both rows of `Sub` on `Instant`, told apart by the type of the right operand and
nothing else:

```sysl
import sysl.time.*

var t = Instant(1000000i64)
var later = t + hours(3i64)

print(whole_hours(later - t))
print(whole_hours(since(later, t)))
```

`since` stays as the named spelling, because `later - earlier` is right and `earlier - later` is just
as easy to write. The finding moved into the library with the types and was answered there, which is
the part worth keeping: a gap that becomes a shipped module's gap is fixed once for everybody instead
of worked around once per program that wants a date.

**A derived scalar and a one-field struct are exactly complementary, and neither is what a quantity
wants.** `type Instant = new i64` inherits its base's whole catalogue for free — `==`, `<`, `+`, `str`
— and [may replace none of it](/reference/errors/), so it arrives with `Instant + Instant`, which is
nonsense, and cannot be given `Instant + Duration`, which is not. A struct of one field gets the
algebra exactly right and arrives with **nothing**: five `impl` blocks per type before anything can be
compared or printed. So a quantity type chooses between the catalogue and the meaning, and this
program pays the struct's price on every one of its six types.

**The three-way answer needed nothing new**, and that is a result rather than an absence. A data enum
already spells "one answer, or none, or two, and here they are"; the resolution type is an ordinary
enum and [coverage checking](/reference/patterns/) is what makes a caller handle all three. The axis
the problem was chosen for turns out to be covered by machinery that was already there — which is the
useful thing to know before designing a library around it.

**An enum cannot render its own variant names — and the three tables here turn out to be three
different asks.** A simple enum's name answers `T::Image(v)` with the word the variant is spelled
with, so the name-only case never needed a table: the library's `weekday_name` is that case, and is
one attribute read.

Neither of this program's own two is that case, and saying why is worth more than the original
complaint. The zone enum maps `NewYork` to `"America/New_York"` — a tz identifier, which is a fact
about the database and not about the declaration, so nothing but a table can hold it. The resolution
type is a **data** enum, where `Image` is refused in as many words, because a value there is a
variant plus a payload and a name answers for half of it — and its renderer builds a sentence out of
that payload anyway.

What is left of the finding is the narrow thing: `str` on an enum is still refused, so a value that
is nothing but its name is printed by asking for the name rather than by rendering the value.

**A `val` could not be sliced**, third program to report that, and the report was acted on: a view of
read-only storage is a [`[]const T`](/reference/arrays/), which carries the property rather than losing
it.

**A built-in is on nobody's left.** `Duration * 2` is writable and `2 * Duration` is not, so the
scaling in a date-time library only ever reads one way round.

## Worth noticing

Both quantities are counts of **microseconds**, and the representation argument is written out in the
source rather than assumed. Packing civil fields to the nanosecond needs 30 bits for the nanosecond,
17 for the time of day and 9 for day and month — 56, leaving 8 bits of year, which is a range of ±128
years and not a calendar. A *count* fits more comfortably at the same width and costs nothing to
compute with, because comparing, subtracting and adding a length are one instruction each on a count
and an unpack-recompute-repack on packed fields.

---

[Source](https://github.com/sysl-lang/sysl/tree/dev/guide/datetime) ·
Next: [matrix](/guides/matrix/) — an operator whose result is neither operand's type.
