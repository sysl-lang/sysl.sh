---
title: sysl.sys
layout: api-module
headingShift: 0
slugStyle: github
module: sysl.sys
summary: "What `sysl.math` asks of the machine and of the C mathematics library, and the whole of what it asks."
requires: "no alloc"
---

What `sysl.math` asks of the machine and of the C mathematics library, and the whole of what it
asks.

The directive is what the driver used to carry: every ELF link was handed `-lm` whether or not
the program computed anything, because the compiler had no way to be told and this file had no
way to say. Where libm *lives* is still the target's answer -- a file of its own on ELF, part of
`libSystem` on Darwin, absent from a freestanding machine -- and that is why the directive names
the library rather than the flag (`reference/ffi.md § @link`).

These are here rather than beside their callers for the reason the module exists: a name in
`sysl.sys` is one the rest of the library may reach and nothing else may, so every declaration in
the library that is not sysl is in one place and the surface a host has to supply can be read off
a single file. Each is bound to a `sysl_`-prefixed word for the same reason `putchar` is --
`sqrt`, `pow` and `floor` are words a program is entitled to declare itself, and programs did
exactly that before there was a module to ask.

**Two kinds of declaration, told apart by the namespace the link name is in.** A name beginning
`llvm.` is an intrinsic: the back end recognises it and emits the machine's own instruction, and
there is no symbol for a linker to find. Everything else is libm's, resolved at the link. The
split is not stylistic -- it is which operations the hardware has. A square root, an absolute
value, a sign transfer and the four roundings are instructions on every machine sysl targets; a
sine is not, on any of them, so asking LLVM for `llvm.sin` would produce a call to the same libm
function this file already names, one indirection later.

**What that buys beyond speed is a program that needs no libc.** A freestanding target has no
libm to link against, so before this the whole module was hosted-only; the operations above the
line now work on a bare machine, and only the transcendentals below it do not.

Two entry points per operation either way, because C names the widths apart: `sqrt` takes a
`double`, `sqrtf` a `float`. Overloading (`reference/declarations.md § Overloading`) could give
the pair one sysl name now, and deliberately does not -- these are the raw declarations, and a
name here that did not match the symbol it resolves would be the one thing this file exists not
to do. The intrinsics are spelled the same twice for a different reason -- one base name, two
widths, and the compiler derives `.f64` or `.f32` from the signature. `sysl.math` is where that
stops being visible, and it stops there by dispatching on the receiver's type rather than by a
caller choosing.

The transcendentals are here and most of the comparisons are not, and the line is what the machine
can do that sysl's operators cannot. A range-reduced sine is an algorithm; so, less obviously, are
the two sign operations, which read and write the **sign bit** directly. An absolute value written
as `if x < 0 then -x else x` is wrong for a negative zero -- no comparison distinguishes it from a
positive one, so the negation never runs and a magnitude comes back negative. Everything that is a
comparison and nothing more -- `signum`, `is_finite`, the interpolation -- `sysl.math` writes in
sysl and calls nothing.
