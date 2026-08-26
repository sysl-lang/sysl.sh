---
title: sysl.crypto
layout: api-module
headingShift: 0
slugStyle: github
module: sysl.crypto
requires: "no alloc"
---

## Index

[`hmac224`](#hmac224) [`hmac256`](#hmac256) [`hmac384`](#hmac384) [`hmac512`](#hmac512) [`new_sha224`](#new_sha224) [`new_sha256`](#new_sha256) [`new_sha384`](#new_sha384) [`new_sha512`](#new_sha512) [`sha224`](#sha224) [`sha256`](#sha256) [`sha384`](#sha384) [`sha512`](#sha512) [`verify`](#verify) [`Fault`](#fault) [`Sha224`](#sha224-1) [`Sha256`](#sha256-1) [`Sha384`](#sha384-1) [`Sha512`](#sha512-1) [Word for u32](#word-for-u32) [Word for u64](#word-for-u64)

## Functions

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

### `Sha224`

```sysl
struct Sha224
    inner: Sha[u32]
```

| Member | Signature | Description |
|---|---|---|
| `update` | `update(*self, data: []const u8)` |  |
| `finish` | `finish(*self, digest: []u8) -> Result[unit, Fault]` |  |

### `Sha256`

```sysl
struct Sha256
    inner: Sha[u32]
```

| Member | Signature | Description |
|---|---|---|
| `update` | `update(*self, data: []const u8)` |  |
| `finish` | `finish(*self, digest: []u8) -> Result[unit, Fault]` |  |

### `Sha384`

```sysl
struct Sha384
    inner: Sha[u64]
```

| Member | Signature | Description |
|---|---|---|
| `update` | `update(*self, data: []const u8)` |  |
| `finish` | `finish(*self, digest: []u8) -> Result[unit, Fault]` |  |

### `Sha512`

```sysl
struct Sha512
    inner: Sha[u64]
```

| Member | Signature | Description |
|---|---|---|
| `update` | `update(*self, data: []const u8)` |  |
| `finish` | `finish(*self, digest: []u8) -> Result[unit, Fault]` |  |

## Implementations

### Word for u32

```sysl
impl Word for u32
```

### Word for u64

```sysl
impl Word for u64
```
