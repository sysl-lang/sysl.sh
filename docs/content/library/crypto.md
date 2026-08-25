---
title: The crypto module
summary: "`sysl.crypto` — the SHA-2 family and HMAC, written in sysl, answering bytes and allocating nothing."
weight: 59
---

`sysl.crypto` is SHA-224, SHA-256, SHA-384 and SHA-512, and HMAC over any of them. It is written in
sysl rather than bound to a C library, it needs no allocator, and it builds for a freestanding target
along with the rest of the standard module.

```sysl
import sysl.crypto.sha256
import sysl.encoding.hex_string

print(hex_string(sha256("abc".bytes)))
```

```output
ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
```

## A digest is bytes

Every digest answers a **fixed-size array of bytes**, and the size is in the type — `sha256` answers
`[32]u8`, `sha512` answers `[64]u8`. Nothing here renders, and that is deliberate: a digest is
usually on its way into a comparison, a signature or another hash, and a caller who wanted text can
say so in one call.

```sysl
import sysl.crypto.{sha224, sha256, sha384, sha512}

print(sha224("abc".bytes).len, sha256("abc".bytes).len, sha384("abc".bytes).len, sha512("abc".bytes).len)
```

```output
28 32 48 64
```

Rendering is [`sysl.encoding`](/library/encoding/)'s job, which is why this module imports nothing to
do it and needs no allocator: `hex_string` builds a string, and a program that never asks for one
never pays for it.

## A hash in progress is a value

`Sha224`, `Sha256`, `Sha384` and `Sha512` are ordinary structs, so a hasher is a value and **copying
one is starting another from the same point**. `update` takes any slice of bytes and may be called as often as you like; `finish`
writes into storage the caller owns.

```sysl
import sysl.crypto.new_sha256
import sysl.encoding.hex_string

var h = new_sha256()
var out: [32]u8

h.update("a".bytes)
h.update("b".bytes)
h.update("c".bytes)
h.finish(out[0..<32]).expect("a digest buffer of the right size")

print(hex_string(out))
```

```output
ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
```

The message is the concatenation of everything handed over, so the three calls above and one call
with `"abc"` agree — which is what lets a file be hashed a block at a time without holding it.

`finish` answers a `Result`, and refuses two things: a hasher that was never started, and a digest
buffer smaller than the digest.

```sysl
import sysl.crypto.new_sha512

var h = new_sha512()
var too_small: [32]u8

print(h.finish(too_small[0..<32]).is_err())
```

```output
true
```

## One algorithm at two widths

The four digests are **two** algorithms. SHA-224 and SHA-256 are the same 32-bit compression started
from different initial values and truncated differently; SHA-384 and SHA-512 are the same 64-bit one.
So the width is what a type parameter carries, and the compression is written **once** over a bound
that `u32` and `u64` both satisfy.

**That genericity does not reach this page's surface, and that is deliberate.** The four hashers are
four ordinary types; the trait behind them is private to the module. A public generic would have
dragged its bound out with it, and a bound declaring `bits`, `rounds` and `k` is the standard library
claiming names general enough that the next program to want one would collide with it.

That is why the truncated pair are not prefixes of the untruncated ones — the published initial
values for SHA-224 and SHA-384 are chosen so that they cannot be.

```sysl
import sysl.crypto.{sha224, sha256}
import sysl.encoding.hex_string

print(hex_string(sha256("abc".bytes))[0..<56] == hex_string(sha224("abc".bytes)))
```

```output
false
```

## Keyed hashing

`hmac224`, `hmac256`, `hmac384` and `hmac512` answer a tag the size of their digest. A key longer
than a block is replaced by its own digest; a shorter one is padded.

```sysl
import sysl.crypto.hmac256
import sysl.encoding.hex_string

print(hex_string(hmac256("Jefe".bytes, "what do ya want for nothing?".bytes)))
```

```output
5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843
```

The generic `hmac` underneath them takes a started hasher and the caller's buffer, which is what the
four above are written in terms of.

## What is not here

**No cipher, no signature, no key exchange, and no password hash.** Those are
[sysl-lang/monocypher](https://github.com/sysl-lang/monocypher), a binding to an audited C library —
a package rather than part of the standard module, because a crypto library has to be able to ship a
fix on its own schedule. SHA-2 is here instead of there because it is a frozen standard with no
upstream to track, and because monocypher does not implement it.

**No constant-time comparison.** Comparing a tag you computed against one that arrived with `==` is a
timing oracle, and sysl has no way to *state* that a comparison is constant-time, let alone check it.
Monocypher's `equal` is the instrument for that and this module does not have one. Verifying a MAC is
the case to be careful about; comparing two digests of public data is not.
