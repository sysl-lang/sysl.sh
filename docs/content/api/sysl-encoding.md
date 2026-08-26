---
title: sysl.encoding
layout: api-module
headingShift: 0
slugStyle: github
module: sysl.encoding
summary: "Fixed-width integers to and from bytes, at both byte orders."
---

**These are free functions at concrete widths, and that is exactly why they can exist.**
`math/integer.sysl` deliberately leaves `swap_bytes` out of the `Bits` trait, because every member
of that trait must be total over every integer type and a `u24` or a `u4` has no byte order at all
-- a member working at `u32` and not at `u24` would turn a bound that was supposed to have proven
an operation into a failure at somebody else's instantiation. Nothing here is a trait member, so
nothing here reopens that: `get_u32_le` names its width, and the widths that have a whole number of
bytes are the only ones written.

**Unsigned only.** The signed read of the same bytes is a cast at the call site, and doubling a
twelve-function surface to spare one cast is not a trade worth making.

**Reading answers `Option` and writing answers `bool`**, rather than trapping on a slice that is
too short. Walking a buffer whose length came from somewhere else is the ordinary use, and running
off the end of one is an expected condition there rather than a program's mistake.

## Index

[`base64_decode`](#base64_decode) [`base64_decoded_len`](#base64_decoded_len) [`base64_encode`](#base64_encode) [`base64_string`](#base64_string) [`get_u16_be`](#get_u16_be) [`get_u16_le`](#get_u16_le) [`get_u32_be`](#get_u32_be) [`get_u32_le`](#get_u32_le) [`get_u64_be`](#get_u64_be) [`get_u64_le`](#get_u64_le) [`hex_decode`](#hex_decode) [`hex_decoded_len`](#hex_decoded_len) [`hex_encode`](#hex_encode) [`hex_string`](#hex_string) [`put_u16_be`](#put_u16_be) [`put_u16_le`](#put_u16_le) [`put_u32_be`](#put_u32_be) [`put_u32_le`](#put_u32_le) [`put_u64_be`](#put_u64_be) [`put_u64_le`](#put_u64_le) [`Alphabet`](#alphabet) [`DecodeError`](#decodeerror) [Display for DecodeError](#display-for-decodeerror)

## Functions

### `base64_decode`

```sysl
base64_decode(text: []const u8, out: []u8) -> Result[usize, DecodeError]
```

The bytes the text spells, written into `out`, and how many there were.

**Six bits at a time into an accumulator**, rather than a four-character group at a time, because
the unpadded spellings end mid-group and a group-at-a-time reader needs a special case for each of
them. Whole bytes fall out of the accumulator as they complete, and the two or four leftover bits
at the end are the padding bits, which are discarded.

### `base64_decoded_len`

```sysl
base64_decoded_len(text: []const u8) -> usize
```

How many bytes `base64_decode` will write, so a caller can size the slice it lends. Trailing
padding is discounted, so this answers the same number for the padded and unpadded spellings of one
input.

### `base64_encode`

```sysl
base64_encode(bytes: []const u8, out: *Writer, alphabet: Alphabet, pad: bool)
```

Three bytes become four characters. The tail -- one or two bytes left over -- becomes two or three
characters, padded out to four with `=` when padding was asked for.

### `base64_string`

```sysl
base64_string(bytes: []const u8, alphabet: Alphabet, pad: bool) -> string
```

The same, gathered into a string. Every character emitted is ASCII, so the bytes are valid UTF-8 by
construction.

### `get_u16_be`

```sysl
get_u16_be(b: []const u8) -> Option[u16]
```

### `get_u16_le`

```sysl
get_u16_le(b: []const u8) -> Option[u16]
```

### `get_u32_be`

```sysl
get_u32_be(b: []const u8) -> Option[u32]
```

### `get_u32_le`

```sysl
get_u32_le(b: []const u8) -> Option[u32]
```

### `get_u64_be`

```sysl
get_u64_be(b: []const u8) -> Option[u64]
```

### `get_u64_le`

```sysl
get_u64_le(b: []const u8) -> Option[u64]
```

### `hex_decode`

```sysl
hex_decode(text: []const u8, out: []u8) -> Result[usize, DecodeError]
```

The bytes the text spells, written into `out`, and how many there were.

### `hex_decoded_len`

```sysl
hex_decoded_len(text: []const u8) -> usize
```

How many bytes `hex_decode` will write, so a caller can size the slice it lends.

Answers for the length alone and does not look at the content: a text of odd length has no whole
number of bytes in it and `hex_decode` refuses it, but the halving is still the right size to have
reserved.

### `hex_encode`

```sysl
hex_encode(bytes: []const u8, out: *Writer)
```

Two characters per byte, written straight through to the sink.

### `hex_string`

```sysl
hex_string(bytes: []const u8) -> string
```

The same, gathered into a string. Every byte emitted above is an ASCII digit, so the result is
valid UTF-8 by construction and needs no validating pass.

### `put_u16_be`

```sysl
put_u16_be(b: []u8, v: u16) -> bool
```

### `put_u16_le`

```sysl
put_u16_le(b: []u8, v: u16) -> bool
```

### `put_u32_be`

```sysl
put_u32_be(b: []u8, v: u32) -> bool
```

### `put_u32_le`

```sysl
put_u32_le(b: []u8, v: u32) -> bool
```

### `put_u64_be`

```sysl
put_u64_be(b: []u8, v: u64) -> bool
```

### `put_u64_le`

```sysl
put_u64_le(b: []u8, v: u64) -> bool
```

## Types

### `Alphabet`

```sysl
enum Alphabet
    Standard
    UrlSafe
```

Which of RFC 4648's two alphabets. They differ only in the last two characters: `+/` reads badly in
a URL and in a filename, which is the whole reason the second exists.

### `DecodeError`

```sysl
enum DecodeError
    BadByte(at: usize)
    BadLength
    BadPadding(at: usize)
    Short(needed: usize)
```

Why a decode refused, at the granularity a caller can act on.

**A type of its own rather than `sysl.text`'s `ParseError`**, which is about reading a *number* out
of text. Two of that type's four variants -- `Overflow` and `BadBase` -- can never occur here, and
a caller matching on it would be told cases exist that cannot. The shape is copied deliberately:
the variants are separated by what a caller would **do** about each and not by taxonomy.

`BadByte` and `BadPadding` carry the offset for the reason `ParseError.BadDigit` and `Utf8Error` do
-- a message naming where is worth writing and cannot be reconstructed afterwards. `Short` carries
the length that *would* have been enough, so a caller can resize and retry in one step rather than
discovering the requirement one byte at a time.

## Implementations

### Display for DecodeError

```sysl
impl Display for DecodeError
```

The rendering, so that a refusal can be printed without matching on it.
