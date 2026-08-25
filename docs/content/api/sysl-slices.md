---
title: sysl.slices
layout: api-module
headingShift: 0
slugStyle: github
module: sysl.slices
summary: "Operations over a built-in slice, and the pointer a C binding needs to hand one across."
requires: "no alloc"
---

**These are free functions over `[]T` rather than members of anything**, which is the call
`math/compare.sysl` already made for `min` and `integer.sysl` made for `gcd`. A slice is indexed by
the compiler walking to an address, so none of this goes through `Index` or `Iterate` -- those say
what indexing and walking mean for a type *someone wrote*, and a built-in has neither to offer. A
bound written on each function is also the specification: `contains` asks for `Eq` because it
compares, `max_index` asks for `Ord` because it orders, and a program with a type of its own
satisfying the bound gets these for nothing.

**Nothing here allocates and nothing here needs a capability.** The module is reachable from a
freestanding target, which is deliberate and is what keeps it usable by a C binding: `as_ptr` is
the first thing a binding reaches for, and acquiring an allocator by asking for it would be a poor
trade. Sorting is in `sort.sysl` beside this file and holds to the same rule.

## Index

[`INSERTION_LIMIT`](#insertion_limit) [`as_mut_ptr`](#as_mut_ptr) [`as_ptr`](#as_ptr) [`binary_search`](#binary_search) [`binary_search_by`](#binary_search_by) [`contains`](#contains) [`ends_with`](#ends_with) [`equal`](#equal) [`fill`](#fill) [`index_of`](#index_of) [`is_sorted`](#is_sorted) [`is_sorted_by`](#is_sorted_by) [`last_index_of`](#last_index_of) [`max_index`](#max_index) [`min_index`](#min_index) [`reverse`](#reverse) [`sort`](#sort) [`sort_by`](#sort_by) [`sort_stable`](#sort_stable) [`sort_stable_by`](#sort_stable_by) [`starts_with`](#starts_with) [`swap`](#swap)

## Constants

### `INSERTION_LIMIT`

```sysl
const INSERTION_LIMIT: usize = 16
```

Below this many elements, insertion sort. It is where the recursion bottoms out and where a simple
quadratic sort genuinely wins -- the constant factor is small and the data is in cache.

## Functions

### `as_mut_ptr`

```sysl
as_mut_ptr[T](xs: []T) -> *T
```

The same, where the caller may write through it.

Separate from `as_ptr` because `[]const T` and `[]T` are different types and the const one must not
yield a pointer something can write through. The empty case answers `null` for the same reason.

### `as_ptr`

```sysl
as_ptr[T](xs: []const T) -> *T
```

A pointer to the first element, for handing a slice to C as the pointer-and-length pair every C
interface takes.

**This exists because every binding was writing it.** `qcbor` and `monocypher` each carried a
private copy and the QOI survey needed the same one, and each copy had to get the empty case right
on its own -- which is the case that is not obvious, and the reason this is a library function.
Both copies are gone: the two packages import this one instead, and their two hand-rolled versions
had different bugs, which is the argument for a library function in one line.

**An empty slice answers `null`.** An empty slice is a real input -- C's convention is a pointer
beside a length, and a length of zero is a loop that does not run -- but `&xs[0]` on an empty slice
is an out-of-bounds index, so it traps rather than yielding the pointer C would have accepted.

The two answers this was chosen over are both worse. A pointer to a **function-local** scratch,
which is what the hand-rolled copies return, points into a dead stack frame: safe exactly as long
as nobody reads it, and silently wrong if anything ever does. A module-level scratch fixes the
lifetime but cannot be generic, so there would have to be one per element type and nowhere to put
them.

**The cost, said here so a caller can see it:** ISO C leaves passing a null pointer to `memcpy`
undefined even at a length of zero, and a small number of libraries assert non-null on entry. Every
real interface takes the pair `(null, 0)`, and the length is what says not to look -- but a binding
whose C asserts otherwise must pass storage it owns rather than an empty slice.

### `binary_search`

```sysl
binary_search[T: Ord](xs: []const T, x: T) -> (bool, usize)
```

Where a value is in a sorted slice, and where it would go if it is not there.

**Answers a pair rather than an `Option`, because the index is wanted either way.** A caller that
found the value wants its position; a caller that did not wants the place to insert it, which is
the expensive half of the answer and is already computed. `Option[usize]` throws that away and
`Result[usize, usize]` -- Rust's spelling -- calls a miss a failure, which it is not.

**The index of the FIRST equal element**, where the slice holds several. That makes the answer
depend on the values rather than on how the search happened to land, which is what lets two
searches of the same slice be compared.

The slice must be sorted by the same order being searched with. Nothing checks it -- that would
cost a linear scan on every search, defeating the point -- and the answer for an unsorted slice is
unspecified rather than wrong in some particular way.

### `binary_search_by`

```sysl
binary_search_by[T](xs: []const T, x: T, lt: (T, T) -> bool) -> (bool, usize)
```

### `contains`

```sysl
contains[T: Eq](xs: []const T, x: T) -> bool
```

Whether the value appears at all -- `index_of` where the position is not wanted.

### `ends_with`

```sysl
ends_with[T: Eq](xs: []const T, suffix: []const T) -> bool
```

Whether the slice ends with the given elements.

### `equal`

```sysl
equal[T: Eq](a: []const T, b: []const T) -> bool
```

Whether two slices have the same length and the same elements in the same order.

The length test comes first and is what makes the common refusal cost nothing.

### `fill`

```sysl
fill[T](xs: []T, v: T)
```

Every element set to one value.

### `index_of`

```sysl
index_of[T: Eq](xs: []const T, x: T) -> Option[usize]
```

Where a value first appears, or `None`.

**`Option[usize]` rather than a sentinel index.** A library answering `-1` for absence hands back a
number that indexes when it should not, and `usize` has no negative to spare in any case. The
absence is in the type, where a caller has to look at it.

### `is_sorted`

```sysl
is_sorted[T: Ord](xs: []const T) -> bool
```

Whether the slice is already in order. Worth having on its own, and it is also the sorts' own
postcondition, which is what the tests assert against.

### `is_sorted_by`

```sysl
is_sorted_by[T](xs: []const T, lt: (T, T) -> bool) -> bool
```

### `last_index_of`

```sysl
last_index_of[T: Eq](xs: []const T, x: T) -> Option[usize]
```

Where a value last appears, or `None`. Walks down from the end, so it stops at the first match
rather than scanning the whole slice to keep the latest one.

### `max_index`

```sysl
max_index[T: Ord](xs: []const T) -> Option[usize]
```

Where the largest element is, or `None`. Ties go to the first, as above -- written `xs[best] <
xs[i]` rather than `xs[i] > xs[best]` so that an equal element does not displace the one already
held.

### `min_index`

```sysl
min_index[T: Ord](xs: []const T) -> Option[usize]
```

Where the smallest element is, or `None` for an empty slice.

**The index rather than the value**, because the index answers both questions: an element is one
subscript away, and a caller that wanted to *modify* the extreme element could not have got there
from a copy. Ties go to the **first**, which is the same choice `min` makes and for the same
reason: for a type whose equality does not mean identity, which of two indistinguishable values
comes back is something a caller can observe.

### `reverse`

```sysl
reverse[T](xs: []T)
```

The elements in the opposite order, in place.

Walks from both ends toward the middle, so each element is written once and an odd middle element
is left alone rather than swapped with itself.

### `sort`

```sysl
sort[T: Ord](xs: []T)
```

The slice in order, in place, using no extra storage.

**Introsort**: quicksort for the general case, insertion sort for the small one, and heapsort once
the recursion has gone deeper than a well-behaved input ever would. The last of those is what holds
the worst case at O(n log n) -- a plain quicksort degrades to O(n²) on input chosen to defeat its
pivot, and a library sort must not have an input that makes it quadratic.

**Equal elements may come back in any order.** That is what "unstable" means and it is only
observable for a type whose equality does not mean identity -- a record ordered on one field. A
caller who cares wants `sort_stable`, which says so in its name.

### `sort_by`

```sysl
sort_by[T](xs: []T, lt: (T, T) -> bool)
```

### `sort_stable`

```sysl
sort_stable[T: Ord](xs: []T, scratch: []T) -> bool
```

The slice in order, with equal elements left in the order they were given, merged through storage
the caller supplies.

Answers `false` and changes nothing when the scratch is shorter than the slice, which is the one
way it can refuse. `scratch` needs `xs.len` elements; anything beyond that is ignored.

**Bottom-up rather than recursive.** The passes double in width -- 1, 2, 4, 8 -- merging adjacent
runs, so there is no recursion and the stack does not grow at all. A top-down merge sort would add
O(log n) frames for the same result.

### `sort_stable_by`

```sysl
sort_stable_by[T](xs: []T, scratch: []T, lt: (T, T) -> bool) -> bool
```

### `starts_with`

```sysl
starts_with[T: Eq](xs: []const T, prefix: []const T) -> bool
```

Whether the slice begins with the given elements. An empty prefix is a prefix of everything, which
falls out of the loop rather than being decided here.

### `swap`

```sysl
swap[T](xs: []T, i: usize, j: usize)
```

Two elements exchanged, in place.

Out-of-range indices trap, as any other subscript does: this is an operation on storage the caller
named, and inventing an answer for an index that is not there would hide the caller's own mistake.
