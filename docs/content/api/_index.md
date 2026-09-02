---
title: "Library API"
weight: 45
layout: api-index
headingShift: 0
slugStyle: github
---

These pages are generated from the library's own doc comments: every declaration, with its signature and nothing else. The [written library section](/library/#the-library-is-a-tree-and-the-tree-is-the-point) is the other half — what each module is *for*, why its pieces are shaped as they are, and worked examples you can run. Start there to learn a module; come here when you know what you want and need its exact signature.

## Modules

| Module | Summary |
|---|---|
| [`sysl`](sysl/) | What a program does when something it was sure of turns out not to hold. |
| [`sysl.args`](sysl-args/) |  |
| [`sysl.buf`](sysl-buf/) | The growable sequence and the sink built on it. |
| [`sysl.container`](sysl-container/) | A singly linked list that is never modified: every operation answers a **new** list, and the old one is still there and still correct. |
| [`sysl.container.ring`](sysl-container-ring/) |  |
| [`sysl.crypto`](sysl-crypto/) |  |
| [`sysl.encoding`](sysl-encoding/) | Fixed-width integers to and from bytes, at both byte orders. |
| [`sysl.env`](sysl-env/) |  |
| [`sysl.fs`](sysl-fs/) | What a filesystem call reports when it does not succeed, and the one number it comes from. |
| [`sysl.harness`](sysl-harness/) | A test framework that runs on the target. |
| [`sysl.io`](sysl-io/) |  |
| [`sysl.math`](sysl-math/) | Choosing between two values, and holding one to a range. |
| [`sysl.math.complex`](sysl-math-complex/) |  |
| [`sysl.path`](sysl-path/) |  |
| [`sysl.posix.net`](sysl-posix-net/) |  |
| [`sysl.posix.rand`](sysl-posix-rand/) |  |
| [`sysl.posix.threads`](sysl-posix-threads/) | Threads of execution, and the two things a program does with one: start it, and wait for it. |
| [`sysl.posix.time`](sysl-posix-time/) |  |
| [`sysl.posix.tty`](sysl-posix-tty/) | Getting the kernel out of the way, so a program can read a terminal a keystroke at a time. |
| [`sysl.process`](sysl-process/) |  |
| [`sysl.rand`](sysl-rand/) |  |
| [`sysl.regex`](sysl-regex/) |  |
| [`sysl.seq`](sysl-seq/) |  |
| [`sysl.slices`](sysl-slices/) | Operations over a built-in slice, and the pointer a C binding needs to hand one across. |
| [`sysl.sync`](sysl-sync/) |  |
| [`sysl.sys`](sysl-sys/) | What `sysl.math` asks of the machine and of the C mathematics library, and the whole of what it asks. |
| [`sysl.term`](sysl-term/) | What a terminal understands: the escape sequences that colour text, emphasise it, and move the cursor about. |
| [`sysl.term.edit`](sysl-term-edit/) |  |
| [`sysl.text`](sysl-text/) | What a program says when it means text as bytes: the validator, the two conversions either side of C, the character cursor, and the builder in the file beside this one. |
| [`sysl.time`](sysl-time/) | The calendar: a date and a time of day with no zone attached, and the arithmetic that turns one into the other. |
| [`sysl.time.tzif`](sysl-time-tzif/) |  |
