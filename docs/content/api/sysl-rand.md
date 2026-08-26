---
title: sysl.rand
layout: api-module
headingShift: 0
slugStyle: github
module: sysl.rand
requires: "no alloc"
---

## Index

[`rng`](#rng) [`Rng`](#rng-1)

## Functions

### `rng`

```sysl
rng(seed: u64, seq: u64) -> Rng
```

A generator from a seed and a stream selector.

**Two arguments, because the stream is not the seed.** Two generators with the same seed and
different `seq` produce different, equally good sequences -- which is what a program wanting
several independent streams from one seed needs, and what seeding several generators from
consecutive seeds does *not* reliably give.

The initialization is the reference implementation's: the increment is made odd, the state is
advanced once, the seed is added, and the state is advanced again. The two steps are what stop a
seed of zero from being a special case.

## Types

### `Rng`

```sysl
struct Rng
    state: u64
    inc: u64
```

A generator: sixty-four bits of state, and the odd increment that selects one of 2^63 distinct
streams.

A copy of an `Rng` is an independent generator continuing from the same point, which is a useful
thing rather than an accident -- it is how a simulation replays a branch from a saved position.

| Member | Signature | Description |
|---|---|---|
| `next_u32` | `next_u32(*self) -> u32` | The next 32 bits. |
| `next_u64` | `next_u64(*self) -> u64` | Sixty-four bits, as two draws with the first taken as the high half. |
| `boolean` | `boolean(*self) -> bool` | One bit, taken from the **top** of a draw rather than the bottom. |
| `below` | `below(*self, n: u64) -> u64` | A uniform value in `[0, n)`, with **no modulo bias**. |
| `range` | `range(*self, lo: i64, hi: i64) -> i64` | A uniform value in `[lo, hi)`, empty when `hi` is not above `lo`. |
| `unit` | `unit(*self) -> real` | A uniform value in `[0, 1)`, from **53** bits. |
| `shuffle` | `shuffle[T](*self, xs: []T)` | The elements rearranged, every permutation equally likely. |
