---
title: The rand module
summary: "`sysl.rand` — PCG32, seeded by the caller and reproducible; a bounded integer with no modulo bias, a shuffle that is Fisher-Yates, and OS seeding kept in a module of its own so a freestanding target need never import it."
weight: 62
---

`sysl.rand` is a named, seedable, reproducible pseudo-random generator and the distributions that are
easy to get wrong.

**This is not a source of unpredictability, and nothing here should be used as one.** The generator is
completely determined by its seed — which is exactly what a test that must reproduce, a simulation
that must replay, and a shuffle all want, and exactly what a key, a token, a nonce or a password reset
must not have. Anyone who can see a handful of outputs can compute the state and every output that
follows. A cryptographic generator is a different thing with a different implementation, and it is not
in this module under any name.

```sysl
import sysl.rand.rng

var g = rng(42, 54)

print(g.below(6), g.below(6), g.below(6))
print(g.boolean())
```

```output
3 3 2
true
```

Those numbers are not illustrative — they are what that seed produces, every run, on every machine.
That is the whole point of the module.

## The algorithm is named so it can be checked

It is **PCG32** — PCG-XSH-RR with a 64-bit state and 32-bit output, from M. E. O'Neill's *PCG: A
Family of Simple Fast Space-Efficient Statistically Good Algorithms for Random Number Generation*
(2014). The library's own tests pin it against the reference implementation's first outputs, because
a generator nobody can identify is one nobody can verify.

The state advances by an ordinary linear congruential step, and the *output* is a permutation of the
state's high bits rather than the state itself. That second step is what a bare congruential generator
lacks, and it is why its low bits are notoriously poor — here every bit of the output is a product of
the whole state.

## Seeding is the caller's, and that is what keeps it portable

`rng(seed, seq)` takes two arguments because **the stream is not the seed**. Two generators with the
same seed and different `seq` produce different, equally good sequences — which is what a program
wanting several independent streams from one seed needs, and what seeding from consecutive seeds does
*not* reliably give.

```sysl
import sysl.rand.rng

var a = rng(7, 1)
var b = rng(7, 1)
var c = rng(7, 2)

print(a.next_u32() == b.next_u32())
print(a.next_u32() == c.next_u32())
```

```output
true
false
```

Nothing in `sysl.rand` reads a clock or asks the operating system for anything, so it compiles and
runs on a freestanding target.

## Taking a seed from the host — `sysl.rand.sys`

A program that wants a different sequence each run needs entropy, and that needs an operating system.
It is a **module of its own** so that importing the generator cannot drag one in behind it — the same
split [`sysl.term`](/library/term/) and `sysl.term.tty` already make.

```sysl
import sysl.rand.rng
import sysl.rand.sys.seed_from_os

var g = rng(seed_from_os().unwrap_or(0), 1)

print(g.below(100) < 100)
```

```output
true
```

It answers an `Option` rather than trapping: a program that cannot get entropy usually has a
reasonable fallback — a fixed seed and a line in its log saying so — and a library that aborted would
take that choice away. It requires `posix`.

## `below` is where this module earns its keep

The obvious `next_u64() % n` is **not uniform** whenever `n` does not divide 2⁶⁴: the low residues
come up more often, by a margin that is invisible in a handful of draws and is a real defect in a
simulation or a shuffle. `below` rejects the unfair tail of the range instead, which is exact,
terminates with probability one, and expects under two draws.

```sysl
import sysl.rand.rng

var g = rng(99, 3)
var counts: []int = [0; 6]

for i in 0..<6000
    counts[usize(g.below(6))] += 1

print(counts[0] > 850 && counts[0] < 1150)
print(counts[5] > 850 && counts[5] < 1150)
```

```output
true
true
```

`range(lo, hi)` is the same thing over a signed interval, half-open at the top. `unit()` answers a
`real` in `[0, 1)` from **53** bits — the width of a `real`'s mantissa, so no representable value is
unreachable and none is thrown away — and never returns `1.0`, which is the property every caller
scaling by a range depends on.

## Shuffling

```sysl
import sysl.rand.rng

var g = rng(2024, 1)
var xs = [1, 2, 3, 4, 5]

g.shuffle(xs[..])

print(xs.len)
```

```output
5
```

Fisher-Yates, walking down: each position from the end takes a uniformly chosen element from those at
or below it. The whole difficulty is drawing that index without bias, which is `below`'s job — the
loop itself is three lines.

It lives here rather than in [`sysl.slices`](/library/slices/) because it is a fact about a
*generator*, and because `sysl.slices` is the module a C binding reaches into: acquiring a
random-number generator by asking for a slice operation would be a poor trade.
