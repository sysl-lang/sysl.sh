---
title: qsort
summary: The C boundary in the direction nothing else goes — a C routine that calls back into sysl, and the trampoline, slice address and element size it takes.
weight: 160
---

`qsort` is handed a slice's storage, the width of an element, and a comparison — and then it calls
that comparison back, once per step, for as long as the sort runs. This program binds it and checks
its answers against [`sysl.slices`](/library/slices/)' own sort on the same data.

**The axis: a callback across the C boundary.** Every other program in the set either stays inside
sysl or reaches out to C and gets an answer back. This is the one where C reaches *in*. It is the
smallest honest example of the shape, and it needs three things at once — the address of a function
that is generic in the element type, the address of a slice's storage, and the size of an element.

## The binding, in full

```sysl
private extern "qsort" c_qsort(base: *u8, n: usize, size: usize, cmp: *u8)

private compare[T: Ord](a: *T, b: *T) -> int
    if *a < *b then -1
    else if *b < *a then 1
    else 0
end compare

sort_libc[T: Ord](xs: []T)
    if xs.len < 2 then return

    val cmp: *extern(*T, *T) -> int = &compare

    c_qsort(ptr_cast(as_mut_ptr(xs)), xs.len, sizeof(T), ptr_cast(cmp))
end sort_libc
```

`compare` is **one function per element type**, and that is the whole trick. `qsort` is told the
width of an element and otherwise moves anonymous bytes; the only thing that knows what those bytes
*are* is that body, and it knows because the compiler made a copy of it for each `T` a program
sorted. A C programmer does the same thing by hand and writes the cast themselves.

The two comparisons are not clumsiness either. C's convention has three answers — negative, zero,
positive — and `<` has two, so there is no third answer to read off a single test.

## What it found

**The obvious trampoline has no address.** The natural shape is C's own signature with the cast
inside it:

```sysl
private compare[T: Ord](a: *u8, b: *u8) -> int = ...
```

and it cannot be given to anything. The address of a generic function takes its instantiation from
the **expected type** — there is no written `&f[T]`, because it could not be told from `&xs[i]` — and
the expected type here is `*extern(*u8, *u8) -> int`, which does not mention `T` at all. There is
nothing to read.

What works is above: let the *pointers* carry the type, and cast the pointer **to the function** at
the call rather than casting inside the body. The `val` exists only because there is nowhere else to
write the type. The [FFI reference](/reference/ffi/) states the limit; what this program
corrected is the sentence that followed it, which said such a pattern therefore needs a concrete
trampoline per element type. It does not — one generic body serves them all, at the cost of an extra
`ptr_cast` and a `val`.

**There is no clock.** [`sysl.time`](/library/time/) has `Instant`, `Duration` and the calendar
between them, and nothing in the library reads one — no monotonic counter and no wall clock. So this
program compares the two sorts for *correctness* and cannot compare them for *cost*, which was half
of why it was written. Binding `clock_gettime` here would have answered the question and put a
hand-rolled clock into the reading material, which is the thing a guide program is least allowed to
teach.

## Why this is not in the standard library

`sysl.slices` requires no capability, which is a promise made to every machine sysl builds for —
including the freestanding ones, where there is no C library and `qsort` is an undefined symbol at
the end of somebody's link. The [slices page](/library/slices/#why-these-are-written-in-sysl-rather-than-calling-qsort)
has the rest of the argument, including the part that has nothing to do with speed: glibc's `qsort`
allocates a merge buffer and Darwin's does not, and **the compiler cannot see through an `extern`**
to know which it got.

That is why the binding lives here, where a reader can see the whole of what it costs on one screen,
rather than in a module whose selling point is that it needs nothing.

---

[Source](https://github.com/sysl-lang/sysl/tree/dev/guide/qsort) ·
Back to [the guide programs](/guides/), or on to the [reference](/reference/).
