---
title: sysl.time.tzif
layout: api-module
headingShift: 0
slugStyle: github
module: sysl.time.tzif
---

## Index

[`abbrev_at`](#abbrev_at) [`is_dst_at`](#is_dst_at) [`offset_at`](#offset_at) [`parse`](#parse) [`TzifError`](#tziferror) [`Zone`](#zone) [Display for TzifError](#display-for-tziferror)

## Functions

### `abbrev_at`

```sysl
abbrev_at(z: Zone, t: Instant) -> Result[string, TzifError]
```

The abbreviation the zone used at that instant -- `EST`, `EDT`, `+0545`.

**It is here because the file already carries it**, and it is the one thing a host's `tm_gmtoff`
route cannot answer without a second call. It is a display string and nothing else: `CST` names
three different offsets around the world, so a timestamp that has to be read back carries the
number instead, which is what `timestamp_text` writes.

### `is_dst_at`

```sysl
is_dst_at(z: Zone, t: Instant) -> Result[bool, TzifError]
```

Whether daylight saving was in force at that instant, as the file records it.

### `offset_at`

```sysl
offset_at(z: Zone, t: Instant) -> Result[Offset, TzifError]
```

How far this zone's clocks were set from UTC at that instant.

**This is the shape `sysl.time.resolve` takes**, which is the whole point of it: a zone read from a
file, a zone talking to a chip and a zone written out by hand are one thing to everything above.

### `parse`

```sysl
parse(data: []const u8) -> Result[Zone, TzifError]
```

Reads a zone out of the bytes of a TZif file.

**The `Zone` borrows `data`** -- see the note above about what that costs.

A version 2 or later file carries its table twice: once with 4-byte transition times for a reader
that predates the format's second version, and then again with 8-byte ones. The first is stepped
over rather than read, which is why the length of it has to be worked out even though nothing in
it is wanted.

## Types

### `TzifError`

```sysl
enum TzifError
    BadMagic
    BadVersion(saw: u8)
    Truncated(needed: usize)
    NoTypes
```

Why a decode refused, at the granularity a caller can act on.

The shape is `sysl.encoding.DecodeError`'s and for its reason: the variants are separated by what a
caller would **do** about each. `BadMagic` is a file that is not one of these at all -- the usual
cause being a path that named something else. `Truncated` carries the length that would have been
enough, so a caller reading in pieces knows how much more to get rather than doubling until it
fits.

### `Zone`

```sysl
struct Zone
    data: []const u8
    trans_at: usize
    types_at: usize
    ttinfo_at: usize
    desig_at: usize
    timecnt: usize
    typecnt: usize
    wide: bool
```

A zone, as a view of the bytes it was decoded from.

## Implementations

### Display for TzifError

```sysl
impl Display for TzifError
```
