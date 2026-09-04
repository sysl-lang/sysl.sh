---
title: sysl.math.bigint
layout: api-module
headingShift: 0
slugStyle: github
module: sysl.math.bigint
summary: "Integers with no width: sign and magnitude, the magnitude a run of 32-bit limbs."
---

**A general-purpose language without arbitrary precision makes a whole class of program wrong in
the same way** -- a hash reduced modulo something, a factorial, a cryptographic exponent, an
identifier that outgrew 64 bits. Go ships `math/big` and Rust does not, and this takes Go's side:
the alternative is a package that every serious program ends up depending on anyway, and a
standard library is where a type everybody has to agree on belongs.

## The representation

A `BigInt` is a **sign** and a **magnitude**, and the magnitude is a view of `u32` limbs written
**least significant first** with no leading zeros. Zero is the empty magnitude and a clear sign,
so there is exactly one representation of it and nothing has to remember whether it is negative.

**Limbs are 32 bits and the arithmetic is done in 64.** That is what makes every carry, borrow and
partial product exact without asking the language for a wider type or a carry flag: a product of
two limbs is at most `(2^32 - 1)^2`, which fits a `u64` with room for two more limbs of carry.
Sixty-four-bit limbs would halve the work and would need a 128-bit product, which is a different
conversation.

## What it costs

**Every operation that grows allocates**, and the docstrings say so where it is not obvious.
There is no in-place arithmetic and no reuse of a caller's buffer: a `BigInt` is a value that
copies, which is what makes `a + b * c` mean what it looks like, and the cost of that is one
allocation per operation. A program doing this in a loop is doing something a big-integer library
is not the fastest way to do.

**Multiplication is schoolbook and division is Knuth's algorithm D.** Both are quadratic.
Karatsuba, Toom-Cook and a Barrett or Montgomery reduction are all improvements that fit behind
this same surface, and none of them changes an answer -- so they are work somebody does when a
program is slow rather than decisions this module has to make now. What this is for is money,
identifiers, checksums and the occasional exact computation, none of which is GMP's territory.

## What is deliberately not here

**Modular exponentiation, primality, a gcd.** Each is a real function with real subtleties -- a
constant-time `mod_pow` is a different function from a fast one -- and none is needed by anything
this library ships. They belong here eventually and belong here written on purpose.

**Bit operations.** `and`, `or` and a shift over a sign-and-magnitude number mean whatever a
two's-complement reading of an infinite-width value means, which is a decision rather than an
implementation, and nothing has asked.

## Index

[`abs`](#abs) [`add`](#add) [`cmp`](#cmp) [`div`](#div) [`div_rem`](#div_rem) [`from_int`](#from_int) [`mul`](#mul) [`negate`](#negate) [`one`](#one) [`parse`](#parse) [`parse_radix`](#parse_radix) [`pow`](#pow) [`rem`](#rem) [`sub`](#sub) [`to_string`](#to_string) [`to_string_radix`](#to_string_radix) [`zero`](#zero) [`BigInt`](#bigint) [Add for BigInt](#add-for-bigint) [Display for BigInt](#display-for-bigint) [Div for BigInt](#div-for-bigint) [Eq for BigInt](#eq-for-bigint) [From for BigInt](#from-for-bigint) [Hash for BigInt](#hash-for-bigint) [Mul for BigInt](#mul-for-bigint) [Neg for BigInt](#neg-for-bigint) [One for BigInt](#one-for-bigint) [Ord for BigInt](#ord-for-bigint) [Rem for BigInt](#rem-for-bigint) [Sub for BigInt](#sub-for-bigint) [Zero for BigInt](#zero-for-bigint)

## Functions

### `abs`

```sysl
abs(x: BigInt) -> BigInt
```

The magnitude, as a value of this type.

### `add`

```sysl
add(a: BigInt, b: BigInt) -> BigInt
```

`a + b`. Allocates the result's limbs.

Two values of the same sign add their magnitudes and keep it; two of different signs subtract the
smaller magnitude from the larger and take the larger one's sign, which is where a zero result
comes from and is why the sign is dropped when the magnitudes cancel.

### `cmp`

```sysl
cmp(a: BigInt, b: BigInt) -> int
```

`-1`, `0` or `1` as `a` is below, equal to or above `b`.

A negative is below every positive, and between two negatives the *larger magnitude* is the
smaller number -- which is the one place sign-and-magnitude needs a thought that two's complement
does not.

### `div`

```sysl
div(a: BigInt, b: BigInt) -> BigInt
```

`a / b`, truncating toward zero.

### `div_rem`

```sysl
div_rem(a: BigInt, b: BigInt) -> (BigInt, BigInt)
```

`(quotient, remainder)` with the signs applied.

**The quotient truncates toward zero and the remainder takes the DIVIDEND's sign**, which is what
C, Go, Rust and sysl's own `/` and `%` on machine integers all do, and is what makes
`a == q * b + r` hold. It is not the floored division a mathematician would write, and the
difference shows on exactly the cases with a negative operand: `-7 / 2` is `-3` with a remainder
of `-1`, rather than `-4` with a remainder of `1`.

**Division by zero traps**, as it does on a machine integer, rather than answering a `Result`.
That is the language's own answer for the same mistake and there is no reason for this type to
disagree with it -- a caller that cannot rule out a zero divisor checks for one.

### `from_int`

```sysl
from_int(n: long) -> BigInt
```

The value `n` names.

The most negative `long` is the case worth knowing about: its magnitude cannot be reached by
negating it, since that value has no positive counterpart, so the conversion goes through the
unsigned reading of the bits.

### `mul`

```sysl
mul(a: BigInt, b: BigInt) -> BigInt
```

`a * b`, schoolbook. Allocates.

The sign is the sign of the product and the magnitudes multiply, with the one special case every
sign-and-magnitude implementation has: a zero result must not be negative, and here it cannot be,
because a zero operand returns early.

### `negate`

```sysl
negate(x: BigInt) -> BigInt
```

The same magnitude with the other sign, and zero unchanged -- there being no negative zero here
to produce.

### `one`

```sysl
one() -> BigInt
```

One.

### `parse`

```sysl
parse(s: string) -> Option[BigInt]
```

The value `s` names in base ten, or `None`.

### `parse_radix`

```sysl
parse_radix(s: string, base: int) -> Option[BigInt]
```

The value `s` names in `base`, or `None` where it names none.

An optional `+` or `-` and then at least one digit, in either case. Nothing else: no underscores,
no `0x` prefix, no leading or trailing space. A caller reading a number out of a document trims
and strips before it gets here, because what a document allows is the document's business.

**`"-0"` parses to zero and renders as `"0"`**, which is the one input that does not round trip
and could not: there is no negative zero in this representation, which is what makes equality and
hashing straightforward everywhere else.

### `pow`

```sysl
pow(a: BigInt, n: usize) -> BigInt
```

`a` raised to `n`, by repeated squaring. Allocates once per squaring and once per set bit.

**A negative exponent has no integer answer and gives one**, since `x^-1` is not an integer for
any `x` but 1 and -1; a caller wanting a rational wants a rational. `pow(x, 0)` is one, including
for `x` of zero, which is the convention every language and every algebra text takes.

### `rem`

```sysl
rem(a: BigInt, b: BigInt) -> BigInt
```

`a % b`, taking the dividend's sign.

### `sub`

```sysl
sub(a: BigInt, b: BigInt) -> BigInt
```

`a - b`, which is `a + (-b)` and is written as that rather than as a second carry loop.

### `to_string`

```sysl
to_string(x: BigInt) -> string
```

`x` in base ten.

### `to_string_radix`

```sysl
to_string_radix(x: BigInt, base: int) -> string
```

`x` written in `base`, with a leading `-` where it is negative and no `+` where it is not.

**A base outside 2 to 36 traps**, since there is no digit to write past `z` and a caller asking
for base 37 has a mistake rather than an unusual requirement. Zero is `"0"` in every base.

Allocates the answer, and a working buffer the size of the answer.

### `zero`

```sysl
zero() -> BigInt
```

Zero, which is the empty magnitude.

## Types

### `BigInt`

```sysl
struct BigInt
    minus: bool
    mag: []const u32
```

An integer of no fixed width.

**The magnitude is a view, so a `BigInt` costs a retain to copy rather than a copy of its limbs.**
That is what makes passing one around cheap and is the reason the limbs are never written through:
every operation builds a new magnitude, so two values may share one and neither can see the other
change.

| Member | Signature | Description |
|---|---|---|
| `is_zero` | `is_zero(self) -> bool` | Whether this is zero, which is the one value with an empty magnitude. |
| `sign` | `sign(self) -> int` | `-1`, `0` or `1`, which is the comparison against zero written as a number -- what a caller switching on the three cases wants, and what `cmp` answers for a pair. |
| `limbs` | `limbs(self) -> usize` | How many limbs the magnitude takes, which is the closest thing to a size this type has. |
| `to_long` | `to_long(self) -> Option[long]` | The value as a `long`, or `None` where it does not fit. |

## Implementations

### Add for BigInt

```sysl
impl Add for BigInt
```

### Display for BigInt

```sysl
impl Display for BigInt
```

### Div for BigInt

```sysl
impl Div for BigInt
```

### Eq for BigInt

```sysl
impl Eq for BigInt
```

### From for BigInt

```sysl
impl From[long] for BigInt
```

### Hash for BigInt

```sysl
impl Hash for BigInt
```

### Mul for BigInt

```sysl
impl Mul for BigInt
```

### Neg for BigInt

```sysl
impl Neg for BigInt
```

### One for BigInt

```sysl
impl One for BigInt
```

### Ord for BigInt

```sysl
impl Ord for BigInt
```

### Rem for BigInt

```sysl
impl Rem for BigInt
```

### Sub for BigInt

```sysl
impl Sub for BigInt
```

### Zero for BigInt

```sysl
impl Zero for BigInt
```
