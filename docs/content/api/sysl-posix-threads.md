---
title: sysl.posix.threads
layout: api-module
headingShift: 0
slugStyle: github
module: sysl.posix.threads
summary: "Threads of execution, and the two things a program does with one: start it, and wait for it."
requires: "no alloc, requires { posix }"
---

**This module requires a capability and `sysl.sync` does not**, which is the whole reason the two
are apart. `Atomic[T]` and `SpinLock` are reachable from a module that has given up its allocator
and its operating system, because a word the processor touches indivisibly is something a bare
machine has. A *thread* is not: creating one needs a scheduler underneath, and a module's
requirement is module-wide -- one type in here needing an operating system would have taken
`Atomic[T]` out of the kernel's reach.

**What is here is pthreads, which is why it sits under `sysl.posix`** (`reference/modules.md §
Capabilities are a module property`). The capability is `posix` and nothing narrower: whether a
scheduler exists is not something the compiler tracks, because nothing in the library is gated on
one. A bare-metal target running FreeRTOS has threads of its own and no POSIX, so it does not
reach this module and wants a package binding its own kernel instead -- the way `termbox2` sits
beside `sysl.term`.

A **domain** is a thread (`reference/memory.md § Crossing a concurrency domain`), so everything
this module starts is a new one, and what may cross the boundary between two is the subject of
that chapter. Nothing here copies anything: `spawn` hands the new thread an **address**, and what
is at that address is then shared by two threads and needs a `Mutex[T]` beside this file, or an
`Atomic[T]` below it.

**`spawn` is marked `@crossing(arg)`**, so every count the state reaches has to be atomic and the
compiler says so at the call (`reference/memory.md § @crossing`). A raw pointer is on the
crossable list because it carries no refcount to make atomic -- which is a fact about the
pointer, not about what it points at, and the annotation is what asks for the second question to
be put.

## Index

[`current`](#current) [`spawn`](#spawn) [`yield_now`](#yield_now) [`Mutex`](#mutex) [`Thread`](#thread)

## Functions

### `current`

```sysl
current() -> Thread
```

The calling thread's own handle, which is what a body compares against to learn it is not the
thread that spawned it.

### `spawn`

```sysl
spawn[T](body: *extern(*T) -> unit, arg: *T) -> Option[Thread]
```

Starts `body` on a new thread, with `arg` as the address it is handed.

**The body is a `*extern`, not a callable** (`reference/ffi.md § A function's address`). A
closure would have to be boxed for the new thread to reach it, which needs an allocator this
module could otherwise do without, and its captures would be values crossing a domain boundary
with nothing yet checking that they may. The address of a named function is what C's own
interface takes, so it is what this takes.

**`T` is inferred from the body**, so `spawn(&work, &state)` is the whole of the call. An address
is always written, `null` included -- and `null` is the one thing that cannot be, since it takes
its type from its context and the context here is the `T` being inferred. A body with nothing of
its own to read is handed the address of whatever it reads instead, which every body has.

The pointer is the sharing: two threads reading and writing what is at that address is a race
unless something orders them, which is what `Mutex[T]` and `Atomic[T]` are for.

**`@crossing(arg)` is what holds a caller to `06`'s rule about what may reach another domain.**
Without it the pointer would end the question: a `*T` is on the crossable list because *it* carries
no count, which says nothing about the object at the far end -- and the object at the far end is
what the new thread gets. The annotation is what asks the compiler to look through it, so a state
holding a plain `&T` is refused here rather than racing later.

A body declared `-> unit` is called by pthreads as though it returned a `void *`, and the value it
leaves in the return register is whatever was there. Nothing reads it -- `join` above passes no
place to put it -- so the mismatch costs the thread's exit value, which this module does not offer.

### `yield_now`

```sysl
yield_now() -> bool
```

Offers the processor to whatever else is ready to run, and answers whether the system took it.

This is a **hint**, not a wait: a thread that yields is still runnable and may be given the
processor straight back. What it is for is the spin in `Mutex.lock`, where the thread holding the
lock may not be running at all and nothing else will make it so.

## Types

### `Mutex`

```sysl
struct Mutex[T]
    held: i32
    value: T
```

Mutual exclusion that **owns what it protects**, which is the difference `library/threads.md §
Mutex[T]` draws against `SpinLock`.

A spinlock is a flag beside the data and what the data is stays the programmer's to remember. This
holds the `T`, and both of its fields are private -- so there is no way to reach the value that
does not go through `lock` or `try_lock`, and no way to build one that skips the free state. That
is as far as a language with no destructor can take the idea: releasing is still written, and
`defer m.unlock()` is how, exactly as `defer f.close()` is in `sysl.fs` and for the same reason.

```
var p = m.lock()

defer m.unlock()
*p = *p + 1
```

**It is not built on `pthread_mutex_t`, and the reason is a build property rather than a
preference.** A caller-allocated opaque C type is one of the three things `reference/ffi.md § A
library may carry C` names as reachable from C and from nothing else: its size is in a header, it
differs between the platforms and between two libcs on the *same* platform -- 64 bytes on Darwin,
40 under glibc on x86-64, 48 on aarch64, 40 again under musl -- and `#if` can ask which operating
system this is but not which libc. Transcribing a bound would compile everywhere and be checked
nowhere, which is precisely the failure that section is about. The way to read a header is C, and
the standard library deliberately includes none: it reaches libc by symbol alone, which is what
lets it go on building for any target the toolchain can lower for. So the lock is three atomic
operations and a yield, and a binding library that carries its own C shim is where a futex-backed
one belongs.

**What that costs is a waiter's processor for one scheduling quantum at a time.** The spin is not
`SpinLock`'s -- a failed exchange gives the processor up rather than turning round again -- so a
waiter cannot starve the holder the way a pure spin can on one core, and the hold may be as long as
it likes. What it does not do is *sleep*: there is no wait queue, so a contended lock costs a
context switch per attempt where a futex would cost none.

| Member | Signature | Description |
|---|---|---|
| `new` | `new(value: T) -> Mutex[T]` | Builds a free lock around a value. |
| `lock` | `lock(*self) -> *T` | Takes the lock, waiting until it is free, and answers the address of what it protects. |
| `try_lock` | `try_lock(*self) -> Option[*T]` | Takes the lock if it is free, and answers what it protects where it did. |
| `unlock` | `unlock(*self)` | Releases the lock. |

### `Thread`

```sysl
struct Thread
    id: usize
```

A thread that has been started, and may be waited for.

It is a handle rather than the thread: copying one copies the handle, and joining either copy
joins the one thread. Joining **twice** is undefined in POSIX and is not checked here, for the
reason `SpinLock.unlock` gives about the releasing thread -- the word it would take to notice is
paid by every correct program.

| Member | Signature | Description |
|---|---|---|
| `join` | `join(self) -> bool` | Waits for the thread to finish, and answers whether it was waited for. |
