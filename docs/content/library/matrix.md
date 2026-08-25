---
title: The matrix module
summary: "`sysl.math.matrix` — vectors and matrices over any element type that behaves like a number, two eliminations, and the marker trait that keeps the wrong one off the integers."
weight: 63
---

`sysl.math.matrix` is linear algebra over an element type the module never names. A `Vector[T]` and a
`Matrix[T]` hold anything that implements [`Scalar`](#what-an-element-has-to-be) — the two float
widths, [`Complex[F]`](/library/complex/) at either of them, and the integers — and every operator,
every elimination and all four of their answers are one body each, compiled per element type.

It is a **submodule** of [`sysl.math`](/library/math/) for the same reason
[`sysl.math.complex`](/library/complex/) is: `Vector` and `Matrix` are not names every numeric
program wants in scope. It requires no capability of the language, but it does allocate — the cells
live in a `Buf[T]`, whose length is a runtime value — so a `no alloc` module cannot reach it.

## The four products of a vector space

**`v * w` is a scalar, `v * k` is a vector, `A * v` is a vector and `A * B` is a matrix.** All four
are `Mul`, and they are told apart by the type of the right operand and by nothing else — an operator
trait's result is an argument, so each implementation says what it hands back.

```sysl
import sysl.math.matrix.{Vector, Matrix}

var v = Vector.of([1.0, 2.0, 3.0])
var w = Vector.of([4.0, 5.0, 6.0])
var a = Matrix.of(2, 3, [1.0, 2.0, 3.0, 4.0, 5.0, 6.0])

print(v * w)
print(v * 2.0)
print(a * v)
print(a * a.transpose())
```

```output
32
(2, 4, 6)
(14, 32)
(14, 32); (32, 77)
```

Written with the result fixed to `Self` the first of those is unspellable, and a dot product becomes
a method while scaling stays an operator — so one line of linear algebra reads as a mixture of calls
and symbols. That is the shape this module exists to be able to avoid, and the rule that makes it
affordable is that **the result does not select**: a use writes the operands, so two implementations
agreeing on them are refused where they are written rather than ranked at the call.

## Solving a system

`solve` answers a `Result`, because a system can fail to have the answer it was asked for. `det`,
`rank` and `inverse` come out of the same elimination — one reduction to upper-triangular form
records the pivots, the swaps and the pivot count, and writing them as four algorithms would be four
chances to disagree about what a zero pivot means.

```sysl
import sysl.math.matrix.{Vector, Matrix, solve, det, rank, inverse}

var m = Matrix.of(3, 3, [2.0, 1.0, -1.0, -3.0, -1.0, 2.0, -2.0, 1.0, 2.0])
var b = Vector.of([8.0, -11.0, -3.0])

print(solve(m, b))
print(det(m))
print(rank(m))
print(inverse(m))
print(solve(Matrix.of(2, 2, [1.0, 2.0, 2.0, 4.0]), Vector.of([3.0, 6.0])))
```

```output
Ok((2, 3, -1))
-1
3
Ok((4, 3, -1); (-2, -2, 1); (5, 4, -1))
Err(the matrix is singular)
```

**Both arms of that last line render themselves**, which is what `Vector[T]`, `Matrix[T]` and the
refusal each implementing `Display` buys: a `Result` is printable exactly when its two arms are, so
nothing here writes a formatter. The three refusals are `NotSquare`, `ShapeMismatch` and `Singular`,
and they compare with `==` — a bare enum is its discriminant.

### A width is the whole value's field; a precision is each number's

The two halves of a specifier go to different places, and for a numeric aggregate that is the useful
split rather than a technicality. A **width** describes the field the whole vector or matrix occupies,
so it pads once around the finished shape and never inside it. A **precision** is read by each
component's own renderer in its own terms — significant digits for a real, a minimum digit count for
an integer — so `%.3s` is three digits in every cell rather than the first three bytes of the text.

```sysl
import sysl.math.matrix.{Vector, Matrix}

var v = Vector.of([1.0 / 3.0, -2.0 / 3.0])
var m = Matrix.of(2, 2, [1, 2, 3, 4])

print(f"[${Vector.of([1, 2, 3])}%12s]")
print(f"[${Vector.of([1, 2, 3])}%-12s]")
print(f"[${v}%.3s]")
print(f"[${m}%20s]")
```

```output
[   (1, 2, 3)]
[(1, 2, 3)   ]
[(0.333, -0.667)]
[      (1, 2); (3, 4)]
```

That is [`Complex[F]`'s split](/library/complex/) at one more dimension, and it is arrived at the same
way: the width is learned by rendering once into a [`Counting`](/library/core/) sink that adds up what
it is handed and keeps none of it, so a padded vector costs a second pass rather than a buffer and an
unpadded one costs a single pass. **Rendering allocates nothing** — which does not make the module
allocator-free, since the cells themselves live in a `Buf[T]`.

## One algebra, every element type

Nothing below is a second implementation. The operators, the elimination and all four of its answers
are the bodies above, at a different `T`.

```sysl
import sysl.math.matrix.{Vector, Matrix, solve, det}
import sysl.math.complex.Complex

var cv = Vector.of([Complex(1.0, 1.0), Complex(2.0, -1.0)])
var cm = Matrix.of(2, 2, [Complex(1.0, 1.0), Complex(2.0, 0.0), Complex(0.0, 1.0), Complex(1.0, -1.0)])

print(cv * cv)
print(cv.len())
print(det(cm))
print(solve(cm, cm * cv))
```

```output
3-2i
2.64575
2-2i
Ok((1+1i, 2-1i))
```

**`‖v‖² = v · v` is a fact about the reals rather than about vector spaces, and the first line is
where that shows.** `cv * cv` is a complex number — a sum of `z * z` can be zero for a vector nowhere
near the origin — so `len` is the root of a sum of squared *magnitudes* and the dot product is left to
be what it is. A module written at one element type would have had the two as one function and been
right to.

**The pivot is why [`Magnitude`](/library/math/#magnitude-how-big-when-that-is-not-which-is-greater)
is required.** Elimination chooses the largest remaining cell in a column, and the complexes have no
`Ord` at all — no order on the plane respects arithmetic, and `sysl.math` refuses to invent one. What
pivoting wants is an ordering on *size*, and `Magnitude`'s associated `Size` is what lets a
`Complex[F]` measure in `F` and an integer measure in itself. The elimination compares sizes without
naming what one is.

## What an element has to be

`Scalar` is the whole operator set a cell is used through, plus the two identities and a size:

```sysl
trait Scalar: Add + Sub + Mul + Div + Neg + Zero + One + Eq + Display + Magnitude
    norm(self) -> real
```

`Zero` and `One` are required because **a generic body cannot spell an identity**: `0.0` is a `real`
and nothing else, so a body shared by several element types has to *ask* — `T.zero()`, `T.one()`.
`Display` is not algebra and is required anyway, because a numerical type that cannot be printed is
one nobody can debug.

`norm` is the one member declared here rather than required, and it is a choice rather than a gap.
A magnitude is compared against other magnitudes and stays in the element's own terms, which is what
pivoting wants; a *length* is arithmetic and a *tolerance* is a literal somebody wrote, so both have
to land at a width. `norm` is that view, at `real`.

## A bound is a signature, not a contract

**`int` satisfies every requirement `Scalar` lists, `Div` included.** A bound can require that
division *exists*; it has no way to require that division answers the quotient it was asked for.
Integer `/` truncates, so every multiplier a Gaussian elimination forms over the integers is a proper
fraction that becomes zero — the elimination reduces nothing, and reports a determinant that is
simply wrong. It does not refuse. It answers.

So the module carries a second trait whose entire content is the promise the first one could not make:

```sysl
trait Field: Scalar
```

`Field` declares no members. Implementing it asserts that `/` yields the true quotient, exactly or to
within rounding — an assertion nothing verifies, and nothing could, because exactness is a property
of an operation rather than of a type. `real`, `f32` and `Complex[F]` implement it. **`int`
deliberately does not**, and that absence is the safeguard:

```sysl
import sysl.math.matrix.{Vector, Matrix, solve}

var m = Matrix.of(3, 3, [3, 1, 2, 1, 4, 1, 2, 1, 5])

print(solve(m, Vector.of([1, 2, 3])))
```

```error
requires its type parameter 'T' to implement 'sysl.math.matrix.Field', but int does not
```

A wrong answer became a compile error naming the promise the element type cannot make. Everything
*else* about a `Matrix[int]` still works — it adds, multiplies, transposes, compares and prints —
because none of that divides.

## The elimination the integers deserve

The answer was not to exclude them but to write the algorithm they can carry. Bareiss's fraction-free
elimination computes every entry as a **minor of the original matrix**, so every division it performs
comes out exact by construction: it divides, but only ever where the quotient is already whole.

```sysl
import sysl.math.matrix.{Vector, Matrix, solve_exact, det_exact}

var m = Matrix.of(3, 3, [3, 1, 2, 1, 4, 1, 2, 1, 5])
var b = Vector.of([1, 2, 3])

print(det_exact(m))

solve_exact(m, b) match
    Ok(x) ->
        print(x.numerators)
        print(x.denominator)
        print(x.verifies(m, b))
    Err(e) -> print(e)
```

```output
Ok(40)
(-8, 16, 24)
40
true
```

**It needs *less* of its element type than the elimination that was wrong for it.** No `Field`, no
`Magnitude`, no tolerance, no ordering — a fraction-free pivot is chosen for being non-zero, which
`Eq` already answers. Its determinant is exact where a floating-point one would not be: on a matrix
whose determinant is the difference of two 19-digit products, `det_exact` answers `-1` and the reals
lose the cancellation entirely.

`verifies` is the check no other element type here could write: `A y = b D` compared with `==`, no
tolerance anywhere, because there is nothing to be approximately right about.

**`Exact[T]` deliberately has no `value(i)` accessor.** The one line it would contain is
`numerators.at(i) / denominator`, and over the integers that division truncates — the exact bug the
algorithm exists to avoid, reintroduced at the last step by a convenience. And the answer above is
the reason it matters: the solution is `(-1/5, 2/5, 3/5)`, which no `int` holds. Fraction-free
elimination does not turn the integers into a field. It makes the inexactness **explicit**, as a
numerator and a denominator a caller can see, instead of letting `/` apply it silently.

## A vector is a handle

`Vector[T]` and `Matrix[T]` hold a `&Buf[T]`, so assigning one shares the numbers and costs a retain.
Every operator builds a fresh value, the members that write in place say so in their names, and
`copy` is how a caller stops sharing.

```sysl
import sysl.math.matrix.{Vector, Matrix}

var v = Vector.of([1.0, 2.0, 3.0])
var shared = v
var separate = v.copy()

shared.set(0, 99.0)
separate.set(1, 99.0)

print(v)
print(Matrix.of(2, 2, [1, 2, 3, 4]).trace())
print(Vector.of([1, 2, 3]) == Vector.of([1, 2, 3]))
```

```output
(99, 2, 3)
5
true
```

That is the bargain a string strikes: the operation that copies is named, and the sharing is
documented rather than encoded. The value-semantics matrix a numerical library would want needs a
fixed `[N]T`, which a type whose shape is a runtime value cannot have — so the elimination copies
defensively and leaves the matrix it was given alone.

**`==` is exact and `near` is not, and both exist so the choice is made rather than inherited.**
`v == w` compares componentwise, which is right for the integers and for a residual meant to be zero;
`v.near(w, eps)` asks whether the difference is *small*, which is what a program holding floats
almost always wants.
