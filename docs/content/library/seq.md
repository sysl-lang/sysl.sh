---
title: The seq module
summary: "`sysl.seq` — `map`, `filter`, `fold` and the seven questions beside them, as one trait a slice and a `Buf` both answer."
weight: 56
---

`sysl.seq` is what a program asks *of* a sequence of values: transform every element, keep the ones
satisfying a predicate, carry a running value across the lot. It is a single trait, `Sequence[T]`,
and the library implements it twice — for a built-in slice and for a
[`Buf`](/library/buf/) — so the same ten names work on either.

```sysl
import sysl.seq.Sequence

val xs = [1, 2, 3, 4, 5, 6]

print(xs[..].filter(n -> n % 2 == 0).map(n -> n * n))
print(xs[..].fold(0, (a, n) -> a + n))
```

```output
[4, 16, 36]
21
```

**This is the surface [`sysl.slices`](/library/slices/) deliberately does not have.** That module asks
*where is this value* — `index_of`, `contains`, `min_index` — and answers every question by comparing
elements. This one asks *which of these satisfies this predicate*, and answers every question by
calling something the caller wrote. The two never overlap, and they are separate modules because they
have different costs: nothing in `sysl.slices` allocates, and everything here does.

## The trait

```sysl
trait Sequence[T]
    fold[A](self, init: A, f: &Fn(A, T) -> A) -> A
    any(self, p: &Fn(T) -> bool) -> bool
    all(self, p: &Fn(T) -> bool) -> bool
    find(self, p: &Fn(T) -> bool) -> Option[T]
    position(self, p: &Fn(T) -> bool) -> Option[usize]
    count_where(self, p: &Fn(T) -> bool) -> usize
    each(self, f: &Fn(T) -> unit)
    map[U](self, f: &Fn(T) -> U) -> []U
    filter(self, p: &Fn(T) -> bool) -> []T
    flat_map[U](self, f: &Fn(T) -> []U) -> []U
```

**`map` and `flat_map` declare a type parameter of their own**, which is a thing a trait's member may
do ([generics](/reference/generics/#a-member-declares-its-own)) and is what this module needed before
it could be written: a `map`'s result type is chosen at the call by what the closure returns, and by
nothing about the receiver. The cost is that those two members have no table slot, so they are
reached on a value whose type is known and not through a `&Sequence` — which is not a restriction
anything here runs into, since a slice and a `Buf` are what a program holds.

## Asking a question

`any` and `all` answer whether some element or every element satisfies a predicate, and both stop at
the first element that settles it. `find` answers the element, `position` answers where it is, and
`count_where` answers how many.

```sysl
import sysl.seq.Sequence

val xs = [3, 8, 2, 9, 4]

print(xs[..].any(n -> n > 8), xs[..].all(n -> n > 1))
print(xs[..].find(n -> n > 5).unwrap(), xs[..].position(n -> n > 5).unwrap())
print(xs[..].count_where(n -> n % 2 == 0))
```

```output
true true
8 1
3
```

**An empty sequence answers `true` to `all`**, which is the vacuous truth every language with this
operation agrees on: there is no element to be a counterexample.

**`find` and `position` are two questions rather than one.** A caller that wants to look at what it
found should not have to subscript for it, and a caller that wants to write through the sequence needs
the index and not a copy of the element.

**`count_where` and not `count`**, which is the name a reader reaches for first. A type's fields and
its members share one namespace, and `count` is the field four of the library's own containers hold
their length in — `Buf`, `Map`, `List` and `Deque` alike — so a member of that name could not be given
to any of them. The label is Swift's, and it says which of the two questions about a length this one
is.

## Folding

`fold` carries a running value across every element, in order, and is the operation the six above are
special cases of. The accumulator's type is the member's own rather than the sequence's, so summing a
slice of names into a length is as ordinary as summing numbers:

```sysl
import sysl.seq.Sequence

val xs = ["ab", "c", "def"]

print(xs[..].fold(0usize, (a, s) -> a + s.len))
```

```output
6
```

**The suffix on the seed is doing real work here and is not the noise it looks like.** The accumulator
type is read off `init` and off nothing else — an annotation on the binding does not reach it, so
`val total: usize = xs[..].fold(0, …)` is refused rather than being the place to say it once. That is
a gap in inference rather than a rule about folding, and it is filed; until it closes, the seed is
where a fold that accumulates at some other type says so.

## Visiting every element

`each` hands every element, in order, to something that returns nothing. **A `for` loop is what a
program should reach for**, and this does not replace it — what it is for is the tail of a chain,
where dropping out of expression position to write a loop is what breaks the line up.

```sysl
import sysl.seq.Sequence

val xs = [1, 2, 3, 4]

xs[..].filter(n -> n % 2 == 1).each(n -> print(n))
```

```output
1
3
```

**A closure captures by value**, so a record kept outside the call has to be storage rather than a
copy of one: a captured `Buf` is filled and thrown away, and a `&Buf` is the buffer itself.

```sysl
import sysl.seq.Sequence
import sysl.buf.{Buf, buf}

val xs = [1, 2, 3]
var seen: &Buf[int] = buf()

xs[..].each(n -> seen.push(n * 2))

print(seen.len(), seen[0], seen[2])
```

```output
3 2 6
```

## Building a new sequence

`map`, `filter` and `flat_map` each answer a **new slice**, eagerly — the elements are there when the
call returns, so each stage is complete — and paid for — before the next begins.

```sysl
import sysl.seq.Sequence
import sysl.buf.{Buf, buf}

var b: Buf[int] = buf()

b.push(2)
b.push(3)

print(b.map(n -> s"<${n}>"))
print(b.count_where(n -> n > 2))
```

```output
[<2>, <3>]
1
```

That is a `Buf` answering both, and it is the same implementation the slice has: a buffer's `view` is
its storage rather than a copy of it, so `Buf`'s half of the module is ten lines of delegation. What
it buys is that a program holding a buffer reads like one holding a slice, instead of carrying a
`.view()` at every call site that says nothing about what it is doing.

**`flat_map` turns each element into a sequence and lays them end to end.** The closure hands back
storage that has to outlive the call, which an array literal does not — a literal is a value in the
frame that built it:

```sysl
import sysl.seq.Sequence
import sysl.buf.{Buf, buf}

pair(n: int) -> []int
    var b: Buf[int] = buf()

    b.push(n)
    b.push(n * 10)

    b.view()

val xs = [1, 2, 3]

print(xs[..].flat_map(n -> pair(n)))
```

```output
[1, 10, 2, 20, 3, 30]
```

Writing the literal inline is refused, and the refusal is about the type rather than about lifetimes:
an array literal is an **array**, `[2]int`, and the member asks for a slice.

```sysl
import sysl.seq.Sequence

val xs = [1, 2]

print(xs[..].flat_map(n -> [n, n * 10]))
```

```error
it implements 'sysl.Fn1[int, [2]int]'
```

## The members are on a slice, not on an array

The `impl` covers `[]const E`, so an array reaches these members the way it reaches anything expecting
a slice — by being sliced, with `[..]`. That is one character and it is deliberate: an array is a
value, a slice is a view of one, and which of the two a `map` walks is the difference between copying
six words and not.

```sysl
import sysl.seq.Sequence

val xs = [1, 2, 3]

print(xs.map(n -> n * 2))
```

```error
type '[3]int' has no method 'map'
```

**Written for `[]const E` rather than `[]E`**, which is what makes it reachable on both: a `[]T` is
accepted wherever a `[]const T` is wanted, so a mutable slice finds these members too and a read-only
one is not handed a member that could write through it. Nothing here writes.

## What it costs

**Every member allocates, and it is the callable rather than the result that does it.** A parameter
written `&Fn(…)` is a counted box, so the closure a caller passes goes on the heap at the call — one
allocation per call, whichever member it is, before an element is touched. A `fold` over three
elements is one allocation and not none.

The spelling that would avoid it is not available to a trait. A parameter written with a bare arrow —
`f: T -> U` — becomes a bounded type parameter instead, monomorphized and called directly with nothing
boxed ([types](/reference/types/#function-types)), and that is what `sysl.slices`'s sorts take. The
desugaring that turns an arrow into a bound runs for a type's own members and for an `impl` block's,
and not for a trait's, so a trait can only ask for the boxed form. That gap is filed, and closing it
makes every member here allocation-free without changing a call site.

**Three of the ten allocate for a second reason**: `map`, `filter` and `flat_map` build a sequence to
hand back, and that cost is inherent. `map` is given the length its answer is known to have, so it
grows once rather than at every doubling; `filter` cannot know its own length in advance and does not
pretend to.

**So this module is not the one to reach for in an inner loop**, and the library does not use it in
one: a `for` over a slice allocates nothing and always will. What `sysl.seq` is for is the code where
a chain says what is happening and a loop would not.

## Eager, and what that leaves room for

`map` returns a slice rather than a description of one, and each stage of a chain is complete before
the next begins. **The lazy alternative** — an adapter that describes the work and is evaluated by
whatever finally walks it — needs a type a trait can name without spelling, which sysl does not have
yet; and a slice is not an [`Iterate`](/library/core/) to begin with, since a `for` walks one by
address.

Swift makes the same trade and opts into laziness explicitly, with the eager spelling as the default
one. That is the shape this leaves room for: a `.lazy` view is additive, and nothing on this page
changes when it arrives.
