---
title: matrix
summary: An operator whose result is neither operand's type — one vector space written once and run at four element types, one of which satisfies the bound and breaks the algorithm.
weight: 110
---

A vector space and the matrices over it, then Gaussian elimination on top — over any field, not over
the reals. And then a second elimination, for the element type that satisfies the bound and is not a
field at all.

**The axis: an operator whose result is neither operand's type.** `A * v` gives a vector and `A * B`
gives a matrix, so one type carries three implementations of one trait — `Mul[Vector[T], Vector[T]]`,
`Mul` and `Mul[T]`. Each is selected by the type of the right operand, and each declares what it hands
back.

**Nothing here is a method that wanted to be an operator**, which is the whole point of the exercise.
This is the program that demonstrates [parameterized traits](/reference/traits/) carrying real weight:
a type implements a trait once at each argument list, so three multiplications on one type are
ordinary rather than a conflict, and the argument list is what tells a call which it meant.

**And every one of them is written once, for every element type at once.** A block whose trait
arguments are built out of its own parameter says the same thing at every instantiation, so
`impl[T: Scalar] Mul[Vector[T], T] for Vector[T]` is *the* dot product — for the reals, for `f32`, and
for `Complex[real]`. The program runs all three, and there is one of each body in it.

Contrast [`sysl.time`](/library/time/), where the same mechanism does *not* rescue
`Instant - Instant -> Duration`. The difference is exactly which position the varying type is in: here
the result is named by the row that was selected, and there it would have had to be named by a row
that could not exist — which is what `Out` on the operator traits was added for, and what makes both
rows of `Sub` on `Instant` writable today.

## What it exercises

**A generic body cannot spell an identity, so it asks.** `0.0` is a `real` and nothing else, so the
zero vector, the identity matrix and the dot product's accumulator go through
[`Zero` and `One`](/library/math/) — `T.zero()`, `T.one()` — which are core traits precisely because
this is not a question about floats. The concrete version of this program would never have noticed
they exist.

**Generalising found an identity that is not one.** `‖v‖² = v · v` is a fact about the reals rather
than about vector spaces: over the complexes `z * z` is a complex number, and a sum of them can be
zero for a vector nowhere near the origin. So a length is the root of a sum of squared *magnitudes*
and the dot product is left to be what it is. One element type is not enough to tell the two apart,
and the program had them as one function until it had two.

**The pivot was where the library stopped, and it is the library's now.** Elimination picks the
largest remaining cell in a column, and the complexes carry no `Ord` at all — there is no order on the
plane that respects arithmetic, and the library refuses to invent one. What pivoting wants is a
**magnitude**, an ordering on size rather than on values. The program declared its own with the result
fixed to `real`, and said why that was a guide's decision and not a library's: a library trait would
have had to fix the answer for every element type at once, committing a rational matrix to floating
point.

**An [associated type](/reference/traits/#a-trait-may-declare-an-associated-type) removed the
obstacle.** [`sysl.math.Magnitude`](/library/math/#magnitude-how-big-when-that-is-not-which-is-greater)
declares `type Size: Ord` and each implementation fills it, so a `Complex[F]` measures in `F` and an
integer measures in itself. The program now *requires* that trait and implements none of it — the
three memberships it pivots on are the library's own — and the elimination compares `T::Size` values
without naming what one is. A `Matrix[f32]` compares `f32` sizes where it used to widen every one of
them to `real`.

**What `Scalar` still declares is one member, and it is a choice rather than a gap.** `norm` is the
view of a size as a `real`, which is what a length and a literal tolerance are written at in this
program. So the line falls where it should: the *ordering* is asked of the element type in its own
terms, and the one fixed number — the tolerance below which a pivot counts as zero — is this
program's, at this program's width.

**A bound is a signature, not a contract — and the integers are how this program found that out.**
`int` meets every requirement in `Scalar`'s bound outright, `Div` included, so `impl Scalar for int`
is one line and `Matrix[int]` compiles. Gaussian elimination then runs on it and is **wrong**:
integer `/` truncates, so every multiplier in a well-chosen system becomes zero, no row is ever
reduced, and the determinant comes back 60 for a matrix whose determinant is 40. It does not refuse.
It answers, wrapped in `Ok`, and nothing in the bound, the types or the run says otherwise.

**That is the finding, and it is not an argument for a stronger bound.** A bound can require that `/`
*exists*; it cannot require that it returns what it was asked for. A field's division is total and
exact and a ring's is neither, and `Scalar` admits both without being able to mention the difference
— so the type system checked everything it was asked to check and the program is still wrong.
Exactness is a property of an operation rather than of a type, so there is nothing for a bound to
name.

**So the integers are not excluded — they get the algorithm they deserve.** `bareiss.sysl` is
fraction-free elimination: every entry it computes is a minor of the original matrix, so every
division it performs is exact by construction rather than by hope. It asks *less* of its element type
than Gauss does — no `Magnitude`, no tolerance, no ordering, because a fraction-free pivot is chosen
for being non-zero rather than for being large — and its import list is empty, which is the
difference between the two algorithms made visible in the file header.

**Two things fall out that are worth more than the algorithm.** A tolerance is a property of *inexact*
arithmetic: Gauss rejects a pivot with `norm(...) <= tiny` because subtraction over the reals leaves
noise, and over the integers that would discard perfectly good pivots for being small — so `Scalar`
can no more say which zero test is right than it can say whether `/` is exact. And exact arithmetic
does **not** make an answer representable: the worked system solves to `(-1/5, 2/5, 3/5)`, so
`solve_exact` returns numerators over a denominator rather than a vector, and deliberately offers no
accessor that would divide them. Making the inexactness explicit is the whole of what it buys.

**A matrix is a handle exactly as a vector is.** The cells are stored row-major in one `&Buf[T]`, so
the operators build fresh values and `copy` is how a caller stops sharing. That makes the memory
question visible in a place people usually do not think about it: a linear-algebra type is a
*container*, and whether `B = A` shares or copies is a decision the language makes you write down.

**Gaussian elimination is where the numerics land.** Pivoting, and the fact that a comparison against
zero is the wrong test for a float, are what the second half is about — and they are ordinary sysl,
because the operator work in the first half means the algorithm reads as the algorithm.

---

[Source](https://github.com/sysl-lang/sysl/tree/dev/guide/matrix) ·
Next: [ring](/guides/ring/) — ranges, attributes, contracts, and invariants.
