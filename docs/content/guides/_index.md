---
title: Guide Programs
summary: Two real programs written to force a language decision — what each one owns, and what writing it found.
weight: 50
---

The [tour](/tour/) teaches the language, the [reference](/reference/) says what the rules are, and
the [library](/library/) says what ships beside them. This section is about something else: the
programs in [`guide/`](https://github.com/sysl-lang/sysl/tree/dev/guide) that were written
to **force a language decision**.

They are not demonstrations of a finished language. That distinction is the whole of why they exist,
and it is worth stating in the form the set's own README does:

> The value is in the friction. The point at which a program cannot be written cleanly is the point
> at which the language is wrong, and that is the signal — a program that goes in smoothly told us
> nothing.

So a guide program is chosen for an **axis** nothing else in the set covers, written until it either
goes in cleanly or does not, and the place it snagged is the output. Most of the language features
documented elsewhere on this site exist because one of these programs could not be written without
them: `Buf[T]`, `Hash` in the core catalogue, `from_utf8`, the repeat form `[v; n]`, `unit` as a
zero-sized type, trailing-operator continuation, `ref` bindings, `sizeof` and `alignof`, and the rule
that a constrained subtype's attributes speak the subtype. Each of those has a program that paid for
it first.

## Why they are documented here rather than left in the repo

Because the findings are the interesting part and they are buried in header comments. A program's
own file records what it found in the place the code that provoked it lives, which is right for
somebody reading the program and useless for somebody wondering **why the language is shaped this
way**. These pages pull each finding out and link it to the rule it produced.

## The set is smaller than it was, on purpose

A guide program is scaffolding for a decision, not a permanent exhibit. Once its findings are
discharged and its subject has a better home — a package somebody can depend on, a module in the
standard library, or a rule written down in the reference — the program has stopped teaching anything
and is only a tree to keep green. Most of the set has been retired that way. What it decided is in
the [reference](/reference/); what it was useful for lives on as
[packages](https://github.com/sysl-lang).

Two of them are worth following, because both had to *change* to be depended on, which is the
difference between a program written to find something out and one written to be used.
[`fft`](https://github.com/sysl-lang/fft) is generic over its float width where the guide program was
fixed at one. [`png`](https://github.com/sysl-lang/png) had to take its CRC table out of module
storage: a derived table needs an initializer run before anything else, which a `build-c` archive on
a freestanding target has nothing to run — so the guide's shape was correct for a program and wrong
for a package, and nothing said so until somebody tried to put it on a board.

## The two, and the axis each owns

| program | the axis it owns |
|---|---|
| [ring](/guides/ring/) | the constrained-subtype surface — ranges, `::` attributes, contracts, invariants |
| [slab](/guides/slab/) | raw storage — reinterpreting bytes, `sizeof`/`alignof`, an intrusive free list |

Inside [ring](/guides/ring/), two implementations are **written to be compared** and the comparison
is the measurement: one buffer keeps the fact of where the ring ends once and the other keeps it
twice, and every check runs both and requires them to agree.

## Running one

Each directory is a **project root**, so the files in it are the anonymous root module and any
subdirectory is a module named by its path:

```
sbt "syslJVM/run run guide/ring"
```

**Each program checks itself.** Every line it prints is either a `--` section header or `ok`
followed by what was checked, so a failure is a line that says otherwise. `GuideTests` runs each one
and asserts that nothing failed, that the number of checks is the expected one, and that the sections
ran in order — the count being what makes the first assertion mean anything, since a check that
quietly stopped running would otherwise look like a check that passed.

## What a self-checking run cannot check

A violated `require`, a broken `invariant` and a failed range check all
[trap](/reference/errors/), so a program demonstrating one would die rather than report it and the
run would look truncated rather than failed. A run therefore asserts a refusal only through a
**total** operation that answers instead of trapping.

The traps are asserted separately, in [`@test(should_trap)`](/reference/attributes/) functions that
live in the program's own directory. Each runs in a process of its own and passes by not coming
back, so a trap is an observation there rather than the end of the run — which is what lets a refusal
be stated in sysl, beside the code it is about. `guide/ring` was the first to need this.

The discipline that goes with it: **write every refusal beside the call that is not refused.** A
`should_trap` test passes for any failure at all, its own setup included, so alone it cannot tell
"the contract fired" from "nothing worked". One call over the line and one call up to it, and the
difference between them is the contract.

---

Next: [ring](/guides/ring/) — bounded indices, and an invariant that found a redundant field.
