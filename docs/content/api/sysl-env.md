---
title: sysl.env
layout: api-module
headingShift: 0
slugStyle: github
module: sysl.env
requires: "requires { os }"
---

## Index

[`get`](#get) [`get_or`](#get_or) [`is_set`](#is_set)

## Functions

### `get`

```sysl
get(name: string) -> Option[string]
```

What a name is set to, or `None` where it is unset.

**A value that is not UTF-8 answers `None` as well**, which folds a third case into the second and
is the honest thing for a function whose result is a `string`: the bytes are somebody's
environment rather than the program's input, so there is no encoding to negotiate and nothing
useful to report. A caller that must tell "unset" from "not text" asks `is_set` too.

### `get_or`

```sysl
get_or(name: string, fallback: string) -> string
```

What a name is set to, or a value of the caller's where it is not.

This is the shape most callers actually want -- a default that is a real value rather than a case
to match -- and it is the one `sysl.posix.time` uses to let `TZDIR` redirect a lookup.

### `is_set`

```sysl
is_set(name: string) -> bool
```

Whether a name is present at all, whatever it holds.

**Set-to-empty answers `true` here and `Some("")` from `get`**, which is what makes the two
functions worth having separately: `VAR=` is a name that is set, and a convention reading presence
rather than content is entitled to say so.
