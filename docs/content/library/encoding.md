---
title: The encoding module
summary: "`sysl.encoding` — hexadecimal and base64 in both directions, fixed-width integers to and from bytes at either byte order, and a `DecodeError` that says what a caller can act on."
weight: 58
---

`sysl.encoding` turns bytes into text and back, and integers into bytes and back. Three files, one
error type, and no allocator required for any of the core surface.

```sysl
import sysl.encoding.{hex_string, base64_string, Standard}

print(hex_string("foobar".bytes))
print(base64_string("foobar".bytes, Standard, true))
```

```output
666f6f626172
Zm9vYmFy
```

## The two directions are shaped differently, on purpose

**Encoding writes to a [`Writer`](/library/core/#rendering-to-a-sink).** That means hex straight to a file or to
standard output with no intermediate string — which is the case that actually matters for a codec.

```sysl
import sysl.encoding.hex_encode

hex_encode("hi".bytes, stdout())
print("")
```

```output
6869
```

**Decoding writes into a slice the caller supplies**, and answers how many bytes it wrote. The output
length is computable before anything is read — half the text for hex, three quarters for base64 — so
there is nothing to discover by allocating, and the module stays usable where there is no allocator.
`hex_decoded_len` and `base64_decoded_len` are exported so a caller can size the slice.

```sysl
import sysl.encoding.{hex_decode, hex_decoded_len}
import sysl.text.from_utf8

val text = "666f6f".bytes
var out: []u8 = [0; 8]

print(hex_decoded_len(text))
print(hex_decode(text, out).unwrap())
print(from_utf8(out[0..<3]).unwrap())
```

```output
3
3
foo
```

The `_string` conveniences beside each encoder are the only things in the module that allocate, and
they exist because assembling a sink for the common case would be the library refusing to do the easy
half.

## base64 has two axes, and they are parameters

The alphabet and the padding are independent, so naming every combination ends at
`base64_encode_urlsafe_nopad`. An enum and a `bool` say the same thing and compose.

```sysl
import sysl.encoding.{base64_string, Standard, UrlSafe}

var bytes: []u8 = [0xfb, 0xff, 0xbf]

print(base64_string(bytes, Standard, true))
print(base64_string(bytes, UrlSafe, true))
print(base64_string("fo".bytes, Standard, false))
```

```output
+/+/
-_-_
Zm8
```

**Decoding accepts either alphabet without being told**, which costs nothing: `+/` and `-_` do not
overlap, so there is no input the two readings disagree about. Padding is optional on input and
checked when present. That asymmetry is deliberate — a writer should be exact and a reader should be
forgiving about what cannot be ambiguous.

## What a refusal says

`DecodeError` has four cases, separated by what a caller would **do** about each rather than by
taxonomy.

| | |
|---|---|
| `BadByte(at)` | a byte outside the alphabet, and where |
| `BadLength` | not a whole number of encoded units |
| `BadPadding(at)` | `=` somewhere it cannot be |
| `Short(needed)` | the output slice is too small, and by how much |

`Short` carries the length that *would* have been enough, so a caller resizes once rather than
discovering the requirement a byte at a time.

```sysl
import sysl.encoding.{hex_decode, BadByte, Short}

var out: []u8 = [0; 8]
var tiny: []u8 = [0; 1]

val bad = hex_decode("66zz".bytes, out) match
    Err(BadByte(at)) -> s"bad byte at $at"
    _ -> "something else"

val short = hex_decode("666f6f".bytes, tiny) match
    Err(Short(n)) -> s"needs $n bytes"
    _ -> "something else"

print(bad)
print(short)
```

```output
bad byte at 2
needs 3 bytes
```

It is a type of its own rather than [`sysl.text`](/library/text/)'s `ParseError`, which is about
reading a *number* out of text: two of that type's four cases could never occur here, and a caller
matching on it would be told cases exist that cannot.

## Fixed-width integers, at either byte order

```sysl
import sysl.encoding.{get_u32_be, get_u32_le, put_u16_be, get_u16_be}

var b: []u8 = [0x11, 0x22, 0x33, 0x44]

print(get_u32_be(b).unwrap())
print(get_u32_le(b).unwrap())

var w: []u8 = [0; 2]

print(put_u16_be(w, 0xbeef))
print(get_u16_be(w).unwrap())
```

```output
287454020
1144201745
true
48879
```

Reading answers an `Option` and writing a `bool`, rather than trapping: walking a buffer whose length
came from somewhere else is the ordinary use, and running off the end of one is an expected condition
there rather than a program's mistake.

**These are free functions at concrete widths, and that is exactly why they can exist.**
[`sysl.math`](/library/math/)'s `Bits` trait deliberately has no `swap_bytes`, because every member of
that trait must be total over every integer type and a `u24` has no byte order at all. Nothing here is
a trait member, so nothing here reopens that — `get_u32_le` names its width, and the widths written
are the ones with a whole number of bytes.

Unsigned only: the signed read of the same bytes is a cast at the call site, and doubling a
twelve-function surface to spare one cast is not a trade worth making.
