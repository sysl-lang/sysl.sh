---
title: sysl.math.matrix
layout: api-module
headingShift: 0
slugStyle: github
module: sysl.math.matrix
summary: "Fraction-free elimination: the same answers over a ring whose division cannot be trusted."
---

`gauss.sysl` requires `Field`, and the integers are not one. What makes that worth a second
algorithm rather than a refusal is that the integers are otherwise a perfectly good element type:
`Matrix[int]` adds, multiplies, transposes, prints and compares, and the only thing wrong with
Gaussian elimination over it is that every multiplier it forms is a proper fraction.

Bareiss is the answer, and its shape is worth stating before the code: every entry it computes is a
**minor of the original matrix**, so every division it performs comes out exact. It divides, but it
only ever divides where the quotient is already whole. That is a promise about *this arithmetic*
rather than about the element type, which is why `Scalar` is bound enough -- and why this file asks
for no `Field`, no `Magnitude`, no tolerance and no ordering.

**The import list is the difference between the two algorithms made visible.** `gauss.sysl` imports
`sysl.math.Magnitude`, because partial pivoting has to know which candidate is *largest*. A
fraction-free pivot is chosen for being **non-zero**, which `Eq` already answers, so this file
imports nothing at all.

## Index

[`det`](#det) [`det_exact`](#det_exact) [`inverse`](#inverse) [`rank`](#rank) [`solve`](#solve) [`solve_exact`](#solve_exact) [`Exact`](#exact) [`Fail`](#fail) [`Matrix`](#matrix) [`Vector`](#vector) [`Field`](#field) [`Scalar`](#scalar) [Add for Matrix[T]](#add-for-matrixt) [Add for Vector[T]](#add-for-vectort) [Display for Fail](#display-for-fail) [Display for Matrix[T]](#display-for-matrixt) [Display for Vector[T]](#display-for-vectort) [Eq for Matrix[T]](#eq-for-matrixt) [Eq for Vector[T]](#eq-for-vectort) [Field for Complex[F]](#field-for-complexf) [Field for f32](#field-for-f32) [Field for real](#field-for-real) [Index for Matrix[T]](#index-for-matrixt) [Index for Vector[T]](#index-for-vectort) [IndexSet for Matrix[T]](#indexset-for-matrixt) [IndexSet for Vector[T]](#indexset-for-vectort) [Mul for Matrix[T]](#mul-for-matrixt) [Mul for Matrix[T]](#mul-for-matrixt-1) [Mul for Matrix[T]](#mul-for-matrixt-2) [Mul for Vector[T]](#mul-for-vectort) [Mul for Vector[T]](#mul-for-vectort-1) [Neg for Matrix[T]](#neg-for-matrixt) [Neg for Vector[T]](#neg-for-vectort) [Scalar for Complex[F]](#scalar-for-complexf) [Scalar for f32](#scalar-for-f32) [Scalar for int](#scalar-for-int) [Scalar for real](#scalar-for-real) [Sub for Matrix[T]](#sub-for-matrixt) [Sub for Vector[T]](#sub-for-vectort)

## Functions

### `det`

```sysl
det[T: Field](m: Matrix[T]) -> T
```

The product of the pivots, with its sign from the parity of the swaps. The element type's own zero
for a singular matrix, which falls out rather than being tested for: a column with no pivot leaves
a zero on the diagonal.

### `det_exact`

```sysl
det_exact[T: Scalar](m: Matrix[T]) -> Result[T, Fail]
```

The determinant, exactly, for an element type that has no rounding to hide behind.

The last diagonal entry *is* the determinant of the reduced matrix -- that is what Bareiss
accumulates rather than a running product, so there is no chain of multiplications to lose
precision through and nothing to divide at the end. The sign comes from the parity of the swaps,
exactly as it does over a field.

A singular matrix has determinant zero, which is an answer rather than a failure, so `Singular`
from the elimination becomes `Ok(T.zero())` here. A non-square one has no determinant at all.

### `inverse`

```sysl
inverse[T: Field](m: Matrix[T]) -> Result[Matrix[T], Fail]
```

`A⁻¹`, by carrying the identity through the same elimination and substituting once per column.

### `rank`

```sysl
rank[T: Field](m: Matrix[T]) -> int
```

The number of pivots the reduction found, which needs no square matrix and no invertibility.

### `solve`

```sysl
solve[T: Field](m: Matrix[T], b: Vector[T]) -> Result[Vector[T], Fail]
```

`A x = b`. Square and non-singular or nothing: an underdetermined system has a family of solutions
and this returns one vector, so promising an answer for one would be lying about which.

### `solve_exact`

```sysl
solve_exact[T: Scalar](m: Matrix[T], b: Vector[T]) -> Result[Exact[T], Fail]
```

`A x = b`, answered as `y` over `D` so that every number stays in `T`.

Back-substitution normally divides by the pivot and lands on a fraction. Solving for `y = D x`
instead keeps it whole: `U[i][i] · x_i = c_i − Σ U[i][j] x_j` multiplied through by `D` makes every
`x` a `y`, and `y_i = D · x_i` is `det(A_i)` by Cramer's rule -- an integer *before* it is a
quotient. So `acc / u.at(i, i)` is exact for the same reason the elimination's division was.

**The denominator is the reduced matrix's own last entry, with no sign applied**, while `det_exact`
above applies one. That is not an inconsistency: a row swap permutes the *equations*, so it flips
the determinant's sign and leaves the solution alone. `y` and `D` come out of the same permuted
system and agree with each other; the determinant is a statement about the original and does not.

## Types

### `Exact`

```sysl
struct Exact[T: Scalar]
    numerators: Vector[T]
    denominator: T
```

A solution that has not been divided yet: `numerators[i] / denominator` is the answer, and the
division is left to the caller precisely because it is the one operation this file will not do.

**There is deliberately no `value(i)` accessor.** Writing one would put `numerators.at(i) /
denominator` behind a name that reads like a component, and over the integers that division
truncates -- the exact bug the second algorithm exists to avoid, reintroduced at the last step by a
convenience. A caller that wants a number has to write the division itself and be looking at it.

**Exact arithmetic does not make an answer representable, which is the trap.** A system can have
numerators `(-8, 16, 24)` over a denominator of 40, and the solution is then `(-1/5, 2/5, 3/5)`,
which no `int` holds. Fraction-free elimination does not turn the integers into a field; it makes
the inexactness **explicit**, as a numerator and a denominator the caller can see, instead of
letting `/` apply it silently.

| Member | Signature | Description |
|---|---|---|
| `verifies` | `verifies(self, m: Matrix[T], b: Vector[T]) -> bool` | `A y = b D`, with `==`. |

### `Fail`

```sysl
enum Fail
    NotSquare
    ShapeMismatch
    Singular
```

How an elimination can fail to answer the question it was asked.

Three distinct causes, because a caller can act differently on each: a shape is a programming
mistake, and a singular matrix is a fact about the numbers. Both eliminations answer with this one,
so a caller that switches from `solve` to `solve_exact` keeps its error handling.

### `Matrix`

```sysl
struct Matrix[T: Scalar]
    rows: int
    cols: int
    cells: &Buf[T]
```

Matrices over any `Scalar`, and the two products whose results are different types.

`A * v` gives a vector and `A * B` gives a matrix, so one type carries three implementations of one
trait: `Mul[Vector[T], Vector[T]]`, `Mul` and `Mul[T]`. Each is selected by the type of the right
operand, and each declares what it hands back. Nothing here is a method that wanted to be an
operator.

**The matrix product is the defaulted one**, written `impl[T: Scalar] Mul for Matrix[T]`: its
operand and its result are both `Self`, which is what the trait's own defaults already say. Writing
`Mul[Matrix[T], Matrix[T]]` would say the same thing twice, and a reader would be entitled to look
for a difference that is not there.

The cells are stored row-major in one `&Buf[T]`, so a matrix is a handle exactly as a vector is:
the operators build fresh matrices and `copy` is how a caller stops sharing.

| Member | Signature | Description |
|---|---|---|
| `zeros` | `zeros(rows: int, cols: int) -> Matrix[T]` |  |
| `of` | `of(rows: int, cols: int, xs: []const T) -> Matrix[T]` | A matrix from the numbers a program wrote, read row by row. |
| `identity` | `identity(n: int) -> Matrix[T]` | The identity, which needs both of the element type's identities -- a zero everywhere and a one down the diagonal -- and is where the pair of core traits earns its second member. |
| `at` | `at(self, i: int, j: int) -> T` |  |
| `set` | `set(*self, i: int, j: int, v: T)` |  |
| `is_square` | `is_square(self) -> bool` |  |
| `row` | `row(self, i: int) -> Vector[T]` |  |
| `col` | `col(self, j: int) -> Vector[T]` |  |
| `trace` | `trace(self) -> T` | The sum of the diagonal, which needs no elimination and no invertibility. |
| `copy` | `copy(self) -> Matrix[T]` |  |
| `transpose` | `transpose(self) -> Matrix[T]` |  |
| `swap_rows` | `swap_rows(*self, a: int, b: int)` | Exchanging two rows in place -- what a pivot search does, and the one operation on a matrix that is not a fresh value. |
| `near` | `near(self, rhs: Matrix[T], tol: real) -> bool` | Agreement to within a tolerance, for the same reason `Vector.near` exists: `==` is exact and floating point almost never wants it. |

### `Vector`

```sysl
struct Vector[T: Scalar]
    n: int
    cells: &Buf[T]
```

Vectors over any `Scalar`, and the half of a vector space whose product yields a *scalar*.

**`v * w` is a `T` and `v * k` is a `Vector[T]`, and both are `Mul` on `Vector[T]`.** An operator
trait's result is an argument, so the two are told apart by the type of the right operand and by
nothing else, and each says what it hands back. Written with the result fixed to `Self` the first
of them is unspellable, and a dot product becomes a method while scaling stays an operator -- which
is a split a reader of one line of linear algebra should never have to see.

**Every operator here is written once, for every element type at once.** A block whose trait
arguments are built out of its own parameter says the same thing at every instantiation, so
`impl[T: Scalar] Mul[Vector[T], T] for Vector[T]` is one dot product for the reals, the complexes,
the integers and whatever else implements `Scalar`.

**A `Vector[T]` is a handle.** It is a length and a `&Buf[T]`, so assigning one shares the numbers
and costs a retain. Every operator below therefore builds a fresh vector rather than writing into
either operand, the one member that does write in place says so in its name (`set`), and `copy` is
how a caller stops sharing. That is the bargain a string strikes: the operation that copies is
named, and the sharing is documented rather than encoded. A value-semantics vector would need a
fixed `[N]T`, which a type whose length is a runtime value cannot have.

| Member | Signature | Description |
|---|---|---|
| `zeros` | `zeros(n: int) -> Vector[T]` | The zero vector, which needs the element type's own zero and has no other way to get one. |
| `of` | `of(xs: []const T) -> Vector[T]` | A vector from the numbers a program wrote. |
| `basis` | `basis(n: int, i: int) -> Vector[T]` | The `i`th standard basis vector, which is a column of the identity and the second place an identity is asked for rather than written. |
| `at` | `at(self, i: int) -> T` |  |
| `set` | `set(*self, i: int, v: T)` |  |
| `quadratic_form` | `quadratic_form(self) -> T` | The dot product of a vector with itself, which is `self * self`. |
| `len` | `len(self) -> real` | The Euclidean length: the root of the sum of the squared *magnitudes*, which is the definition that holds at every element type. |
| `copy` | `copy(self) -> Vector[T]` | A vector that shares nothing with this one. |
| `near` | `near(self, rhs: Vector[T], eps: real) -> bool` | Agreement to within a tolerance, which is the question floating point makes the right one almost everywhere. |

## Traits

### `Field`

```sysl
trait Field: Scalar
```

The promise `Scalar` cannot make: that `/` answers the quotient it was asked for.

**A bound is a signature, not a contract, and this trait exists because of the gap.** `Scalar`
requires `Div`, so it requires that division *exists*; it has no way to require that division
*means* anything. `int` supplies `Div` and satisfies `Scalar` outright, and integer `/` truncates:
every multiplier a Gaussian elimination forms over the integers is a proper fraction, every one of
them truncates to zero, and the elimination reduces nothing and reports a determinant that is
simply wrong. It does not refuse. It answers.

**So the promise is made by the implementor rather than checked by the compiler.** `Field` declares
no members at all: implementing it is an assertion that `/` yields the true quotient, exactly or to
within rounding. Nothing verifies that, and nothing could -- exactness is a property of an
operation, not of a type, so there is nothing for a signature to name. What the marker buys is that
the assertion is written down once, by the type that can make it, instead of being assumed at every
call.

`gauss.sysl` requires it and `bareiss.sysl` does not, which is the whole difference between the two
eliminations stated in a bound. A `Matrix[int]` reaches every operator in this module, reaches
`det_exact` and `solve_exact`, and is refused by `solve` and `det` by name.

### `Scalar`

```sysl
trait Scalar: Add + Sub + Mul + Div + Neg + Zero + One + Eq + Display + Magnitude
    norm(self) -> real
```

The element type of a vector or a matrix: everything the algebra in this module performs on a cell.

**`Display` is not algebra and is required anyway**, because a numerical type that cannot be
printed is a numerical type nobody can debug -- and a bound is where a generic body says what it
needs, whatever the need is for.

| Member | Signature | Description |
|---|---|---|
| `norm` | `norm(self) -> real` | The size of this value as a `real`: the same quantity `magnitude` gives, in the one width this module writes lengths and tolerances at. |

## Implementations

### Add for Matrix[T]

```sysl
impl[T: Scalar] Add for Matrix[T]
```

### Add for Vector[T]

```sysl
impl[T: Scalar] Add for Vector[T]
```

### Display for Fail

```sysl
impl Display for Fail
```

The rendering, so that a refusal can be printed without matching on it -- and so that a
`Result[Vector[T], Fail]` prints as a whole, since `Result` renders both arms through theirs.

**No variant carries anything, so `==` is already there** and no `Eq` of its own is needed: a value
whose variants are bare *is* its discriminant, and the compiler compares it. `e == Singular` is the
spelling, and a `Fail::Pos(e) == Fail::Pos(Singular)` ladder is a workaround for a gap that is
closed.

### Display for Matrix[T]

```sysl
impl[T: Scalar] Display for Matrix[T]
```

`(1, 2); (3, 4)` -- the rows, each in the form `Vector[T]` renders in.

The width belongs to the whole matrix and the precision reaches the cells, exactly as it does for a
vector and for `Complex[F]`; and it is measured into a `Counting` sink rather than gathered, so an
ordinary print allocates nothing.

### Display for Vector[T]

```sysl
impl[T: Scalar] Display for Vector[T]
```

`(a, b, c)`, with each element rendered as its own type renders it -- which is what `Scalar`
requiring `Display` buys. The field applies to the whole value rather than to each part
(`library/core.md § A specifier is the whole value's field`), so the components are padded once
between them rather than each in turn.

**The width is measured rather than gathered**, which is what this used to cost: building the text
first meant a string per component plus one per separator, `2n + 1` of them for a vector of `n`,
all thrown away as soon as they were written. Rendering into a `Counting` sink answers the same
question -- how wide did it come out -- and stores nothing, and the sink is built only where a
width was asked for, so an ordinary print of a vector allocates nothing at all.

### Eq for Matrix[T]

```sysl
impl[T: Scalar] Eq for Matrix[T]
```

### Eq for Vector[T]

```sysl
impl[T: Scalar] Eq for Vector[T]
```

Exact, componentwise equality. Right for the integers and for a residual that is meant to be zero;
**`near` is what a program holding floats should reach for**, and the two are separate so that the
choice is made rather than inherited.

### Field for Complex[F]

```sysl
impl[F: Float + Display] Field for Complex[F]
```

Complex division is the same bargain as real division: total, and right to within rounding.
`sysl.math.complex` divides by Smith's algorithm rather than by the textbook formula, which is what
keeps that true at operands the naive expression would overflow on.

### Field for f32

```sysl
impl Field for f32
```

### Field for real

```sysl
impl Field for real
```

Floating-point division is not exact, and that is not what the marker claims. It claims the
quotient is the true one to within rounding, which is precisely what an elimination with a
tolerance is written against: `gauss.sysl` rejects a pivot whose size is below `tiny` because
subtraction leaves noise where exact arithmetic leaves zero. Truncation is a different thing, and
it is the thing this excludes.

### Index for Matrix[T]

```sysl
impl[T: Scalar] Index[(int, int), T] for Matrix[T]
```

A pair as the index: a subscript takes one argument and a matrix wants two, so the one argument is
a pair.

### Index for Vector[T]

```sysl
impl[T: Scalar] Index[int, T] for Vector[T]
```

### IndexSet for Matrix[T]

```sysl
impl[T: Scalar] IndexSet[(int, int), T] for Matrix[T]
```

### IndexSet for Vector[T]

```sysl
impl[T: Scalar] IndexSet[int, T] for Vector[T]
```

### Mul for Matrix[T]

```sysl
impl[T: Scalar] Mul[Vector[T], Vector[T]] for Matrix[T]
```

A matrix applied to a vector: the result is a **vector**, which is neither operand's type. Written
as `self.row(i) * rhs`, so the dot product in `vector.sysl` is what computes each component -- one
operator whose result is a scalar, inside one whose result is a vector.

### Mul for Matrix[T]

```sysl
impl[T: Scalar] Mul for Matrix[T]
```

The one implementation here that writes no trait arguments at all, because the trait's defaults
already say what it does: a matrix times a matrix is a matrix.

### Mul for Matrix[T]

```sysl
impl[T: Scalar] Mul[T] for Matrix[T]
```

### Mul for Vector[T]

```sysl
impl[T: Scalar] Mul[Vector[T], T] for Vector[T]
```

The dot product: two vectors in, one **element** out. `Mul[Vector[T], T]` -- the operands select
the implementation and the implementation says the result, so this and the scaling below are one
operator on one type.

The accumulator starts at `T.zero()`, which is also what makes a zero-length vector's dot product
the value every other one has rather than a case to special-case.

### Mul for Vector[T]

```sysl
impl[T: Scalar] Mul[T] for Vector[T]
```

Scaling, whose result *is* a vector. Nothing but the right operand's type tells this from the dot
product above, and nothing needs to.

### Neg for Matrix[T]

```sysl
impl[T: Scalar] Neg for Matrix[T]
```

### Neg for Vector[T]

```sysl
impl[T: Scalar] Neg for Vector[T]
```

### Scalar for Complex[F]

```sysl
impl[F: Float + Display] Scalar for Complex[F]
```

The complexes, which are the case the whole generic exercise is for. `Complex[F]` measures in `F`
-- the modulus is a real number however the parts are stored -- so this is one widening and no
arithmetic, at every float width `sysl.math` has.

### Scalar for f32

```sysl
impl Scalar for f32
```

The narrower width, widened at the end rather than at the start. `magnitude` answers at the
element's own width -- an `f32`'s size is an `f32`, deliberately and not the `real` it would widen
to -- so a `Matrix[f32]` pivots on `f32` sizes and only a length or a tolerance pays for a
conversion.

### Scalar for int

```sysl
impl Scalar for int
```

The integers, and the reason this module has a second elimination.

**Every requirement of `Scalar` is met outright**, `Div` included, so `Matrix[int]` is an ordinary
matrix here: it adds, multiplies, transposes, prints and compares, and `bareiss.sysl` solves it
exactly. `Zero` and `One` are the standard module's, and `magnitude` reaches the whole integer
family through one blanket block, so the only member left to supply is the view of a size as a
`real`.

**There is deliberately no `impl Field for int`.** That absence is the entire safeguard: it is what
turns Gaussian elimination over the integers from a wrong answer into a compile error naming the
promise `int` cannot make.

### Scalar for real

```sysl
impl Scalar for real
```

The reals, whose size is already a `real`, so the view is the magnitude itself.

### Sub for Matrix[T]

```sysl
impl[T: Scalar] Sub for Matrix[T]
```

### Sub for Vector[T]

```sysl
impl[T: Scalar] Sub for Vector[T]
```
