---
title: sysl.fs
layout: api-module
headingShift: 0
slugStyle: github
module: sysl.fs
summary: "What a filesystem call reports when it does not succeed, and the one number it comes from."
requires: "requires { os }"
---

`reference/errors.md § Which channel — the policy` puts this class of failure — something outside
the program, a file or a user or a device — on `Result` rather than on a trap, and this is the
error half of every `Result` the module hands back. It is an enum rather than a bare `int` for
the reason `Result` carries its error as a type parameter at all: a caller that wants to act on
*why* wants to match, and a match over named cases is exhaustive where a comparison against a
number is a guess that compiled.

The cases are the ones a program branches on. Everything else arrives as `Other`, carrying the
number, because a library that mapped every `errno` to a name of its own would be a table nobody
could keep current and a program could not extend -- and the number is what a reader looks up
anyway.

**The module requires `os`.** Nothing here is language, and nothing here can be given a body on a
target with no filesystem under it: a freestanding image has no `fopen` to call and no `errno` to
read. `reference/modules.md § Capabilities are a module property` reserves `os` for exactly this
and the clause is what says so in the one place a reader of the file will see it.

## Index

[`append`](#append) [`append_bytes`](#append_bytes) [`append_text`](#append_text) [`create`](#create) [`create_update`](#create_update) [`entries`](#entries) [`exists`](#exists) [`is_dir`](#is_dir) [`is_file`](#is_file) [`make_dir`](#make_dir) [`open`](#open) [`open_update`](#open_update) [`read_bytes`](#read_bytes) [`read_text`](#read_text) [`readable`](#readable) [`remove_dir`](#remove_dir) [`remove_file`](#remove_file) [`rename`](#rename) [`size_of`](#size_of) [`writable`](#writable) [`write_bytes`](#write_bytes) [`write_text`](#write_text) [`File`](#file) [`FileState`](#filestate) [`IoError`](#ioerror) [Display for IoError](#display-for-ioerror) [Eq for IoError](#eq-for-ioerror) [Fallible for File](#fallible-for-file) [Reader for File](#reader-for-file) [Writer for File](#writer-for-file)

## Functions

### `append`

```sysl
append(path: string) -> Result[File, IoError]
```

### `append_bytes`

```sysl
append_bytes(path: string, bytes: []const u8) -> Result[unit, IoError]
```

Writes the bytes at the end of the file, making it if it is not there. Each call lands after
whatever the file already held, including anything another program appended in between.

### `append_text`

```sysl
append_text(path: string, text: string) -> Result[unit, IoError]
```

### `create`

```sysl
create(path: string) -> Result[File, IoError]
```

### `create_update`

```sysl
create_update(path: string) -> Result[File, IoError]
```

### `entries`

```sysl
entries(path: string) -> Result[Buf[string], IoError]
```

Every name in a directory, with `.` and `..` left out.

The two dots are dropped because every caller drops them: a program listing a directory is asking
what is *in* it, and one that genuinely wants to walk upwards has a path to build rather than a
name to find. Leaving them in makes the ordinary case a filter written at every call site, and a
recursive walk that forgets the filter does not terminate.

**The names arrive in whatever order the filesystem keeps them**, which is not sorted and is not
stable between two listings of one directory. A program that wants an order applies one; there is
no order here to be relied on by accident.

**A name that is not UTF-8 stops the program**, exactly as `read_text` does with a file's contents
and for the reason written there: answering with an error would put a case in `IoError` that no
filesystem ever reports. A POSIX name is bytes, so this is reachable — and a program that would
rather inspect than trap is one this module does not serve yet.

### `exists`

```sysl
exists(path: string) -> bool
```

Whether there is anything at all at this path.

It answers `false` for a path that exists but sits under a directory the program may not search,
which is the one way this differs from the question it looks like it is asking. That case cannot
be told from absence without a second call whose answer would already be stale, and every caller
that matters -- one about to open the file -- gets the truth from the open.

### `is_dir`

```sysl
is_dir(path: string) -> bool
```

Whether the path names a directory, asked by opening it as one. `DIR *` is opaque, so this needs
nothing about the platform beyond the two symbols -- which is what makes it the one question about
a path's *kind* the module can answer honestly.

### `is_file`

```sysl
is_file(path: string) -> bool
```

Whether the path names something that is not a directory. A device or a socket answers `true`
here, which is right for what callers use it for: the question is whether opening it as a file
could work.

### `make_dir`

```sysl
make_dir(path: string) -> Result[unit, IoError]
```

Makes one directory. The parent has to be there already: making a chain is a loop over path
separators, and where a separator *is* is a question about paths rather than about the
filesystem, which this module does not answer.

### `open`

```sysl
open(path: string) -> Result[File, IoError]
```

The three ways a program opens a file, named for what it means rather than for the mode string it
becomes. `open` reads an existing file; `create` makes one, or empties one that was there;
`append` writes at the end of whatever is there, making the file if it is not.

### `open_update`

```sysl
open_update(path: string) -> Result[File, IoError]
```

A file opened for reading **and** writing, which the three above cannot express between them: each
of C's update modes is a different answer to "and what about what is already there", and naming
them apart is what keeps a caller from having to know that `"r+"` fails on a missing file while
`"w+"` empties one that exists.

### `read_bytes`

```sysl
read_bytes(path: string) -> Result[[]u8, IoError]
```

Every byte of the file.

It reads in chunks and grows rather than sizing the buffer from `size` first, which costs a little
copying and buys the case that matters: a file being written while this reads it, and anything
whose length is not a fact until the read ends. The loop stops on the first empty read, and asks
afterwards whether the reading *ended* badly -- the two are separate questions, which is the split
`Reader` was built around.

### `read_text`

```sysl
read_text(path: string) -> Result[string, IoError]
```

The file as text.

**Bytes that are not UTF-8 stop the program**, the way an ill-formed command-line argument does,
and for the reason `sysl.io`'s `line_text` gives: the layer underneath is public, so a caller who
would rather inspect than trap reads `read_bytes` and validates it. Answering with an error
instead would put a case in `IoError` that no filesystem ever reports, and would make every
program that reads its own configuration handle a failure that means its build is broken.

### `readable`

```sysl
readable(path: string) -> bool
```

### `remove_dir`

```sysl
remove_dir(path: string) -> Result[unit, IoError]
```

Removes a directory, which has to be empty. A directory with anything in it reports
`DirectoryNotEmpty` -- the one error code in the set whose number the two platforms disagree
about, which is why `error.sysl` reads it through a call rather than a literal.

### `remove_file`

```sysl
remove_file(path: string) -> Result[unit, IoError]
```

Removes a file, and refuses a directory rather than removing it: `unlink` is the call that draws
that line, where C's own `remove` quietly does either. Two names for two intentions is what lets
a program that meant one of them find out it had the other.

### `rename`

```sysl
rename(from: string, to: string) -> Result[unit, IoError]
```

Moves a path to another name, replacing whatever was at the destination.

It is one operation as far as anything watching is concerned when both paths are on the same
filesystem, and it fails rather than copying when they are not -- which is `rename(2)`'s contract
and worth knowing, since a program moving a file across devices has to read and write it itself.

The two copies are named rather than written into the call, because a `cstring` temporary lives
for the statement it appears in and this statement needs both of them alive at once.

### `size_of`

```sysl
size_of(path: string) -> Result[long, IoError]
```

How many bytes the file holds. It opens the file to ask, so it reports the same failures an open
does -- a missing file answers `NotFound` rather than zero, which is the distinction a caller
sizing a buffer needs and the one a bare number could not carry.

### `writable`

```sysl
writable(path: string) -> bool
```

### `write_bytes`

```sysl
write_bytes(path: string, bytes: []const u8) -> Result[unit, IoError]
```

Writes the bytes, replacing whatever was there and making the file if it was not.

### `write_text`

```sysl
write_text(path: string, text: string) -> Result[unit, IoError]
```

## Types

### `File`

```sysl
struct File
    inner: &FileState
```

A file that is open, or that was.

**Closing is the program's to do**, and `defer f.close()` is how it is written -- the language has
no destructor, so nothing runs at the end of a scope that the scope did not say. What the shared
state above buys is that saying it *twice* is harmless rather than a use-after-free, which is the
mistake a value type would otherwise make easy. Dropping a `File` without closing it leaks the
handle until the program exits, the same as in C.

| Member | Signature | Description |
|---|---|---|
| `closed` | `closed(*self) -> bool` |  |
| `flush` | `flush(*self) -> Result[unit, IoError]` | *Every member below asks this first, and none of them may be dropped.** `fclose` releases the handle, so the pointer this struct holds afterwards names storage C has freed, and reaching it is a use-after-free rather than a call that fails. |
| `close` | `close(*self) -> Result[unit, IoError]` | Releases the handle, flushing what is still buffered. |
| `tell` | `tell(*self) -> Result[long, IoError]` | Where the next read or write will happen, in bytes from the start. |
| `seek` | `seek(*self, to: long) -> Result[unit, IoError]` | Moves to an absolute offset, which also clears the end-of-file mark. |
| `size` | `size(*self) -> Result[long, IoError]` | How many bytes the file holds. |
| `at_end` | `at_end(*self) -> bool` | Whether a read has already run off the end. |

### `FileState`

```sysl
struct FileState
    handle: *u8
    bad: bool
    shut: bool
```

The handle itself, and the two things that are true of it beyond the pointer.

It is a struct of its own -- rather than the three fields sitting directly in `File` -- because of
what `File` has to survive being copied. A struct is a value here, so a `File` handed to a
function is a second `File`, and if it held the pointer directly then closing either would leave
the other holding a handle C has freed. Every copy sharing **one** `FileState` is what makes
`close` answerable at all: it is written once, and every copy sees it.

### `IoError`

```sysl
enum IoError
    NotFound
    PermissionDenied
    AlreadyExists
    NotADirectory
    IsADirectory
    DirectoryNotEmpty
    TooManyOpenFiles
    NoSpaceLeft
    Interrupted
    NotOpen
    Other(code: int)
```

Why a filesystem operation did not happen.

| Member | Signature | Description |
|---|---|---|
| `code` | `code(self) -> int` | The number this came from, so a program can report or compare one the set does not name. |
| `message` | `message(self) -> string` | A sentence, in the terms the operation was asked in rather than in C's. |

## Implementations

### Display for IoError

```sysl
impl Display for IoError
```

So that `print(e)` and `f"$e%s"` say the sentence rather than the shape of the value.

### Eq for IoError

```sysl
impl Eq for IoError
```

So that a caller can say `e == NotFound` rather than `e.code() == 2`.

**The enum's own docstring is what asks for this**: it says the cases are named so that a program
acting on *why* can match rather than compare against a number, since "a comparison against a
number is a guess that compiled" -- and without an `Eq` the only comparison available was exactly
that. This module's own test wrote `assert_eq(e.code(), 2)` for want of it.

**A payload is why it has to be written out.** An enum no variant of which carries anything gets
`==` on its discriminant for free; `Other` carries a code, so this one does not.

It compares on `code()`, which makes `Other(2)` equal to `NotFound` -- deliberately, because they
are the same condition wearing two spellings, and `from_errno` never produces the first. Comparing
on the shape instead would make an error that came from a platform sysl does not have a named case
for unequal to itself after a round trip through a number.

### Fallible for File

```sysl
impl Fallible for File
```

Whether anything has gone wrong on this file since it was opened. It latches, so it is worth
asking once after a run of writes rather than after each -- which is the whole reason the surface
reports this way (`reference/errors.md § The third shape: a latch`).

**It is written once, here, and that is what makes the two `impl` blocks below answerable.** A
`failed` declared by `Reader` and another declared by `Writer` would be two members of one name on
one type -- allowed, but a call could not say which it meant, since `failed` takes no arguments
and a program that reads and writes a file has both traits in scope. Both requiring `Fallible`
instead leaves this the single answer each of them reaches.

### Reader for File

```sysl
impl Reader for File
```

Reading bytes out of a file is the `Reader` of `sysl.io`, whole and unmodified, which is what lets
`lines(&f)` work over a file with nothing here knowing about lines.

An empty answer means the end of the input and nothing more. Whether the input ended *badly* is
what `failed` reports, and the two are separate questions because a caller that does not care
should not have to ask -- the same split `FdReader` makes.

### Writer for File

```sysl
impl Writer for File
```

And writing them is the `Writer` of `library/core.md § What is in it`, so everything that renders
into a sink renders into a file. It latches rather than returning, which is what keeps
`f.write(b)` a statement.
