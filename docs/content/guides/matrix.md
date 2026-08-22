---
title: matrix
summary: An operator whose result is neither operand's type — one vector space, written once, running over the reals and over the complex numbers.
weight: 110
---

A vector space and the matrices over it, then Gaussian elimination on top — over any field, not over
the reals.

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

**The pivot is where the library stops, and it is a reported finding rather than a rough edge.**
Elimination picks the largest remaining cell in a column, and the complexes carry no `Ord` at all —
there is no order on the plane that respects arithmetic, and the library refuses to invent one. What
pivoting wants is a **magnitude**, an ordering on size rather than on values, and no core trait offers
one. The program declares its own, with the result fixed to `real`, and says why that is a guide's
decision and not a library's: sysl has no associated type, so a library `Magnitude` would have to fix
the answer for every element type at once — committing a rational matrix to floating point. **The
library reaches three of the four things a field needs; the fourth is waiting on associated types.**

**The integers are outside, and the identities are what keep them out.** `Zero` and `One` are written
for the floats and for `Complex` and cannot be written for `int`: the `iN`/`uN` families are open, so
no list of blocks covers them, and every membership the compiler hands out is a method with a
receiver — which a `zero()` has nothing to be called on. A `Matrix[int]` is not writable today, and a
matrix of exact integers is a thing people want.

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
