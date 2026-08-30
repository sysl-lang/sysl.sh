---
title: The crypto module
summary: "`sysl.crypto` — the SHA-2 family, SHA-1, and HMAC over any of them, written in sysl, answering bytes and allocating nothing."
weight: 59
---

**Every declaration in `sysl.crypto`, with its signature:** [the generated API page](/api/sysl-crypto/#index). This page is the argument — what the module is for, and how its pieces fit; that one is the list.

`sysl.crypto` is SHA-224, SHA-256, SHA-384 and SHA-512, and HMAC over any of them. It is written in
sysl rather than bound to a C library, it needs no allocator, and it builds for a freestanding target
along with the rest of the standard module.

SHA-1 is here too, and it is here for one thing — [the WebSocket handshake](#sha-1-and-the-one-thing-it-is-for).
It is broken, a new program should not choose it, and the section that introduces it says so at
length rather than in a footnote.

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

The four SHA-2 digests are **two** algorithms. SHA-224 and SHA-256 are the same 32-bit compression started
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

## SHA-1, and the one thing it is for

**SHA-1 is broken and has been publicly broken since 2017.** `SHAttered` exhibited two different PDF
files with one digest; `SHA-1 is a Shambles` made that a *chosen-prefix* collision in 2019, for about
$45,000 of rented compute. A SHA-1 digest therefore says nothing about a document nobody could have
substituted, so it must not carry a signature, a certificate, a content address or a deduplication
key. **`sha256` is what every one of those wants**, and it is in this same module.

It is here because of **RFC 6455's opening handshake**, where a WebSocket server answers a client's
key with `base64(sha1(key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"))`. There is no substitute: the
algorithm and the constant are both written into the standard, and no browser will open a socket
against any other answer. The example below is the RFC's own, key and result both.

```sysl
import sysl.crypto.sha1
import sysl.encoding.{Alphabet, base64_string}

val key = "dGhlIHNhbXBsZSBub25jZQ=="
val accept = sha1((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").bytes)

print(base64_string(accept[..], Alphabet.Standard, true))
```

```output
s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
```

**That use makes no security claim at all**, which is the whole reason an algorithm this broken still
serves it. The handshake shows that the far end read the request and understood the protocol — it
authenticates nobody, and a collision would buy an attacker nothing, because there is nothing to
substitute. Wherever the answer to "what would a second preimage let somebody do here?" is "nothing",
SHA-1 is adequate; everywhere else it is not, and there is no third case.

The surface is the same as the others': a one-shot digest, and a hasher that streams.

```sysl
import sysl.crypto.{new_sha1, sha1}
import sysl.encoding.hex_string

var h = new_sha1()
var out: [20]u8

h.update("a".bytes)
h.update("bc".bytes)
h.finish(out[..]).expect("a digest buffer of the right size")

print(hex_string(sha1("abc".bytes)))
print(hex_string(out[..]) == hex_string(sha1("abc".bytes)))
```

```output
a9993e364706816aba3e25717850c26c9cd0d89d
true
```

### `hmac1` is a weaker claim than SHA-1's collisions make it sound

`hmac1` answers a twenty-byte tag, like the four beside it.

```sysl
import sysl.crypto.hmac1
import sysl.encoding.hex_string

print(hex_string(hmac1("Jefe".bytes, "what do ya want for nothing?".bytes)))
```

```output
effcdf6ae5eb2fa2d27416d5f184df9c259a7c79
```

**HMAC's security does not rest on the hash being collision resistant**, so HMAC-SHA-1 is not broken
the way SHA-1 is — which is why RFC 6238 still specifies it as TOTP's default and why a great deal of
deployed request signing still uses it. It is here to talk to those, not to be chosen: a protocol
being written now wants `hmac256`.

## What is not here

**No cipher, no signature, no key exchange, and no password hash.** Those are
[sysl-lang/monocypher](https://github.com/sysl-lang/monocypher), a binding to an audited C library —
a package rather than part of the standard module, because a crypto library has to be able to ship a
fix on its own schedule. SHA-2 is here instead of there because it is a frozen standard with no
upstream to track, and because monocypher does not implement it.

## Comparing a tag: use `verify`, not `==`

**`==` on two tags is a timing oracle.** A natural comparison walks left to right and stops at the
first difference, so a tag whose first byte is wrong is rejected fractionally sooner than one whose
first three bytes are right. An attacker who can submit guesses and time the rejections recovers the
tag a byte at a time — turning an impossible search into an afternoon's work. It is the defect that
bit Keyczar in 2009, and why Java documents `Arrays.equals` as unsuitable for the same job.

`verify` reads **every** byte and asks the question once, at the end.

```sysl
import sysl.crypto.{hmac256, verify}

val key = "Jefe".bytes
val msg = "what do ya want for nothing?".bytes
val tag = hmac256(key, msg)

print(verify(tag, hmac256(key, msg)), verify(tag, hmac256(key, "something else".bytes)))
```

```output
true false
```

The **length** is compared first, and that comparison does branch — which costs nothing, since how
long a SHA-256 tag is was never a secret. What must not leak is *where* two tags of the same length
diverge.

**Reach for it wherever one side is a secret somebody supplied** — a message authentication code, a
session token, a password digest. For two digests of public data `==` is fine and says so more
plainly.

### What is promised, and what is not

**Promised**: `verify` reads every byte of both inputs and takes no branch on their contents. That is
a property of the emitted code, and it is pinned by a test — a change that introduced an early exit
fails a build rather than shipping quietly.

**Not promised**: that the machine executes it in constant time. sysl has no way to *state* that
requirement — there is no annotation for it and no check — so nothing stops a future optimizer, or a
processor with a data-dependent instruction, from reintroducing a signal. The emitted code was read
at 0.0.79 on aarch64: the loop is branchless and the verdict is a conditional set.

Where a **hardware-backed** guarantee is the requirement rather than a careful implementation, the
instruments are the processor's own — AArch64's `DIT`, x86's `DOITM` — and a vetted C implementation
such as [Monocypher](https://github.com/sysl-lang/monocypher)'s `crypto_verify*`, which the org binds
as a package.
