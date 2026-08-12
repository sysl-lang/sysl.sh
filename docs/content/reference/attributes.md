---
title: Attributes, annotations, and compile time
summary: `::` attributes a type answers, the seven annotations a declaration takes, the four a file's header takes, `@assert` which stands on its own, and the `#if` directive that gates lines before the lexer sees them.
weight: 130
---

Three kinds of form in sysl reach into the *compilation* rather than into the running program. Each
has a name and a spelling of its own:

| written | is | read by |
|---|---|---|
| `T::Attr` | an **attribute** — a question a type's own name answers | the analyzer, at the use |
| `@test`, `@tailrec`, `@pure`, `@ghost`, `@export`, `@reads`, `@writes` | an **annotation** — a fact about the declaration under it | the grammar |
| `@no_alloc`, `@requires`, `@link`, `@include`, `@tests` | an **annotation** — a fact about the whole file, in its header | the grammar |
| `@assert` | an **annotation** that describes nothing but itself — a condition settled while compiling | the analyzer, once |
| `#if` | a **directive** — a gate on lines | a pass before the lexer |

**The last two are told apart by the sigil, and that is the whole rule.** An annotation is `@` and
belongs to what it is written above — a declaration, or the file itself; a directive is `#` and
gates lines. Nothing about
indentation is involved, which matters because a declaration at the margin — a `module` header — has
its annotation at the margin too, so a rule about columns would have had the two forms competing for
one position.

A directive is still written at column 1, and for its own reason: it is gone before anything counts a
column, so an indented one would look like it takes part in a block structure it has nothing to do
with. That is a rule about directives, not the thing that distinguishes them.

Annotations come in three groups, by what they attach to.

**On a function** there are seven, each written on its own line above the declaration. More than one
may be stacked, and writing the same one twice is refused. `@test` and `@tailrec` are below; `@pure`,
`@ghost`, `@reads` and `@writes` belong to the specification vocabulary and are on the
[verification](/reference/verification/) page; `@export` makes the definition C-callable and is on
the [FFI](/reference/ffi/) page, beside the `extern` it is the mirror image of.

**On the file** there are five, in its header directly below `module` and before everything else:
`@no_alloc` and its siblings, `@requires(...)`, `@link("...")`, `@include("...")`, and `@tests`. The
first two say what the whole module may do; `@link` says what its `extern`s need at the linker and
`@include` what its `c const` block needs at the C compiler; the last says the file is scaffolding
for the module's tests. All five attach to the file rather than to any declaration in it — and
writing one further down is refused with a message saying where it belongs. The first four are
covered under [modules](/reference/modules/) and [FFI](/reference/ffi/), where what they *mean* is;
`@tests` is below.

**On nothing at all** there is one: `@assert`, which stands where a declaration stands and describes
only itself. It attaches to nothing, declares no name, and nothing can refer to one — two saying the
same thing are two checks rather than a duplicate. It is below.

**An annotation's name is an ordinary identifier**, which is the point of writing these as
annotations at all: nothing here is reserved, so a program may still call something `test`, `link`,
`alloc`, `no` or `requires`. `guide/slab`'s allocator calls its central function `alloc` and threads
its free list through a field called `link`.

The `@` is also read **inside a pattern**, where it binds a name to what a sub-pattern matched
([patterns](/reference/patterns/)). The two never compete: an annotation's `@` is a prefix at the
start of a line above a declaration, and a pattern's is infix between a name and a pattern, in a
position no declaration may stand.

## `::` — what a type's own name answers

A type's name is a type and not a value, so nothing is *read* from it. What it answers are
**attributes**, written with `::` rather than `.` so they stay out of the member namespace:
`Color::First` cannot be confused with a variant, an associated function, or a member an `impl`
added, and no `impl` can shadow one by declaring a member of that name.

Three kinds of type answer them, and each set is **fixed and closed**.

### A simple enum

```sysl
enum Color: u8
    Red = 2
    Green = 5
    Blue = 9

print(Color::Image(Color::First), Color::Image(Color::Last))
print(Color::Pos(Green), Color::Image(Color::Val(2)))
print(Color::Image(Color::Succ(Red)), Color::Image(Color::Pred(Blue)))
print(Color::Image(Color::Value("Blue")), int(Color::Value("Blue")))
```

```output
Red Blue
1 Blue
Green Green
Blue 9
```

| written | is | traps |
|---|---|---|
| `T::First` / `T::Last` | the first and last variant | no |
| `T::Pos(v)` | a value's **0-based position** in the declaration | no |
| `T::Val(i)` | the value at position `i` | on a position past the last, or below zero |
| `T::Succ(v)` / `T::Pred(v)` | the neighbouring value | at the last / at the first |
| `T::Image(v)` | the variant's **name**, as a `string` | no |
| `T::Value(s)` | the value a name stands for | on a name no variant has |

**Position is not the discriminant, and that distinction is why `Pos` and `Val` exist.**
Discriminants may be explicit, non-contiguous and not zero-based — `Green` above is position 1 and
discriminant 5 — so an ordinal has to be looked up rather than computed. `Pos` and `Val` are the two
directions of that lookup, and `Succ`/`Pred` walk the **declaration order** rather than adding one.
Going the other way, to a value's discriminant, is the ordinary conversion `int(c)`.

**`Image` and `Value` are the printed-name pair, and they are what makes a simple enum
observable.** The type carries no `==` and no `Display` of its own, which is why every line above
turns a value into a position or a name to look at it. A comparison is written on the discriminants,
`int(a) == int(b)`.

### A constrained subtype

The other set — `First`, `Last`, `Valid`, `Succ`, `Pred`, `Range` — belongs to a `within`-ranged
integer subtype, and is on the [errors and contracts](/reference/errors/) page beside the checking it
is the question form of.

The two sets are spelled the same way for the same reason and overlap where the questions overlap,
but they are not one set: an enum has `Pos`, `Val`, `Image` and `Value` because its values are named
and unevenly spaced, and a subtype has `Valid` and `Range` because its values are a contiguous run of
integers.

### An integer type

The third set is two attributes wide — `Min` and `Max`, the extremes the type can hold. They belong
to every member of the `iN`/`uN` family, and to the named types that are members of it.

```sysl
print(u8::Min, u8::Max)
print(i8::Min, i8::Max)
print(int::Min, int::Max)
print(byte::Min, byte::Max)
```

```output
0 255
-128 127
-2147483648 2147483647
0 255
```

**The open family is why these exist rather than being written out.** A program can spell `4294967295`
for a `u32` and lose nothing; the largest `u10000` is 3,011 digits and cannot practically be written
at all, so for a wide member the attribute is the only way to name a value the type obviously has.
It is also why they cannot be a library table the way C's `UINT8_MAX` is — there is no finite set of
integer types to tabulate.

```sysl
print(u3::Min, u3::Max)
print(i5::Min, i5::Max)
print(u64::Max)
```

```output
0 7
-16 15
18446744073709551615
```

**They are constants taking no argument, so they fold.** That is what puts them where no call is
admitted — a `const` initializer, an `@assert` condition, an array bound:

```sysl
const LIMIT: u16 = u16::Max
const HALF: u32 = u32::Max / 2

print(LIMIT, HALF)
```

```output
65535 2147483647
```

**`usize` answers only a floor across targets.** Its width is the pointer's, so `usize::Min` is 0
everywhere and `usize::Max` is whatever the target makes it — a program that prints the latter is
reading a fact about the machine rather than about the language.

**`Min`/`Max` are not `First`/`Last` renamed**, and asking for the wrong pair is answered by name
rather than by a general refusal:

```sysl
print(u8::First)
```

```error
'u8' is an integer, not a declared sequence — its extremes are 'u8::Min' and 'u8::Max'. 'First' and 'Last' name the ends of an enum's variants or of a 'within' range, which is a different question
```

`First` and `Last` name the ends of a *declared sequence* — an enum's variants, a written range —
and an enum's discriminants may be explicit and non-contiguous, so its first-declared variant need
not carry the smallest value. The two questions coincide on an integer and only there. **A
`within`-ranged subtype answers both**, since there they genuinely agree, and a reader who learned
`Min` on `u32` should not find it renamed on a subtype of `u32`.

Only integers have them: `f64::Max` and `bool::Max` are refused, the float because its extremes are
`sysl.math`'s business and the boolean because it is not that kind of type.

### What has no attributes

**A data enum**, because its value is a variant plus a payload, so a position, a name and a
neighbour are each questions about only half of it:

```sysl
enum Shape
    Circle(r: int)
    Rect(w: int, h: int)

print(Shape::First)
```

```error
'Shape::First' needs a simple enum, and 'Shape' carries data
```

**A generic enum**, because its name stands for no one value set until it is applied:

```sysl
enum Box[T]
    Full(v: T)
    Empty

print(Box::First)
```

```error
'Box' is generic, so 'Box::First' has no single enum to read
```

And an attribute outside the set is answered by name, since the set is closed:

```sysl
enum Color: u8
    Red
    Green

print(Color::Nonesuch)
```

```error
'Color' has no attribute 'Nonesuch'
```

## `@test` — a function with a caller nothing else has

A word after `@` that is neither `test` nor `tailrec` is answered by name rather than as grammar. The
set is closed, which is what makes a misspelling an error instead of a marker that quietly does
nothing — annotations are deliberately *not* a general mechanism yet.

The annotation goes on its own line above an ordinary function declaration, which may still be
`private`:

```sysl
add(a: int, b: int) -> int = a + b

@test
adds_two()
    assert(add(1, 1) == 2, "one and one")

@test("an empty slice has no first element")
private first_of_empty()
    assert(true, "nothing to see")

print(add(2, 3))
```

```output
5
```

That program prints `5` and runs neither test, which is the whole of the arrangement: `sysl run`
builds the program, and the tests are for `sysl test`.

Four forms of the annotation:

| written | means |
|---|---|
| `@test` | an ordinary test, named after the function |
| `@test("a sentence")` | named by the sentence instead |
| `@test(should_trap)` | the test **passes** by stopping the program |
| `@test(should_trap: "past the end")` | …and the run must have printed that text |

### What a test may be

**A test is an ordinary function with a caller nothing else has**: no parameters, no result, not
generic. All three are the same requirement from different sides, since the runner calls it with
nothing and reads the answer off whether it returned. They are checked **at the annotation**, because
the function is a perfectly good function and it is `@test` that made a promise about it:

```sysl
@test
takes_one(n: int)
    assert(n > 0, "positive")
```

```error
a '@test' function takes no parameters, and 'takes_one' takes one — 'sysl test' calls it with nothing, so there is nowhere for an argument to come from
```

```sysl
@test
generic_one[T]()
    assert(true, "yes")
```

```error
a '@test' function has no type parameters, and 'generic_one' declares 'T' — a generic is compiled for the arguments a caller fixes, and the runner supplies none
```

```sysl
@test
gives_back() -> int = 1
```

```error
a '@test' function returns nothing, and 'gives_back' returns 'int' — a test's result is whether it came back, so there is nothing to read a value with
```

### A test passes by returning

That is the whole protocol, and it is what lets a test assert **in the language it is testing**
rather than in a framework. A broken `require`, a bounds violation, an `unwrap` of a `None` — each
ends the process, and none of them had to know it was running under a test.

`should_trap` inverts the reading, for a test whose subject *is* the check. With a string it
additionally requires that the run printed it, which is what tells a trap from the **right** trap. A
silent trap satisfies `should_trap` and can satisfy no string, because a compiler-inserted check
raises a signal and says nothing — see [what stopping looks like](/reference/errors/).

### A test has one caller, and the program is not it

```sysl
run_it()
    adds_two()

@test
adds_two()
    assert(true, "yes")
```

```error
'adds_two' is a '@test' function, which 'sysl test' calls and nothing else does — every other build leaves it out, so this call would have no definition to reach. Work two tests share belongs in an ordinary function they both call
```

### What is dropped, and when

`sysl run`, `sysl build` and `sysl emit-llvm` drop the tests — and drop them **after** analysis. So a
`@test` that does not compile is an error in a build that would never have run it, and a module's
capability clause reaches its tests like any other member.

That ordering is what lets a test sit beside what it tests: a program's tests do not run when it
runs, and they do not stop being checked.

A helper only a test calls leaves with it, because it becomes unreachable and pruning notices; a
helper the program also calls stays, because the program still calls it.

What a test build keeps regardless is the three definitions **nothing in the program names**: an
interrupt handler, an [`@export`](/reference/ffi/), and a [destructor](/reference/memory/). Each is
entered from somewhere no walk over the program can see — the processor, a caller outside this
compilation, and the release hook generated from a payload type — so each is a root wherever the walk
begins. A test build swaps the roots, putting the tests where the entry point was, and these three
are roots there too. They have to be: leaving them out would not make them reachable from the tests
instead, it would make them reachable from nothing.

That matters most to a package, whose only build is its own suite. A test build dropping a destructor
could not link at all, the release hook being left to call a symbol nothing defined; one dropping an
export would link, pass, and quietly go without the surface the package exists to provide.

**`sysl build-lib` is the exception, and drops them *before* analysis.** An artifact is the one
output that outlives the compilation that made it, and analysis is not a passive reading: a test
naming `Buf[int]` **creates** the whole of `Buf` at `int`, and that instantiation is an ordinary
library function afterwards, with nothing in it recording which declaration asked for it. Dropping
the test from the analyzed program would therefore drop the test and keep everything it caused —
shipping instantiations no caller of the library ever asked for, and making the artifact's contents
a fact about its tests.

**The line falls between parsing and analysis.** Every source is parsed before the drop, so a
**syntax** error in a `@tests` file still stops a `build-lib`. What such a file no longer gets is
everything after the parse — name resolution, types, visibility, capabilities, the `@test`
well-formedness rules above, generic instantiation. So a library test that is well-formed text and
wrong in every other way builds clean, and its real errors are reported by
[`sysl test --std`](/getting-started/cli/), which is where a library's tests are run.

### `@tests` — a file of scaffolding

Pruning answers for a **program**. It does not answer for a **library**, which has no `main` to lower
outwards from, so every public declaration is a potential entry and all of them are emitted. A helper
only a test called would ride into the artifact and be advertised out of it, nameable by everything
that links the library. Nothing about the declaration says it is scaffolding, and nothing could — it
is an ordinary function, which is the point of it.

So the **file** says it, in its header, beside the capability clauses:

```sysl
@tests

fixture() -> int = 6 * 7

@test("the fixture is the answer")
the_fixture_holds() =
    assert_eq(fixture(), 42)
```

It is `@tests` and not `@test` because the two say different things: `@test` names something the
runner calls, and this names something no build but the runner's keeps. One word for both would read
as though the file were itself a test.

**Two rules, and either alone would be unsound.** Every build but `sysl test` drops everything such a
file declares — and nothing outside a test may name any of it. Without the second, a program that
called a helper would compile here and fail at the *link*, with a message about a missing symbol
rather than about the line that named it.

The restriction is stated over the **referring declaration**, not over the file it sits in. So a
`@test` function may name scaffolding wherever it was written — which is what keeps a test able to
sit beside what it tests — and another `@tests` file may, and nothing else may:

```sysl
@tests

fixture() -> int = 6 * 7

print(fixture())
```

```error
error: 'fixture' is declared in a file that said '@tests', so it is there for the module's tests and no build but 'sysl test' keeps it — only another such file, or a '@test' function, may name it
```

**A closure counts as the body it was written in.** It is lowered to a function of its own under a
name nobody wrote, so on its own terms it belongs to no file — but what decides is where it sits, not
what it ends up called. A lambda inside a test may name the test file's own scaffolding, and so may a
bare function name, which is the same thing written shorter:

```sysl
@tests

fixture() -> int = 6 * 7

apply(f: &Fn(int) -> int, n: int) -> int = f(n)

@test("the fixture is the answer, reached through a callback")
the_fixture_holds() =
    assert_eq(apply(v -> fixture() + v, 0), 42)
```

It goes when the file goes, too, which is the half that makes the naming safe: the program that ships
carries no body for it.

**An `impl` block may not sit in one.** It declares no name; it fills a slot in a method table, which
the rest of the program reads without naming anything. Kept in a test build and dropped everywhere
else, it would mean a trait answering one way while the tests ran and another way in the program that
ships. The impl belongs beside the type. A closure is the one exception the compiler makes for
itself, and it is not a hole: the table it writes is dropped with the closure.

**It stops at the package boundary.** A package is compiled from source and a library arrives as an
artifact whose test files were never encoded, so a rule that let the reference cross would compile
against one and fail against the other. A test-support library *meant* to be imported is therefore
ordinary code that ships, and is a different thing from a file of scaffolding inside a package.

### The runner

```bash
sysl test <path>
sysl test <path> --filter <text>
sysl test <path> --fail-fast
```

**One build, one process per test.** The tree is compiled once, into a binary whose entry point takes
a test's name and runs that test alone — the program's own statements and its `main` are not run,
though its module-level `val`s are still filled, since a test reads a module's storage like any other
function. The runner then starts that binary once per test.

The process per test is not a cost being tolerated; **it is the mechanism.** A test that fails does
so by ending its process, so a run that shared one would report the first failure and nothing after
it. The compile is the slow half, and there is only ever one of it.

Exit status is 0 if and only if every test that ran passed. A tree with no tests, and a filter that
matched none of the tests there are, both exit 0 and say which happened.

### `assert` and `panic`

Both are ordinary functions in the standard module:

```sysl
panic(msg: string) -> never
    print("panic:", msg)
    exit(1)

assert(cond: bool, msg: string)
    if !cond then panic(msg)
```

They exist because `require` is a promise about a **call**, checked on entry, and a test's fifth
statement has no contract to hang a claim on — the contract was about the arguments, four statements
ago.

They stop the program the way `unwrap` does — a line naming what happened, then the hosted exit —
rather than through the trap instruction, because **a check a program makes is one the compiler
cannot see, and the message is the whole point of it.** The message is required rather than
defaulted, because the condition's source is not available to print and a failure saying only
"assertion failed" sends its reader looking for which one.

## `@assert` — a condition settled while compiling

```sysl
const capacity: usize = 512

@assert(capacity == 512, "the protocol fixes this")

print(capacity)
```

```output
512
```

The condition is a [constant expression](/reference/modules/), folded by the machinery
a `const` initializer already goes through — so it may name constants, `sizeof`, `alignof`,
`offsetof`, and the arithmetic and comparisons over them, including a constant declared *below* it. A true one emits
nothing at all. A false one stops the compilation, quoting the message:

```sysl
@assert(1 == 2, "arithmetic broke")

print(1)
```

```error
assertion failed: arithmetic broke
```

The message is optional and is the reader's own, because they know what the number *means* — that a
struct matches its C counterpart, that a table is the size a protocol fixes — where the expression
alone says only that two numbers differ.

**It is not `require`.** A `require` is a runtime precondition: it is compiled, it branches, and it
traps when the program reaches it. This is settled while compiling and reaches the binary as nothing.
A condition it cannot settle is refused rather than deferred — and a call is the line, since a call
in a constant expression would be a request for compile-time evaluation of arbitrary code:

```sysl
f() -> int = 3

@assert(f() == 3)

print(1)
```

```error
has to be a constant expression
```

### Checking a C struct's layout

This is what it was built for. sysl lays a struct out in declaration order and is C-compatible by
construction, but from inside sysl that claim cannot be checked: `sizeof` reports what *sysl* laid
out, not what the header says, so comparing the two is a tautology.

Where the C side is `__attribute__((packed))` or was declared at a boundary, `@packed` and
`@align(n)` further down this page are how the sysl side says the same thing — and the check below is
what proves the two agree rather than merely look similar.

It stops being one when both sides name the same number. The C half goes in a `.c` beside the sysl,
which [is compiled with it](/reference/ffi/) and for the same target, so it reads the headers of the
machine being built for:

```c
#include <stddef.h>
_Static_assert(sizeof(struct pair) == 8, "struct pair size moved");
_Static_assert(offsetof(struct pair, b) == 4, "struct pair.b moved");
```

and the sysl half pins what sysl laid out:

```sysl
struct Pair
    a: i32
    b: i32
end Pair

@assert(sizeof(Pair) == 8, "Pair must match struct pair")
@assert(offsetof(Pair, b) == 4, "struct pair.b moved")

print(sizeof(Pair), offsetof(Pair, b))
```

```output
8 4
```

Neither half finds the other's mistake, which is why both are written: the `.c` catches a header that
moved, and the `@assert` catches a struct that was transcribed wrong.

**Pin every field you read, not only the total.** A size catches a field that changed width and one
that was added. It says nothing about **order** — so two same-width fields transposed in the mirror
leave the total unchanged, and the size assertion on *both* sides passes while every read is off by
the distance between them:

```sysl
struct Pair
    b: i32
    a: i32
end Pair

@assert(sizeof(Pair) == 8, "Pair must match struct pair")
@assert(offsetof(Pair, b) == 4, "struct pair.b moved")

print(1)
```

```error
assertion failed: struct pair.b moved
```

That is the failure the whole pairing exists to prevent, arriving through the half that was not
checked. `offsetof(T, field)` closes it: it takes a type and a field **name**, and answers where the
field starts in bytes. A field the struct does not have is refused by name rather than reported as a
condition that would not fold.

### Inside a generic, where it is settled once per instantiation

A generic's interesting facts are not properties of its declaration — they are properties of each set
of arguments it is compiled for. `sizeof(T)` has no width until something chooses a `T`, so an
assertion about one is settled at every instantiation rather than once:

```sysl
slab[T](x: T) -> usize
    @assert(sizeof(T) >= sizeof(*u8), "a free block has to hold the link through it")
    sizeof(T)

print(slab(1u64))
```

```output
8
```

An argument the claim does not hold for stops the compilation, and the report names which one asked —
the mistake is at the call that chose the type, while the sentence explaining why is at the
declaration, so a message carrying only one of the two sends its reader to the wrong file:

```sysl
slab[T](x: T) -> usize
    @assert(sizeof(T) >= sizeof(*u8), "a free block has to hold the link through it")
    sizeof(T)

print(slab(1u8))
```

```error
assertion failed: a free block has to hold the link through it — where T = byte
```

**This is the check `require` cannot make.** A precondition over `sizeof(T)` written as a `require` is
a runtime branch for a fact that was settled at the call: a container instantiated at a type too
narrow compiles, ships, and traps the first time anybody uses it. `guide/slab` was written that way
before this and is the worked example of the difference.

A **value** parameter is bound the same way and so is checked the same way, which is what lets a
generic hold its caller to a bound on a length:

```sysl
scratch[const N: usize](xs: [N]int) -> usize
    @assert(N <= 4, "the scratch buffer is a stack array")
    N

var small: [2]int = [1, 2]

print(scratch(small))
```

```output
2
```

**A generic nothing instantiates is not checked**, and that is the same deferral an array bound
already lives under: `[sizeof(T)]u8` is a well-formed length nobody can name until a `T` is chosen,
and the width is not wrong there — it is not being measured yet. The claim is settled at the first
call, which is the first moment there is anything to settle. A condition that could *never* fold is
still refused inside a generic, exactly as it is outside one.

## `@tailrec` — an assertion that the frame is reused

A function whose last act is a call to itself compiles to a branch back to its own entry rather than
a second frame, so the recursion is bounded by the arithmetic and not by the stack. That happens
whether or not anything is written — see
[tail calls](/reference/declarations/#tail-calls) for what counts as the last act, and for the two
things that end a tail position.

`@tailrec` asserts the jump is there:

```sysl
@tailrec
count(n: int, acc: int) -> int =
    if n == 0 then acc else count(n - 1, acc + 1)

print(count(100000, 0))
```

```output
100000
```

What it buys is the refusal. The optimization is silent, so an edit that costs it is silent too —
until the day the recursion is deep enough to matter:

```sysl
@tailrec
count(n: int, acc: int) -> int =
    if n == 0 then acc else 1 + count(n - 1, acc)

print(count(5, 0))
```

```error
calls itself nowhere the jump can replace
```

It changes nothing about what is emitted. Write it where losing the jump silently would be a bug,
and leave it off everywhere else.

## `@packed` and `@align(n)` — where a struct's fields sit

By default a struct pads: each field begins on its own alignment, and the aggregate takes the widest
of them. That is the layout a program wants unless something outside the program has an opinion.

```sysl
struct Head
    tag: u8
    len: u32

print(sizeof(Head), alignof(Head))
```

```output
8 4
```

Three bytes of that eight are a gap in front of `len`. **Two attributes move a struct off the
default, and they are separate axes** — one removes the padding *between* fields, the other raises
where the aggregate *begins*.

### `@packed` — no interior padding

```sysl
@packed
struct Head
    tag: u8
    len: u32

print(sizeof(Head), alignof(Head), sizeof([4]Head))
```

```output
5 1 20
```

The fields are laid end to end, the aggregate needs no alignment of its own, and an array of them has
no gap between elements either. A register block, and a C struct that has to match one, are what it
is for.

The fields still read and write their own values — packing changes where they sit, not what they are:

```sysl
@packed
struct Head
    tag: u8
    len: u32

var h = Head(7, 1000)

h.len += 1

print(h.tag, h.len)
```

```output
7 1001
```

**A packed field has no address.** `&s.f` is refused, and so is any `*T` into one:

```sysl
@packed
struct Head
    tag: u8
    len: u32

var h = Head(1, 2)
val p = &h.len

print(1)
```

```error
'&' here makes a '*uint' into Head, which is '@packed' — its fields sit at their declared offsets, so this one need not be on a 'uint' boundary, and every use of a '*uint' is entitled to assume that it is. Read or write the field through the struct, which is where the offset is known, or take the address of the Head itself
```

The field sits at its declared offset, which is very often *not* a multiple of its own alignment —
that is the point of the attribute — while a `*u32` is a `*u32` wherever it came from, and every use
of one is entitled to assume the address is aligned. Only the escaped address loses that, and it
would lose it arbitrarily far from the `&` that made it. **The struct's own address is untouched**,
being an ordinary pointer to an ordinary aggregate.

### `@align(n)` — where the aggregate begins

```sysl
@align(64)
struct Head
    tag: u8
    len: u32

print(sizeof(Head), alignof(Head))
```

```output
64 64
```

The size rounds up as well as the start, so an array keeps every element on the boundary. **It may
only raise**: asking for less than the fields already need changes nothing, since lowering is what
`@packed` is for and a type that under-promised would be unsound to pass around.

**The bound is folded rather than lexed**, so a program writes the name it already has for the number,
and arithmetic over one works:

```sysl
const CACHE_LINE: int = 64

@align(CACHE_LINE)
struct Head
    tag: u8
    len: u32

print(alignof(Head))
```

```output
64
```

It must be a power of two — an address is aligned by having low bits clear, so a boundary of six is
unsatisfiable rather than merely weak:

```sysl
@align(6)
struct S
    a: int
```

```error
'@align(6)' on 'S' is not an alignment — a boundary is a power of two, since an address is aligned by having low bits clear
```

### They compose

The gaps *between* fields and the boundary the whole thing *starts* on are different questions, so a
wire header that has to live in a DMA-capable buffer is both at once. The order they are written in
does not matter.

```sysl
@packed
@align(16)
struct Head
    tag: u8
    len: u32

print(sizeof(Head), alignof(Head))
```

```output
16 16
```

**Sub-byte fields are not part of this.** An `iN` field occupies its allocated width wherever it sits,
packed or not, so a hardware register's five-bit field is still shifts and masks. That is the bitfield
question and it is open on its own.

### `@align(n)` on a binding

The boundary may also be written on the storage itself — a `var` or a `val`, at the top of a module
or inside a function, and on the `static` spelling of either.

```sysl
@align(64)
var region: [128]u8

region[0] = 7u8
print(region[0], region.len)
```

```output
7 128
```

It says nothing the struct form could not. A boundary put on a type travels with every value of it,
and a named aligned type is reusable where a repeated attribute is not — so reach for the type when
more than one thing needs the boundary. What this spelling saves is at the *use* site: a buffer
wrapped in a struct is read as `region.bytes[i]` rather than `region[i]`.

**`@packed` has no meaning here**, and says so, because it describes the arrangement of fields inside
an aggregate and a binding has none:

```sysl
@packed
var n: int = 1

print(n)
```

```error
'@packed' describes how a struct's fields are laid out, so it can only mark a struct — a 'var' or a 'val' has no fields to pack, and '@align(n)' is the one of the two that may stand above one
```

A binding that names **several** things is refused for a related reason: there is no one object for a
boundary to be about.

```sysl
@align(16)
var a, b = 1, 2

print(a, b)
```

```error
'@align(n)' is the boundary one object's storage begins on, and a binding that names several has no one object for it to be about — declare them on lines of their own
```

The bound is held to the same rule a struct's is — a power of two, folded rather than lexed, so a
`const` or arithmetic over one is what a program writes.

## `#if` — gating lines before the lexer

Everything a target decides is a fact the *compiler* reads about the machine. `#if` is the one place
a **program** reads one, and it exists because machines genuinely differ in ways a library cannot
paper over: a syscall number, a struct a header lays out two ways, a symbol one libc exports and the
other does not.

```sysl
#if posix
line_ending() -> string = "\n"
#else
line_ending() -> string = "\r\n"
#endif

print("a posix machine", line_ending().len)
```

```output
a posix machine 1
```

**What differs between the branches is the implementation, not the answer.** That is the shape most
uses of `#if` have: a syscall number, a struct a header lays out two ways, a symbol one libc exports
and the other does not — chosen per machine so that everything above the choice can stop caring.

`#elif` chains, and `#else` catches what nothing named:

```sysl
#if aarch64
machine() -> string = "aarch64"
#elif x86_64
machine() -> string = "x86_64"
#elif riscv64
machine() -> string = "riscv64"
#else
machine() -> string = "something else"
#endif
```

That one is quoted rather than run, because what it prints is *supposed* to depend on the machine —
which is the whole point of the construct and exactly what a page cannot pin to one answer.

`#if` / `#elif` / `#else` / `#endif`, nesting freely, and **the branches are exclusive** — the first
whose condition holds is the one that contributes, and a group inside a branch that was not taken
contributes nothing however its own condition reads.

### It gates lines, and it gates them before anything is parsed

**A line in a branch this build is not for is replaced by an empty line, not removed**, and so is
every directive line. After the pass the file is an ordinary sysl file that happens to have some
blank lines in it, and nothing downstream knows any of this happened.

Replaced rather than removed, because **every line below a gate has to keep the number it was written
at.** Deleting them would leave the messages right and the carets somewhere else, with nothing to say
so:

```sysl
#if linux
say() -> string = "one"
extra() -> int = 1
another() -> int = 2
#endif

print(missing_name)
```

```error
:7:7
```

The caret is on line 7, which is where `print(missing_name)` is written — not line 2, where it would
be if the four gated lines had been dropped.

**A directive sits at the margin, column 1.** That is a rule and not a convention. sysl is
indentation-sensitive, and indentation is how the language reads block structure — so a gate written
*in* that channel would look like it takes part in a nesting it has nothing to do with, when in fact
the line is gone before anything counts a column. At the margin it is visibly not part of the code's
shape, which is what it is. It is also how C is written. What keeps a declaration's `@test` from ever
being mistaken for one of these is the **sigil**, not the margin — an annotation on a `module` header
sits at column 1 too.

**Why lines and not a construct wrapping declarations.** Rust spells this `#[cfg]`, an attribute on
an item, and can because Rust is brace-delimited: the attribute attaches without moving anything.
Here the equivalent would have to take an indented block, so adding or removing a platform gate would
reindent everything inside it — a one-line intent showing up as a whole-body diff. A flat marker
disturbs nothing.

### The symbols are derived from the target, and the set is closed

| kind | symbols |
|---|---|
| operating system | `macos`, `linux`, `windows`, `freestanding` |
| processor | `aarch64`, `x86_64`, `riscv64`, `riscv32`, `thumb`, `x86` |
| derived | `hosted` (not `freestanding`), `posix` (`macos` or `linux`) |

That is the whole vocabulary. There is **no `#define`**, nothing a project can add, and no dependence
on a project config. A condition is a symbol, `!`, `&&`, `||`, and parentheses; `&&` binds tighter
than `||`.

`posix` is a name for the commonest disjunction rather than a replacement for writing it — `#if linux
|| macos` still says the same thing. Note that this `posix` is not the *capability* of the same name:
this one asks **is this a POSIX system**, a fact about the machine settled by the target, where the
capability asks **may this module use POSIX**, a permission a project grants and a `no posix` clause
takes away. They agree today only because nothing denies anything yet.

**A symbol nobody knows is an error, not false:**

```sysl
#if darwin
say() -> string = "one"
#else
say() -> string = "two"
#endif

print(say())
```

```error
'darwin' is not something a target says about itself — sysl knows aarch64, freestanding, hosted, linux, macos, posix, riscv32, riscv64, thumb, windows, x86, x86_64
```

The set is closed, so a name outside it is a mistake rather than a fact this build happens not to
have — and a misspelling that read as *false* would gate code out of the build with nothing said.
**Silently missing code is the one failure this feature cannot be allowed to have, and it is the one
C has.**

A target's *name* is not a symbol — it has a `-` in it, which no identifier carries — and writing one
is told what to write instead, because otherwise the reader is told that `-` is not an operator,
which is true and no help:

```sysl
#if aarch64-macos
say() -> string = "one"
#else
say() -> string = "two"
#endif

print(say())
```

```error
'aarch64-macos' is a target's name rather than something a condition asks about — a condition asks about one fact of the machine at a time, so this is written 'aarch64 && macos'
```

**Every condition is checked, in the branch being taken and the ones being skipped alike.** So a
misspelling in the Linux half is caught by a macOS build, which is where it would otherwise sit until
somebody built for Linux:

```sysl
#if linux
say() -> string = "one"
#elif nosuchmachine
say() -> string = "two"
#else
say() -> string = "three"
#endif

print(say())
```

```error
'nosuchmachine' is not something a target says about itself
```

That refusal comes from a build whose `#if linux` was already false and whose `#else` is the branch
being taken. The condition on the way past was still read.

### What is given up, and what is not

**The inactive branch is never syntax-checked.** That is the price of gating text rather than trees,
it is C's price too, and a Linux branch can therefore rot while the macOS build stays green. What
finds that is a build for each target — a thing to *run*, not a thing to design around. The
conditions themselves are the part that is checked everywhere, and they are the part where a mistake
would otherwise be silent.

**The gate runs before anything knows what a string or a comment is**, so a line that begins at the
margin with a directive word is a directive even inside a text block or a block comment. Recognizing
those would mean a second copy of the lexer's rules about literals, in a place where the two could
drift with nothing to notice — a worse defect than this one. The margin rule is what keeps it rare: a
text block written anywhere but the top level is indented in the source, whatever its value turns out
to be.

### The library is subject to it too

`library/sysl` is sysl source, so it may gate on the machine like any other — which makes "the standard
module" a question with a target in it, and the library's source is parsed once per target
accordingly.

The one thing held fixed is that **a name the compiler spells for itself is declared on every
target**. A library that gated `Option` away for Windows would be a library nothing compiles against
there, so that is refused in a registry-wide check rather than at the first `?` somebody writes.

And **an artifact records the target it was built for** and is refused by a build for another,
because the trees a library ships are now a per-target answer.

## What is deliberately absent

| absent | why |
|---|---|
| a general annotation mechanism | the set is closed: `@test`, `@tailrec`, `@pure`, `@ghost` and `@export` on a declaration, `@packed` and `@align(n)` on a struct, `@no_<capability>`, `@requires`, `@link` and `@tests` on a file. Each was designed and added on its own evidence; there is no way to write one the compiler does not already know |
| a sub-byte field | `@packed` removes the padding *between* fields and not the width *of* one, so an `iN` field occupies its allocated width wherever it sits and a five-bit hardware register is still shifts and masks. That is the bitfield question, and it is open on its own |
| `#define`, or any project-supplied symbol | the `#if` vocabulary is derived from the target and closed, which is what makes an unknown symbol an error rather than a false |
| a `#if` that asks about a capability | a condition asks what the *target* says; what a project permits is a different question, left with the config that would define it |
| a test framework in the library | a test asserts in the language it is testing, and passes by returning |
| a release mode that drops a check | see [errors and contracts](/reference/errors/) — every check is in every build |

---

That is the language. What ships beside it is the [standard library](/library/), which is a section
of its own — nothing in it is a language feature, and every type in it is one a program could have
written.
