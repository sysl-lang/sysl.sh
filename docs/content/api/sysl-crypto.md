---
title: sysl.crypto
layout: api-module
headingShift: 0
slugStyle: github
module: sysl.crypto
requires: "no alloc"
---

## Index

[`hmac1`](#hmac1) [`hmac224`](#hmac224) [`hmac256`](#hmac256) [`hmac384`](#hmac384) [`hmac512`](#hmac512) [`new_sha1`](#new_sha1) [`new_sha224`](#new_sha224) [`new_sha256`](#new_sha256) [`new_sha384`](#new_sha384) [`new_sha512`](#new_sha512) [`sha1`](#sha1) [`sha224`](#sha224) [`sha256`](#sha256) [`sha384`](#sha384) [`sha512`](#sha512) [`verify`](#verify) [`Fault`](#fault) [`Sha1`](#sha1-1) [`Sha224`](#sha224-1) [`Sha256`](#sha256-1) [`Sha384`](#sha384-1) [`Sha512`](#sha512-1) [Compression for Sha1C](#compression-for-sha1c) [Compression for Sha2Narrow](#compression-for-sha2narrow) [Compression for Sha2Wide](#compression-for-sha2wide) [Word for u32](#word-for-u32) [Word for u64](#word-for-u64)

## Functions

### `hmac1`

```sysl
hmac1(key: []const u8, msg: []const u8) -> [20]u8
```

HMAC-SHA-1, answering a twenty-byte tag. A key longer than a block is replaced by its own
digest; a shorter one is padded with zeroes.

**This is a weaker warning than `sha1` carries, and deliberately so.** HMAC's security does not
rest on the hash being collision resistant, so HMAC-SHA-1 is not broken the way SHA-1 is -- which
is why RFC 6238 still specifies it as TOTP's default and why a great deal of deployed request
signing still uses it. It is here to talk to those. A protocol being written now wants `hmac256`.

### `hmac224`

```sysl
hmac224(key: []const u8, msg: []const u8) -> [28]u8
```

### `hmac256`

```sysl
hmac256(key: []const u8, msg: []const u8) -> [32]u8
```

### `hmac384`

```sysl
hmac384(key: []const u8, msg: []const u8) -> [48]u8
```

### `hmac512`

```sysl
hmac512(key: []const u8, msg: []const u8) -> [64]u8
```

### `new_sha1`

```sysl
new_sha1() -> Sha1
```

A hasher ready for its first `update`.

**SHA-1 is broken.** Reach for `new_sha256` unless a wire protocol names this one; see `sha1`.

### `new_sha224`

```sysl
new_sha224() -> Sha224
```

### `new_sha256`

```sysl
new_sha256() -> Sha256
```

### `new_sha384`

```sysl
new_sha384() -> Sha384
```

### `new_sha512`

```sysl
new_sha512() -> Sha512
```

### `sha1`

```sysl
sha1(data: []const u8) -> [20]u8
```

The digest of one slice of bytes, which is the whole of what most callers want.

**SHA-1 is broken, and has been publicly broken since 2017.** A collision is buildable, and since
2019 a chosen-prefix collision is, so this digest says nothing about a document nobody could have
substituted: it must not carry a signature, a certificate, a content address or a deduplication
key. **`sha256` is what all of those want.**

It is in this module for **RFC 6455's opening handshake**, which answers a client's key with
`base64(sha1(key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"))` and names both the algorithm and the
constant in the standard, so there is no substitute. That use makes no security claim at all --
the handshake shows the far end read the request and authenticates nobody -- which is why an
algorithm this broken still serves it. Where a second preimage would buy an attacker nothing,
SHA-1 is adequate; everywhere else it is not, and there is no third case.

### `sha224`

```sysl
sha224(data: []const u8) -> [28]u8
```

### `sha256`

```sysl
sha256(data: []const u8) -> [32]u8
```

### `sha384`

```sysl
sha384(data: []const u8) -> [48]u8
```

### `sha512`

```sysl
sha512(data: []const u8) -> [64]u8
```

### `verify`

```sysl
verify(a: []const u8, b: []const u8) -> bool
```

## Types

### `Fault`

```sysl
enum Fault
    NotStarted
    DigestTooSmall
```

| Member | Signature | Description |
|---|---|---|
| `describe` | `describe(self) -> string` |  |

### `Sha1`

```sysl
struct Sha1
    inner: Sha[Sha1C]
```

A SHA-1 hash in progress. **SHA-1 is broken** -- see `sha1` for what that does and does not
rule out.

| Member | Signature | Description |
|---|---|---|
| `update` | `update(*self, data: []const u8)` |  |
| `finish` | `finish(*self, digest: []u8) -> Result[unit, Fault]` |  |

### `Sha224`

```sysl
struct Sha224
    inner: Sha[Sha2Narrow]
```

| Member | Signature | Description |
|---|---|---|
| `update` | `update(*self, data: []const u8)` |  |
| `finish` | `finish(*self, digest: []u8) -> Result[unit, Fault]` |  |

### `Sha256`

```sysl
struct Sha256
    inner: Sha[Sha2Narrow]
```

| Member | Signature | Description |
|---|---|---|
| `update` | `update(*self, data: []const u8)` |  |
| `finish` | `finish(*self, digest: []u8) -> Result[unit, Fault]` |  |

### `Sha384`

```sysl
struct Sha384
    inner: Sha[Sha2Wide]
```

| Member | Signature | Description |
|---|---|---|
| `update` | `update(*self, data: []const u8)` |  |
| `finish` | `finish(*self, digest: []u8) -> Result[unit, Fault]` |  |

### `Sha512`

```sysl
struct Sha512
    inner: Sha[Sha2Wide]
```

| Member | Signature | Description |
|---|---|---|
| `update` | `update(*self, data: []const u8)` |  |
| `finish` | `finish(*self, digest: []u8) -> Result[unit, Fault]` |  |

## Implementations

### Compression for Sha1C

```sysl
impl Compression for Sha1C
```

### Compression for Sha2Narrow

```sysl
impl Compression for Sha2Narrow
```

### Compression for Sha2Wide

```sysl
impl Compression for Sha2Wide
```

### Word for u32

```sysl
impl Word for u32
```

### Word for u64

```sysl
impl Word for u64
```
