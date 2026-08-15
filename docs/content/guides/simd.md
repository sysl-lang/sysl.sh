---
title: simd
summary: One kernel compiled for more than one register width — a lane count that is a value parameter, and the four things writing a constraint solver lane-wise turned up.
weight: 170
---

Box2D's `contact_solver.c` writes the same solver four times — AVX2, NEON, SSE2 and a scalar
fallback, selected by `#if` — and over half of that 2120-line file is those four copies. This program
asks whether sysl can write it once.

**The axis: one body, more than one register width.** Every other program in the set is written for
one machine shape. This is the one where the *width* is the variable, and the interesting part is
that the mechanism is not a vector feature at all.

## The lane count is an ordinary value parameter

A [`<N>T`](/reference/vectors/) is N lanes of T. What makes one kernel serve several widths is that
`N` can come from a [value parameter](/reference/generics/), exactly as `[N]T` reads an array's
length:

```sysl
solve[const W: usize](vn: <W>f32, mass: <W>f32, bias: <W>f32) -> <W>f32
    val impulse = (bias - vn) * mass

    // The impulse a contact applies can only push, never pull — so a negative one is clamped away.
    return (impulse < 0.0).select(0.0, impulse)
end solve
```

Nothing in that body names a width. `W` is read off the argument, the mask `impulse < 0.0` is a
`<W>bool` because the vector it compares is a `<W>f32`, and `select` chooses at whatever width the
mask has. Called with four lanes and with eight, it is two bodies holding different instructions from
one piece of source — `fmul <4 x float>` in one and `fmul <8 x float>` in the other.

**The clamp is why `select` is a method rather than an `if`.** Some lanes want it and others do not,
and a branch takes one path for the whole register. Both sides are computed and the mask picks per
lane. That a mask is an ordinary value — it binds, combines with `&`, and reduces with `any` and
`all` — is what keeps the solver's convergence test to one word instead of a loop with an early exit.

## What it found

**A vector may not cross to C, and the refusal had to be built.** The scope said it was refused and
nothing implemented it, so an `extern` taking a `<4>f32` emitted the declaration and made a call
whose convention nothing had checked. Which register a vector arrives in differs by target *and* by
which extensions the other side was compiled for — so the failure mode is a call that resolves and
corrupts its arguments, not one that fails to link, which is precisely what a boundary check exists
to prevent. The shape that does cross is the one C's own SIMD-taking functions take: a pointer to the
lanes. The [FFI reference](/reference/ffi/) carries the rule.

**`v += 1.0` was refused while `v = v + 1.0` beside it worked.** The compound form did not broadcast
its scalar, so two spellings defined to reach the same instruction disagreed about it. That is the
kind of gap a program finds and a suite does not: a suite tests the form it was written for, and a
program writes whichever one reads better.

**A vector's lanes could not reach memory, and that was the main finding.** There was no way to move
a `<W>f32` into a `[W]f32` or a `[]f32`, and no way to build one from a run of a slice — so a kernel
could compute a batch of results and had nowhere to put them. At a *parameterised* width it could not
be written at all, since a lane index must be a constant and a `[const W]` body has no constant to
write. It was reported rather than worked around, because the workaround would have been per-width
and would have been copied.

[`xs.load(i)` and `xs.store(i, v)`](/reference/vectors/) are what closed it, and this is the loop the
finding said could not be written:

```sysl
batch[const W: usize](vn: []const f32, mass: []const f32, bias: []const f32, out: []f32, relax: <W>f32)
    var i: usize = 0

    while i + W <= vn.len
        val v: <W>f32 = vn.load(i)
        val m: <W>f32 = mass.load(i)
        val b: <W>f32 = bias.load(i)

        out.store(i, solve(v, m, b) * relax)
        i += W

    while i < vn.len
        out[i] = scalar_solve(vn[i], mass[i], bias[i]) * relax[0]
        i += 1
end batch
```

**The tail is the caller's and it is written out rather than hidden**, because every kernel over real
data has one: an array whose length is not a multiple of the register's width ends with fewer
elements left than there are lanes, and a load of that run traps rather than reading past the end. A
masked load would answer it in one instruction and sysl has none; a scalar loop answers it in three
lines, which is what C's SIMD code writes anyway.

**`relax` is a vector rather than an `f32`, and it is a choice rather than a workaround.** The
relaxation factor of a Gauss-Seidel step is a constant broadcast across the lanes, and every real
SIMD solver passes its constants as vectors for that reason — so the parameter says what the kernel
does, and `W` falls out of it.

It was also, when this program was written, the only way `W` could enter. Both halves of that have
since moved: a load takes its width from what receives the value, and an **operand** is such a place
now, so `xs.load(i) * by` reads its lane count off `by`; and a **written type argument at a call** is
what a kernel with no vector parameter uses, so `add[8](a, b, out)` says the width where the reader
is standing. This program's findings are what closed both.

**There is still no shuffle, and the gather is where that shows.** A real solver reads its bodies
through an index table — body 17 in lane 0, body 3 in lane 1 — and moving eight scattered velocities
into lane order costs a scratch array and a pass of scalar stores:

```sysl
gather(xs: []const f32, at: []const usize) -> <8>f32
    var scratch: [8]f32

    for i in 0..<at.len
        scratch[i] = xs[at[i]]

    return scratch.load(0)
end gather
```

That is better than the eight written-out lane writes it replaces, and it is still not one
instruction. **The store is what makes the loop possible**: a lane index has to be a constant,
because a vector has no address to bounds-check against and a computed lane would be LLVM's `poison`
rather than a trap — while an array index is checked and may be computed. Putting the lanes somewhere
a subscript works is how "check every lane" gets to be an ordinary `for`.

It is also the honest limit on the claim this program set out to test. Box2D's four copies differ in
the **gather-and-transpose** half and agree about the arithmetic, so *write it once* is a true
statement about the solver and a weaker one about the loading.

---

[Source](https://github.com/sysl-lang/sysl/tree/dev/guide/simd) ·
Back to [the guide programs](/guides/), or on to the [vectors reference](/reference/vectors/).
