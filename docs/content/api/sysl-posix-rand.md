---
title: sysl.posix.rand
layout: api-module
headingShift: 0
slugStyle: github
module: sysl.posix.rand
requires: "no alloc, requires { posix }"
---

## Index

[`entropy_from_os`](#entropy_from_os) [`seed_from_os`](#seed_from_os)

## Functions

### `entropy_from_os`

```sysl
entropy_from_os(into: []u8) -> bool
```

`into` filled from the kernel's entropy pool, or `false` where the host would not supply it.

**This is the source key material comes from**, unlike `seed_from_os` above it, which feeds a
generator that is not one. A salt, a nonce, an IV, a session id and a token all want these bytes
and want nothing computed from them.

**A slice longer than 256 bytes is filled by looping**, because that is `getentropy(2)`'s limit
per call and not a limit on what a caller may ask for. Refusing a longer slice was the other road
and is worse: every real caller wants 16 or 32, so the cap would be a rule nobody meets and
everybody has to read about, and the loop is three lines.

**An empty slice is filled successfully**, which is what a loop over no bytes means. It is worth
saying because the alternative -- asking the kernel for zero bytes -- is a call some libcs refuse.

Answers a `bool` rather than trapping, for `seed_from_os`'s reason turned the other way round: a
caller who cannot get entropy has no reasonable fallback for key material and must **stop**, and a
value it has to look at is what makes that a decision rather than an omission.

### `seed_from_os`

```sysl
seed_from_os() -> Option[u64]
```

Eight bytes of host entropy as a seed, or `None` where the host would not supply them.

**Answers an `Option` rather than trapping.** A program that cannot get entropy usually has a
reasonable fallback -- a fixed seed, and a line in its log saying so -- and a library that aborted
would take that choice away.
