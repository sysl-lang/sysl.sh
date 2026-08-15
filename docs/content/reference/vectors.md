---
title: Vectors
summary: `<N>T` is an array that computes lane-wise — one instruction for N additions, one kernel for every register width, and it still compiles where there is no vector unit at all.
weight: 77
---

`<N>T` holds N lanes of `T` — the same values an `[N]T` holds, in the same order — with one
difference: its operators work on every lane at once.

```sysl
val a: <4>f32 = [1.0, 2.0, 3.0, 4.0]
val b: <4>f32 = [10.0, 20.0, 30.0, 40.0]

val c = a + b

print(c[0], c[3])
```

```output
11 44
```

That `+` is one machine instruction computing four additions. The two type constructors differ by one
bracket pair because a vector **is** an array that computes lane-wise, and the spelling says so:
`[4]f32` is storage, `<4>f32` is a register.

**You never have to ask whether a machine can run this.** A vector wider than the hardware becomes
several registers, and one on a machine with no vector unit at all becomes ordinary scalar
operations — so `<4>f32` compiles for a Cortex-M0 exactly as it compiles for an Apple M-series, and
the only thing that changes is how fast it goes. The question a program asks is how *wide* to go
where it cares about speed, never whether it may write one.

## Writing one down

**A literal** fills the lanes, and it has to fill all of them: the lane count is part of the type, so
there is no equivalent of a slice dropping its length.

```sysl
val v: <4>int = [1, 2, 3, 4]

print(v[0], v[3], v.len)
```

```output
1 4 4
```

**A scalar broadcasts** — the *splat*, and the commonest thing any vector code does. Write the scalar
and it goes into every lane, at a binding, at an operator, at a return, anywhere a vector is wanted:

```sysl
val zero: <4>f32 = 0.0
val ones: <4>f32 = [1.0; 4]

half(v: <4>f32) -> <4>f32 = v * 0.5

print(zero[0], ones[3], half(ones)[0])
```

```output
0 1 0.5
```

**A declaration with no initializer** starts every lane at the lane type's zero.

**A lane is read by a constant index.** This is the one subscript in sysl that is not checked while
the program runs, and the reason is that there is nothing to check against: a vector has no address,
and an out-of-range lane read has no defined answer at the machine level rather than trapping. So the
index has to be a literal or a `const`, and it is checked where it is written:

```sysl
val v: <4>f32 = [1.0, 2.0, 3.0, 4.0]

print(v[4])
```

```error
<4>f32 has lanes 0 to 3, and this is 4
```

If you need a computed index, you want the values in an array — whose checked subscript already
answers.

## What the operators do

A vector has exactly the operators its **lane** has, applied to every lane. Both sides must be the
same vector, or one side a scalar that broadcasts:

| lanes | operators |
|---|---|
| an integer | `+` `-` `*` `&` <code>&#124;</code> `^` `<<` `>>`, and unary `-` `~` |
| a float | `+` `-` `*` `/`, and unary `-` |
| `bool` (a mask) | `&` <code>&#124;</code> `^` |

**Integer `/` and `%` are refused**, and it is worth knowing why rather than reading it as an
omission. Scalar integer division traps on a zero divisor and on the one signed overflow; a register
traps as a whole or not at all, so a vector would either drop the check or trap for lanes that were
perfectly fine. No processor sysl targets has an integer vector divide anyway, so the loop you write
instead is what the hardware was going to do:

```sysl
val a: <4>int = [10, 20, 30, 40]

f() -> unit
    val b = a / a
```

```error
'/' is not defined on <4>int
```

There is no promotion, exactly as there is none between scalars. Two different widths, or two
different lane types, are two different types:

```sysl
val a: <4>f32 = [1.0, 2.0, 3.0, 4.0]
val b: <8>f32 = [1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0]

f() -> unit
    val c = a + b
```

```error
'+' needs matching types, got <4>f32 and <8>f32
```

## Masks, and the lane-wise `if`

A comparison between vectors yields a **mask** — a `<N>bool`. It is an ordinary value: bind it, combine
it, reduce it.

```sysl
val a: <4>f32 = [1.0, 5.0, 3.0, 7.0]

val small = a < 4.0

print(small.any(), small.all())
```

```output
true false
```

**`select` is how a mask chooses**, and it is a method rather than a keyword because it cannot be
`if`. An `if` branches — it evaluates one side or the other — and a register has no way to take one
branch in two lanes and the other in the remaining two. So both sides are computed and the mask picks
between the results, lane by lane:

```sysl
val a: <4>f32 = [1.0, 5.0, 3.0, 7.0]
val b: <4>f32 = [4.0, 2.0, 6.0, 0.0]

val lo = (a < b).select(a, b)
val clamped = (a > 4.0).select(4.0, a)

print(lo[0], lo[1], clamped[1], clamped[3])
```

```output
1 2 4 4
```

Either side of a `select` may be a scalar, which broadcasts — `(v > hi).select(hi, v)` is what
clamping looks like and needs no construction written around the bound.

Two masks combine with the bitwise operators, and **not** with `&&`. That is not an oversight: `&&`
short-circuits, and there is no such thing as short-circuiting per lane.

```sysl
val a: <4>int = [1, 2, 3, 4]

val inside = (a > 1) & (a < 4)

print(inside.any(), inside.all())
```

```output
true false
```

For the same reason **a comparison chain has no lane-wise form**. `1 < a < 4` joins its two links with
`&&`, so it is refused rather than quietly turned into an `&` that would look like the scalar spelling
and mean something different:

```sysl
val a: <4>int = [1, 2, 3, 4]

f() -> unit
    val m = 1 < a < 4
```

```error
compare two vectors at a time and combine the masks with '&'
```

## Reductions

A reduction collapses a vector to one scalar. `sum`, `min` and `max` are a numeric vector's; `any` and
`all` are a mask's.

```sysl
dot(a: <4>f32, b: <4>f32) -> f32 = (a * b).sum()

val v: <4>f32 = [3.0, 1.0, 4.0, 1.5]
val w: <4>f32 = [1.0, 2.0, 3.0, 4.0]

print(v.sum(), v.min(), v.max(), dot(v, w))
```

```output
9.5 1 4 23
```

A float sum is computed as a **tree** rather than left to right, which is what makes it worth an
instruction instead of a loop — so it may differ in the last bit from adding the lanes yourself in
order. An integer sum wraps at the lane width, exactly as scalar integer arithmetic does.

## One kernel, every width

This is what the feature is for, and it needs nothing of its own: a lane count is a
[value parameter](/reference/generics/), so it binds from the argument the way an array's length does.

```sysl
scale[const W: usize](v: <W>f32, by: f32) -> <W>f32 = v * by

val four: <4>f32 = [1.0, 2.0, 3.0, 4.0]
val eight: <8>f32 = [1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0]

val a = scale(four, 2.0)
val b = scale(eight, 3.0)

print(a[3], b[7], a.len, b.len)
```

```output
8 24 4 8
```

Neither call writes a width. One body is compiled twice, and the two hold different instructions —
four lanes wide and eight. The mask inside a generic kernel follows the same width, so a comparison
in there is a `<W>bool` and the `select` chooses at whatever W turned out to be:

```sysl
clamp_low[const W: usize](v: <W>f32, lo: f32) -> <W>f32 = (v < lo).select(lo, v)

val four: <4>f32 = [1.0, -2.0, 3.0, -4.0]
val two: <2>f32 = [-1.0, 5.0]

print(clamp_low(four, 0.0)[1], clamp_low(two, 0.0)[0])
```

```output
0 0
```

For comparison, the C answer to the same problem is to write the kernel once per instruction set
behind `#ifdef` — Box2D's contact solver carries four copies of itself, and more than half of that
file is those copies.

## What a vector does not have yet

**Shuffles and swizzles.** There is no way to rearrange lanes in one step. Rearranging takes a
`select` against a mask per lane, which is correct everywhere and slower than the single instruction a
shuffle would be. This is also the reason the section above is a stronger claim about arithmetic than
about loading: a gather is what changes between hand-written SIMD variants, and it is the half that
does not yet generalise over a width.

**A way into and out of memory.** A vector cannot be converted to or from an array, loaded from a run
of a slice, or stored back into one. At a fixed width you can write the lanes out one at a time; in a
kernel generic over its width you cannot, because a lane index has to be a constant. So a width-generic
kernel can answer a vector or a reduction, and cannot yet fill an array of results.

**A place in a C signature.** A vector may not cross to a C function in either direction. Which
register it would arrive in differs by target and by which instruction-set extensions the other side
was compiled with, and guessing would produce a call that links and corrupts its arguments rather than
one that fails to link. Pass the lanes through memory — a `*f32` and a count, which is what C's own
SIMD-taking functions take:

```sysl
extern "process_lanes" process(v: <4>f32) -> unit
```

```error
how a vector reaches a C function differs by target
```

## Lanes

A lane is a scalar: an integer, a float, `bool` or `char`. An aggregate cannot be one — there is no
such thing as a vector of structs — and neither can a `volatile` type, since per-lane access ordering
is not something a single load can give. `volatile <4>u32` is the spelling that means something, and
it qualifies the whole register.

```sysl
struct Point
    x: int
    y: int

f() -> unit
    var v: <4>Point
```

```error
a vector's lanes are scalars
```

A **constrained** lane is fine, and computes at its base exactly as a scalar of that subtype does:

```sysl
type Small = int within 0..100

f() -> unit
    val v: <4>Small = [1, 2, 3, 4]
    val w = v + v

    print(w[0], w[3])

f()
```

```output
2 8
```
