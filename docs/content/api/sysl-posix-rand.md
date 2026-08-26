---
title: sysl.posix.rand
layout: api-module
headingShift: 0
slugStyle: github
module: sysl.posix.rand
requires: "no alloc, requires { posix }"
---

## Index

[`seed_from_os`](#seed_from_os)

## Functions

### `seed_from_os`

```sysl
seed_from_os() -> Option[u64]
```

Eight bytes of host entropy as a seed, or `None` where the host would not supply them.

**Answers an `Option` rather than trapping.** A program that cannot get entropy usually has a
reasonable fallback -- a fixed seed, and a line in its log saying so -- and a library that aborted
would take that choice away.
