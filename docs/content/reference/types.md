---
title: Types
summary: Every type the language has — the open integer families, the closed float set, and the aggregates built on them.
weight: 20
---

sysl's types fall into four groups: **scalars** (numbers, `bool`, `char`), the built-in **`string`**,
the **aggregates** you build (arrays, slices, structs, enums), and the **modes** that decide where a
value lives (`T`, `&T`, `*T`). This page covers the first three. The modes have a
[page of their own](/reference/memory/), because they are about storage rather than shape.

There is no implicit conversion anywhere in the language. Every width change, signedness change, and
float/integer crossing is written as a cast, and a cast that could lose information is written and
seen.

## Integers are an open family

`iN` and `uN` are not a fixed set of four sizes. They are an **open family parameterized by an
arbitrary bit width**: `i5`, `u3`, `u12`, `i128` are all types you may write, and none of them needed
the compiler to have heard of them. LLVM supports integers of any width natively, so this is a
capability of the target rather than something sysl emulates.

```sysl
var small: u3 = 5
var odd: i5 = -7
var wide: u12 = 4000

print(small, odd, wide)
```

```output
5 -7 4000
```

That openness is the reason the `iN` spelling exists at all. If the integers were only four sizes,
carrying both `iN` names *and* C-style names would be two spellings for every type and no benefit.
Instead there are two layers, each earning its place: `iN`/`uN` is the general mechanism, and a short
list of **aliases** covers the common widths.

| alias | is | | alias | is |
|---|---|---|---|---|
| `byte` | `u8` | | `short` | `i16` |
| `ushort` | `u16` | | `int` | `i32` |
| `uint` | `u32` | | `long` | `i64` |
| `ulong` | `u64` | | `real` | `f64` |

C's "how wide is `long`?" problem — the usual reason to distrust names like these — **does not apply**,
because each width is pinned *by definition*. `long` is exactly `i64`, on every target, always.

**But that is an anti-ambiguity guarantee, not an ABI promise**, and the difference matters at a
foreign boundary. On a 64-bit host every alias happens to match its C namesake, which is what makes
them safe in `extern` code there. On a 32-bit target C's `long` is 32 bits while sysl's is still
exactly 64 — so **precise FFI should use the explicit-width names**, which match C's `int32_t` and
`int64_t` on every target. Note also that `i8` has no alias: there is no settled C-style name for a
signed byte worth adopting.

### Arithmetic wraps

Integer arithmetic wraps at the **declared** width, and this is defined behaviour rather than a
checked error. `i5` wraps mod 2⁵ exactly as `i32` wraps mod 2³².

```sysl
var small: byte = 250

print(small + 10)
```

```output
4
```

Overflow is therefore **not** a trap source. See [errors and traps](/reference/errors/) for what is.

### Storage is not `N / 8`

An integer's alignment rounds up to the smallest width the target names, and its stride rounds up to
that alignment. A `u12` occupies **two** bytes aligned to two; a `u96` occupies sixteen aligned to
sixteen. This is LLVM's rule, and it matters anywhere a width is stated rather than derived.

### Where the width stops

Up to 64 bits everything is a machine instruction. Past that the arithmetic is still native — the
back end expands a wide multiply or divide inline, with no runtime routine behind it — and decimal
rendering becomes the language's own job, since C's `printf` has no length modifier that wide. So a
value past 64 bits renders through a digit loop and is refused a `%d`.

The ceiling is LLVM's own **2²³ − 1**, and a wider width is a diagnostic naming it. That is a
statement about the toolchain, not about the design.

```sysl
var wide: u256 = 1
var i = 0

while i < 200 do
    wide = wide * 2u256
    i = i + 1

print(wide)
```

```output
1606938044258990275541962092341162602522202993782792835301376
```

Two costs are worth knowing before reaching for an extreme width, because neither is guarded against:
the digit buffer is stack space proportional to the width, so a width near the ceiling overflows the
frame, and sizing it evaluates 2^N at compile time. Nothing reaches either without asking for it by
name.

### The narrowest widths

`N ≥ 1` has no exception at its low end. `u1` is a single binary digit. `i1` holds `{-1, 0}` — one bit
of two's complement, where the only bit *is* the sign bit. It is degenerate but entirely consistent,
and nothing special-cases it: `abs` at `i1` answers `-1` because `-1` is that width's most negative
value, exactly as `abs` at any width answers its own minimum, and `signum` never returns `+1` because
no value of the type is positive. If you want a bit, write `u1` or `bool`.

## Floating point is a closed set

`fN` is **not** open — only the IEEE widths exist, because there is no meaningful `f37`. `f32` and
`f64` are the two the back end supports today.

Only the default width gets an alias, and it is deliberately named `real` rather than `float`, because
`float` means 32-bit to every C, C++, Rust and Java programmer and `real` promises nothing it does not
keep.

```sysl
var a: f32 = 1.5
var b: real = 2.25

print(a + 1.0, b / 2.0)
```

```output
2.5 1.125
```

A float renders in the shortest form that round-trips, so a value with no fractional part prints
without one.

## `usize` and `isize`

Pointer-width integers, and they are **distinct types** from every `uN` — not aliases for `u64`. That
is required by the target scope: aliasing `usize` to `u64` would be correct only if every target were
64-bit, and a 32-bit embedded target is squarely in scope.

A length, an index, and `sizeof` are all `usize`.

```sysl
var xs = [10, 20, 30]
var n: usize = xs.len

print(n, xs[1])
```

```output
3 20
```

## `bool`

`true` and `false`, and nothing coerces to it. `if x = 0` is a **type error** rather than a subtle
bug, because `if` requires a `bool` and an assignment yields what was assigned. There is no
integer-as-condition rule to memorize.

## `char`

One **Unicode scalar value** — a codepoint up to `0x10FFFF`, excluding the surrogate range. It is its
own type, aliased to neither `u8` nor `u32`.

Keeping it distinct is what lets "a character" and "a byte" stay different ideas, which a language
with a UTF-8 `string` has to do. Conversion to `u32` is total and written `u32(c)`; conversion *from*
`u32` is partial, and has both a checked form that traps and a fallible form that returns an `Option`.

## `unit` and `never`

`unit` is the type with exactly one value — what a function that returns nothing returns. It is a real
type, so it composes: a `Fn() -> unit` needs no special case in the callable machinery.

`never` is the type with **no** values, and it is what an expression that does not finish has. A call
to `exit` has type `never`, as does a `return` or a `break` considered as an expression. Because there
are no values of it, `never` is a subtype of everything — which is what lets a `match` arm that aborts
sit beside arms that produce an `int` without the arms disagreeing.

## `string`

A validated UTF-8 string, three words wide. It is immutable, indexing it by byte position gives
bytes, iterating it gives `char`s, and `+` concatenates but will not accept a non-string operand.
[Strings](/reference/strings/) is the full account — the representation, the validity guarantee, and
every form that makes new bytes — and the operations that live in the library are under
[`sysl.text`](/library/text/).

## Fixed arrays and slices

Two sequence types where many languages have one.

| type | what it is |
|---|---|
| `[N]T` | a **fixed array**: `N` elements, a value, no header, length known while compiling |
| `[]T` | a **slice**: a view of elements someone else owns — `{ owner, pointer, length }` |
| `[]const T` | a slice that may be read and not written |

An array **is** its elements, so copying one copies all of them and passing one by value passes the
whole thing. A slice **names** elements that live somewhere else, and copying a slice copies the
three-word header rather than the data.

```sysl
var a = [1, 2, 3]
var b = a

b[0] = 99

var v: []int = a[..]
v[1] = 88

print(a[0], a[1], b[0])
```

```output
1 88 99
```

The disagreement in that program is the whole distinction: writing through `b` did not touch `a`
because `b` is a copy, and writing through `v` did because `v` is a view.

Both carry a length, so **every index is checked**. [Arrays and slices](/reference/arrays/) is the
full account — writing one down, storage sized while running, the indexing and slicing rules, and
what a view keeps alive.

## Vectors

A fourth sequence shape, and the one that is not storage at all.

| type | what it is |
|---|---|
| `<N>T` | a **vector**: N lanes of `T` in a register, whose operators work on every lane at once |

It holds the same values an `[N]T` holds, in the same order — the two type constructors differ by one
bracket pair because a vector *is* an array that computes lane-wise:

```sysl
val a: <4>f32 = [1.0, 2.0, 3.0, 4.0]
val b: <4>f32 = [10.0, 20.0, 30.0, 40.0]

print((a + b)[3], (a * 2.0)[0], (a * b).sum())
```

```output
44 2 300
```

That `+` is one instruction doing four additions, and the `2.0` broadcasts into every lane. A lane is
read by a **constant** index, which is the one subscript in the language not checked while the program
runs — a register has no address to check against.

A machine with no vector unit is not a special case: the back end turns a vector into as many
registers as it needs, or into ordinary scalar operations, so `<4>f32` compiles everywhere sysl
compiles. [Vectors](/reference/vectors/) is the full account — masks and `select`, the reductions, and
writing one kernel that is compiled for more than one register width.

## Structs

A named product type. Fields are declared one per line, and a struct is a **value** — assigning one
copies it.

```sysl
struct Point
    x: int
    y: int
end Point

var p = Point(1, 2)
var q = p

q.x = 99

print(p.x, q.x)
```

```output
1 99
```

Structs may carry methods, an `invariant`, and a visibility modifier per field. A struct may also be
declared `opaque`, which withholds its layout from everyone outside its own module — a different axis
from visibility, covered under [modules](/tour/modules/).

## Enums

Two shapes under one keyword. A **simple** enum is a set of named discriminants with an underlying
integer type; a **data** enum gives variants payloads, making it a sum type.

```sysl
enum Color
    Red
    Green
    Blue

enum Shape
    Circle(r: real)
    Rect(w: real, h: real)

area(s: Shape) -> real = s match
    Circle(r)  -> 3.14159 * r * r
    Rect(w, h) -> w * h

print(int(Green), area(Circle(1.0)), area(Rect(2.0, 3.0)))
```

```output
1 3.14159 6
```

`Option[T]` and `Result[T, E]` are ordinary data enums declared in the standard library, with no
compiler privileges — which is why you can write your own and have it work identically.

### A variant belongs to its enum

Two enums in one module may each name a variant `Failed`, and neither has to be renamed. **What a
bare name means is settled where it is used, by the type expected there** — an argument, an annotated
binding, a `return` and a field all supply one, so the short form is what you normally write:

```sysl
enum Shape
    Circle(r: int)
    Square(side: int)

enum Hole
    Circle(r: int)
    Slot(len: int)

area(s: Shape) -> int = s match
    Circle(r)    -> 3 * r * r
    Square(side) -> side * side

depth(h: Hole) -> int = h match
    Circle(r) -> r
    Slot(len) -> len

val s: Shape = Circle(2)

print(area(s), depth(Circle(5)))
```

```output
12 5
```

Where two enums answer and nothing says which, that is a diagnostic rather than a quiet choice — a
construction that picked the first-declared enum would be a line whose meaning changed when somebody
added an unrelated enum above it:

```sysl
enum Shape
    Circle(r: int)
    Square(side: int)

enum Hole
    Circle(r: int)
    Slot(len: int)

var s = Circle(1)

print(1)
```

```error
'Circle' is a variant of 'Shape' and 'Hole', and nothing here says which — qualify it, as 'Shape.Circle'
```

`Shape.Circle(1)` is what that line wants. The qualified form works at a construction exactly as it
[works in a pattern](/reference/patterns/#the-bare-name-rule).

This is Rust's arrangement — a variant is namespaced under its enum — without Rust's use site, where
`Link::Failed` is required everywhere unless a scope opts into `use Link::*`. A variant still may not
share a name with a constant, a `val`, a module `var` or an `extern` variable: two variants of a name
are told apart by the enum they belong to, and a variant and a constant have nothing to be told apart
*by*.

## Type aliases

`type Name = Existing` introduces a second spelling for a type, interchangeable with the first. It
creates **no** new type and no checking: an alias is for shortening a name that has grown long, not
for distinguishing two uses of the same representation.

When you want a genuinely distinct type — one the compiler will not let you confuse with its base —
that is a **constrained type**, written with `new`, and it is covered under
[contracts](/tour/contracts/).

## Function types

A callable's type is written with `Fn`:

```sysl
apply(f: &Fn(int) -> int, x: int) -> int = f(x)

print(apply(n -> n * 3, 7))
```

```output
21
```

A named function used where a callable is expected is the capture-free case of the same thing — there
is no separate "function pointer" concept to learn. A raw C function pointer, for a foreign boundary,
is spelled `*extern(A) -> R` and is covered under the [foreign interface](/reference/ffi/).

### A parameter passed by name

A parameter written with the arrow and **nothing on its left** takes an expression the call does not
evaluate, and the body evaluates at each use:

```sysl
static var built: int = 0

message() -> int
    built += 1
    42

log(on: bool, m: -> int)
    if on then print(m)

log(false, message())
log(true, message())
print(built)
```

```output
42
1
```

`message()` ran once, not twice: the first call never evaluated its argument. That is the form's
whole purpose — an argument a callee may not want should cost nothing to offer.

**Each use is an evaluation**, because each use is a call — so a body that names the parameter twice
runs the argument twice. A body wanting one evaluation binds it to a `val` first.

It costs nothing at runtime. `x: -> T` has the type `Fn() -> T`, so it lowers to a bounded type
parameter exactly as the ordinary bare arrow does — one specialized copy per call site, called
directly, with no allocation.

**`x: () -> T` is the neighbouring form and keeps its meaning.** Same type, different call site:
there the caller constructs the callable and the body calls it.

```sysl
twice(f: () -> int) -> int = f() + f()

print(twice(() -> 21))
```

```output
42
```
