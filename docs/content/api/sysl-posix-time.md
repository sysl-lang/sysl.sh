---
title: sysl.posix.time
layout: api-module
headingShift: 0
slugStyle: github
module: sysl.posix.time
requires: "requires { posix }"
---

## Index

[`default_zoneinfo_root`](#default_zoneinfo_root) [`from_local`](#from_local) [`local`](#local) [`local_offset`](#local_offset) [`local_text`](#local_text) [`local_zone_data`](#local_zone_data) [`monotonic`](#monotonic) [`now`](#now) [`zone_data`](#zone_data) [`zone_data_in`](#zone_data_in) [`zoneinfo_root`](#zoneinfo_root)

## Constants

### `default_zoneinfo_root`

```sysl
const default_zoneinfo_root: string = "/usr/share/zoneinfo"
```

Where the operating system keeps the database when nothing says otherwise. Every system this
module compiles for uses this path -- macOS symlinks it at `/var/db/timezone/zoneinfo` and answers
here too.

## Functions

### `from_local`

```sysl
from_local(ldt: LocalDateTime) -> Resolution
```

A wall clock reading in the host's zone, resolved to the instant or instants it names -- or to
neither, where the clocks skipped it.

### `local`

```sysl
local(t: Instant) -> LocalDateTime
```

An instant as the host's own clocks were showing it.

### `local_offset`

```sysl
local_offset(t: Instant) -> Offset
```

How far the host's clocks were set from UTC at that instant.

It takes an instant rather than answering "the current offset" because those are different
questions wherever a zone moves its clocks, and the one a caller almost always wants is this one:
the offset that applied to *the timestamp being rendered*, not the one that applies now.

### `local_text`

```sysl
local_text(t: Instant) -> string
```

An instant as a timestamp in the host's zone: `2026-08-22T13:26:04-04:00`.

The offset is written out rather than an abbreviation like `EDT`, and that is RFC 3339's choice
rather than a shortcut: an abbreviation is ambiguous across zones -- `CST` is three different
offsets -- so a timestamp carrying one cannot be read back reliably, and `parse_timestamp` reads
what this writes.

### `local_zone_data`

```sysl
local_zone_data() -> Result[[]u8, IoError]
```

The bytes of the zone **this host is set to**, which is what `/etc/localtime` is: a copy of, or a
symlink to, one file of the database.

It answers what `local_offset` answers and more -- the whole table rather than one instant's
offset, so a program can ask this zone for an abbreviation, or hand it to `resolve` without a
capability crossing the call. What it cannot answer is the zone's **name**: the file does not carry
one, and where `/etc/localtime` is a symlink the name is the link's target rather than anything
inside it.

### `monotonic`

```sysl
monotonic() -> Duration
```

A monotonic counter, as a length of time from an origin this deliberately does not name.

One reading answers nothing. **Two readings subtracted answer how long something took**, and that
is the only question it can be asked:

val t0 = monotonic()
work()
print(s"took ${(monotonic() - t0).us}us")

It never goes backwards and nothing can set it, which is the difference from `now` and the reason
a measurement wants this one.

### `now`

```sysl
now() -> Instant
```

The wall clock: where the host thinks it is on the timeline.

**Two readings of this may differ by anything at all**, including a negative amount, because it is
the clock a person and an `ntpd` are both allowed to set. Use it to stamp something, and use
`monotonic` to measure something.

### `zone_data`

```sysl
zone_data(name: string) -> Result[[]u8, IoError]
```

The bytes of a named zone -- `"America/Toronto"`, `"Europe/Paris"`, `"UTC"`.

The name is a path under the database root, which is why a region-qualified one has a `/` in it and
why nothing here parses it: the database's own layout is the naming scheme.

### `zone_data_in`

```sysl
zone_data_in(root: string, name: string) -> Result[[]u8, IoError]
```

The same, from a database somewhere else -- a copy shipped with an application, a mount, or a
directory a test wrote.

Through `sysl.path.join` rather than a `+ "/" +`, which is the one case a hand-rolled join gets
wrong: a root written with a trailing separator would otherwise put two in the middle, and a name
that is already absolute would be appended to rather than replacing what is in front of it.

### `zoneinfo_root`

```sysl
zoneinfo_root() -> string
```

The database root this host is using: `TZDIR` where it is set, and the path above where it is not.

**`TZDIR` is the convention every other reader of this database follows**, which is what makes it
worth consulting rather than a nicety: it is how a distribution relocates the database, how a
container image carries its own copy, and how a test points at one it wrote. A reader that ignored
it would disagree with `date` and `zdump` on the same machine.
