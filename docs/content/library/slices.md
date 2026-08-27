---
title: The slices module
summary: "`sysl.slices` — the operations over a built-in slice: searching, comparing, reversing, two sorts that neither of them allocates, a binary search that answers where a missing value belongs, and the pointer a C binding hands across."
weight: 55
---

**Every declaration in `sysl.slices`, with its signature:** [the generated API page](/api/sysl-slices/#index). This page is the argument — what the module is for, and how its pieces fit; that one is the list.

`sysl.slices` is what a program does *to* a `[]T` once it has one. Everything here is a **free
function** rather than a member of anything, which is the same call [`sysl.math`](/library/math/)
made for `min`: a built-in slice is indexed by the compiler walking to an address, so none of this
goes through a trait, and what each function needs is written in its own bound.

**Nothing here allocates and nothing here needs a capability.** That is deliberate and it is load
bearing — this is the module a C binding reaches into, and it is reachable from a target with no
allocator and no operating system. **The questions answered by calling something the caller wrote —
`map`, `filter`, `fold` — are [`sysl.seq`](/library/seq/) for exactly that reason**: every one of
them allocates, so they are a module a freestanding program can leave out.

```sysl
import sysl.slices.{sort, is_sorted, binary_search}

var xs = [5, 3, 8, 1, 9, 2]

sort(xs)

print(xs)
print(is_sorted(xs))
```

```output
[1, 2, 3, 5, 8, 9]
true
```

## Looking things up

`index_of` and `last_index_of` answer an `Option[usize]` rather than a sentinel — a library answering
`-1` hands back a number that indexes when it should not, and `usize` has no negative to spare in any
case.

```sysl
import sysl.slices.{index_of, last_index_of, contains, min_index, max_index}

var xs = [1, 2, 3, 2, 5]

print(index_of(xs, 2).unwrap(), last_index_of(xs, 2).unwrap())
print(contains(xs, 5), contains(xs, 4))
print(min_index(xs).unwrap(), max_index(xs).unwrap())
```

```output
1 3
true false
0 4
```

The extremes answer an **index** rather than a value, because the index answers both questions: an
element is one subscript away, and a caller that wanted to *modify* the extreme element could not have
got there from a copy. Ties go to the first, which is the choice `min` makes and for the same reason.

## Comparing and rearranging

```sysl
import sysl.slices.{equal, starts_with, ends_with, reverse, fill, swap}

var a = [1, 2, 3, 4]

print(equal(a, [1, 2, 3, 4]))
print(starts_with(a, [1, 2]), ends_with(a, [3, 4]))

reverse(a)
print(a)

swap(a, 0, 3)
print(a)

fill(a, 7)
print(a)
```

```output
true
true true
[4, 3, 2, 1]
[1, 3, 2, 4]
[7, 7, 7, 7]
```

**`equal` is not the only way to ask that any more.** A slice is
[`Eq` when its elements are](/library/core/#a-slice-or-an-array-of-anything-equatable), so `a == b`
answers the same question, and `assert_eq` takes two slices and prints both when they disagree.

What `equal` still has is its **signature and its module**: it takes `[]const T` and it lives here,
under `@no_alloc`, reachable from a freestanding target — so a caller holding two slices and wanting
a `bool` loses nothing by keeping it, and a binding that must not acquire an allocator has it
whatever the core module gains. `starts_with` and `ends_with` are the same story one step along:
both are `equal` over a sub-slice, and both stay for the same reason.

## Two sorts, and neither of them allocates

`sort` is **unstable**, works in the slice it was given, and uses no extra storage. It is an
introsort: insertion sort below sixteen elements, a median-of-three quicksort above that, and
heapsort once the recursion has gone deeper than a well-behaved input ever would — which is what holds
the worst case at O(n log n) rather than letting a chosen input make it quadratic.

`sort_stable` keeps equal elements in the order they arrived, and merges through **scratch the caller
supplies**. That is the honest shape: a stable sort needs somewhere to merge into, and a library that
allocated on your behalf would be unusable on the targets this module exists to stay available on. It
answers `false` if the scratch is shorter than the slice.

```sysl
import sysl.slices.sort_stable

var xs = [(2, 1), (1, 1), (2, 2), (1, 2)]
var scratch = [(0, 0); 4]

print(sort_stable(xs, scratch))
print(xs)
```

```output
true
[(1, 1), (1, 2), (2, 1), (2, 2)]
```

Both come in a `_by` form taking a comparison, which is where the work actually is — the `Ord` forms
are one line handing it `<`. The comparison is a [bare arrow](/reference/types/#function-types), so it is
monomorphized and inlined rather than boxed, and the pair costs nothing over one function.

```sysl
import sysl.slices.sort_by

var xs = [1, 5, 3, 2]

sort_by(xs, (a, b) -> b < a)

print(xs)
```

```output
[5, 3, 2, 1]
```

The pair is `sort_by` and `sort_stable_by`, and `is_sorted_by` asks the same question of a slice
somebody else ordered — which is what an assertion in a test wants, and what a caller checks before
paying for a sort it may not need:

```sysl
import sysl.slices.{is_sorted, is_sorted_by}

var xs = [5, 3, 2, 1]

print(is_sorted(xs), is_sorted_by(xs, (a, b) -> b < a))
```

```output
false true
```

**Which one you want is a real question.** `sort` is faster and says nothing about equal elements;
`sort_stable` promises their order survives. That only matters for a type whose equality does not mean
identity — a record ordered on one field — and when it matters, it matters a great deal.

### Why these are written in sysl rather than calling qsort

It is the first question anybody arriving from C asks, and the answer is not that the C library is
slow.

The binding is perfectly writable, in a dozen lines, and everything it needs is in the language: the
address of a per-instantiation comparison, the address of a slice's storage, and the size of an
element. [The foreign interface](/reference/ffi/) writes exactly this one out.

What decides it is what this module **promises**. `sysl.slices` requires no capability, which is a
promise made to every machine sysl builds for — and several of those are freestanding, where there is
no C library to call at all and `qsort` is an undefined symbol at the end of somebody's link. On a
hosted machine the same promise fails more quietly: glibc's `qsort` sorts by merging into a temporary
buffer and calls `malloc` to obtain one, while Darwin's sorts in place. One source text therefore
allocates on one platform and not on the other.

**Neither fact is visible to the compiler**, because what is behind an `extern` is behind it. A
module whose whole selling point is that it needs nothing cannot offer a function whose needs are
unknowable.

Two smaller reasons stand behind that one. There is no portable **stable** sort in a C library —
`mergesort` and `heapsort` are BSD extensions, present on macOS and absent from glibc, and ISO C has
only the unstable `qsort` — so a binding could have replaced `sort` and never `sort_stable`. And a
bound comparison is an indirect call per step, where a monomorphized `[T: Ord]` body inlines the
same comparison.

## Searching a sorted slice

`binary_search` answers a **pair**: whether the value was found, and the index it is at *or would be
inserted at*.

```sysl
import sysl.slices.binary_search

var xs = [1, 3, 5, 7, 9]

val (found, at) = binary_search(xs, 5)
val (missing, where) = binary_search(xs, 4)

print(found, at)
print(missing, where)
```

```output
true 2
false 2
```

The insertion point is wanted on a miss and is the expensive half of the answer, so throwing it away
would mean computing it twice. An `Option[usize]` does exactly that; a `Result[usize, usize]` — Rust's
spelling — keeps it but calls a miss a *failure*, which it is not.

Where several elements compare equal, the answer is the index of the **first**. That makes it a
function of the values rather than of how the search happened to land, which is what lets two searches
of one slice be compared.

The slice must already be sorted by the same order you are searching with. Nothing checks it — that
would cost a linear scan on every search and defeat the point — and the answer for an unsorted slice
is unspecified rather than wrong in some particular way.

`binary_search_by` takes the comparison, exactly as the sorts' `_by` forms do, and it is the one to
reach for on a slice ordered by anything but `<` — searching with an order the slice was not sorted
by is the mistake the paragraph above describes, and it is easiest to make when the sort took a
comparison and the search did not.

## Handing a slice to C

Every binding to a C library needs a pointer to a slice's first element, because C's convention is a
pointer beside a length. `as_ptr` and `as_mut_ptr` are that, and they exist here because every binding
was otherwise writing them.

```sysl
import sysl.slices.as_ptr

var xs = [10, 20, 30]

print(*as_ptr(xs))
print(as_ptr(xs[0..<0]) == null)
```

```output
10
true
```

**An empty slice answers `null`.** An empty slice is a real input — a length of zero is a loop that
does not run — but `&xs[0]` on one is an out-of-bounds index and traps rather than yielding the
pointer C would have accepted.

The cost, said plainly: ISO C leaves passing a null pointer to `memcpy` undefined even at a length of
zero, and a small number of libraries assert non-null on entry. Every real interface takes the pair
`(null, 0)`, and the length is what says not to look — but a binding whose C asserts otherwise must
pass storage it owns rather than an empty slice.

## Placing a C struct in storage you supplied

A binding that hands C a slice to keep its state in has a second problem after the pointer: a `[]u8`
promises nothing about its own address, and the struct C is about to read wants its natural
alignment. `align_up` answers the first address at or after a pointer that a given alignment divides,
and `is_aligned` asks whether one already is. Neither needs C — an address read as a `usize` is an
ordinary conversion, and `ptr_cast` reads it back.

```sysl
import sysl.slices.align_up
import sysl.slices.is_aligned
import sysl.slices.as_mut_ptr

var storage: [sizeof(u64) + alignof(u64) - 1]u8 = [0; sizeof(u64) + alignof(u64) - 1]

val state: *u64 = ptr_cast(align_up(as_mut_ptr(storage[..]), alignof(u64)))

*state = 42

print(is_aligned(state, alignof(u64)))
print(*state)
```

```output
true
42
```

The storage is over-sized by `alignment - 1`, which is the most the rounding can ever cost, so the
value fits wherever the slice happens to start. `alignof(T)` is a compile-time constant, so the call
asks for what the type wants rather than repeating a number.

**A pointer that is already aligned is answered unchanged.** On a 64-bit machine storage usually is
aligned already, which is what makes a missing call so easy to ship: it works until the day the
storage does not start on a boundary.

**`alignment` must be a power of two and nothing checks it.** The arithmetic is a mask, and a mask
means something only for a power of two — any other value quietly answers an address that is aligned
to nothing. It is the caller's to get right, exactly as it is in C.
