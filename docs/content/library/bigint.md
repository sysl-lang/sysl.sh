---
title: The bigint module
summary: "`sysl.math.bigint` — integers with no width: sign and magnitude over 32-bit limbs, schoolbook multiply, Knuth's algorithm D, and text in any base."
weight: 63
---

**Every declaration in `sysl.math.bigint`, with its signature:** [the generated API page](/api/sysl-math-bigint/#index). This page is the argument — what the module is for, and how its pieces fit; that one is the list.

A `BigInt` is an integer with no width. It is here because a general-purpose language without one
makes a whole class of program wrong in the same way — a hash reduced modulo something, a factorial,
a cryptographic exponent, an identifier that outgrew 64 bits — and because the alternative is a
package every serious program ends up depending on anyway. Go ships `math/big`; this takes Go's side.

```sysl
import sysl.math.bigint.{from_int, mul, one, pow, to_string}

var f = one()

for i in 1..<21
    f = mul(f, from_int(long(i)))

print(to_string(f))
print(to_string(pow(from_int(2), 128)))
```

```output
2432902008176640000
340282366920938463463374607431768211456
```

## Sign and magnitude, and the magnitude is limbs

A `BigInt` is a **sign** and a **magnitude**, and the magnitude is a view of `u32` limbs written
least significant first with no leading zeros. Zero is the empty magnitude and a clear sign, so there
is exactly one representation of it and nothing has to remember whether it is negative — which is
what makes `Eq`, `Ord` and `Hash` straightforward rather than careful.

**Limbs are 32 bits and the arithmetic is done in 64.** That is what makes every carry, borrow and
partial product exact without asking the language for a wider type or a carry flag: a product of two
limbs is at most `(2^32 - 1)^2`, which fits a `u64` with room for two more limbs of carry.

**The magnitude is a view, so copying a `BigInt` costs a retain rather than a copy of its limbs.**
That is what makes passing one around cheap, and it is why the limbs are never written through: every
operation builds a new magnitude, so two values may share one and neither can see the other change.

## The operators are there, and they allocate

`+`, `-`, `*`, `/` and `%` are all implemented, and so are `==`, `<` and the rest. Every one of them
that grows allocates — there is no in-place arithmetic and no reuse of a caller's buffer, which is
what makes `a + b * c` mean what it looks like:

```sysl
import sysl.math.bigint.{parse, to_string}

val a = parse("12345678901234567890").expect("a number")
val b = parse("98765432109876543210").expect("a number")

print(to_string(a * b))
print(to_string(a + b))
print(a < b, a == a)
```

```output
1219326311370217952237463801111263526900
111111111011111111100
true true
```

A program doing this in a loop is doing something a big-integer library is not the fastest way to do.

## Division truncates toward zero, as everything else in the language does

The quotient truncates and the remainder takes the **dividend's** sign, which is what C, Go, Rust and
sysl's own `/` and `%` on machine integers all do — and is what makes `a == q * b + r` hold. It is not
the floored division a mathematician would write, and the difference shows on exactly the cases with a
negative operand.

```sysl
import sysl.math.bigint.{div_rem, from_int, to_string}

val (q, r) = div_rem(from_int(-7), from_int(2))

print(to_string(q), to_string(r))
```

```output
-3 -1
```

`div_rem` answers both at once, which is one division rather than two. **Division by zero traps**, as
it does on a machine integer, rather than answering a `Result`.

## The algorithms are named so they can be checked

**Multiplication is schoolbook** and **division is Knuth's algorithm D** (*The Art of Computer
Programming*, volume 2, §4.3.1). Both are quadratic. Karatsuba, Toom-Cook and a Barrett or Montgomery
reduction are all improvements that fit behind this same surface and change no answer, so they are
work somebody does when a program is slow rather than decisions this module had to make.

The one thing worth knowing about algorithm D is that its quotient digit is a *guess* that has to be
corrected twice — a loop that tightens it, and an add-back for the one case the loop still gets wrong.
The add-back fires about twice in every `2^32` digits, which is rare enough that a suite of generated
inputs never enters it: the library's own tests use inputs constructed to force it, because two
hundred random pairs left the branch green with it deleted.

## Text in any base from 2 to 36

Both directions work a chunk at a time rather than a digit at a time, the chunk being the largest
power of the base that fits in a limb — `10^9` for base ten, `2^31` for binary. So rendering divides
the whole magnitude by a billion and gets nine digits out of the remainder with ordinary machine
arithmetic.

```sysl
import sysl.math.bigint.{parse, parse_radix, to_string_radix}

val x = parse("340282366920938463463374607431768211455").expect("a number")

print(to_string_radix(x, 16))
print(to_string_radix(parse("255").expect("a number"), 2))
print(parse_radix("ff", 16).expect("a number").to_long().expect("it fits"))
```

```output
ffffffffffffffffffffffffffffffff
11111111
255
```

Parsing takes an optional sign and then at least one digit, in either case. Nothing else: no
underscores, no `0x` prefix, no leading or trailing space. What a document permits is the document's
business, and stripping is one call before this one.

**`"-0"` parses to zero and renders as `"0"`**, which is the one input that does not round trip and
could not — there is no negative zero in this representation, which is exactly what makes equality and
hashing straightforward everywhere else.

## Getting a machine integer back out

```sysl
import sysl.math.bigint.{from_int, parse}

print(from_int(42).to_long().expect("it fits"))
print(parse("9223372036854775808").expect("a number").to_long().is_none())
```

```output
42
true
```

`to_long` answers `None` rather than truncating, because a big integer that has outgrown 64 bits is
exactly the case this type exists for and silently wrapping it would defeat the purpose.

## What is deliberately not here

**Modular exponentiation, primality, a gcd.** Each is a real function with real subtleties — a
constant-time `mod_pow` is a different function from a fast one — and none is needed by anything the
library ships. They belong here eventually and belong here written on purpose.

**Bit operations.** `and`, `or` and a shift over a sign-and-magnitude number mean whatever a
two's-complement reading of an infinite-width value means, which is a decision rather than an
implementation, and nothing has asked.
