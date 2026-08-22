---
title: Lexical structure
summary: What the compiler reads before it parses anything — tokens, literals, and the layout rules that decide where a statement ends.
weight: 10
---

sysl is read in two passes that are worth keeping separate in your head. A **lexer** turns characters
into tokens and, because the language is indentation-sensitive, also inserts the tokens that open and
close blocks. Only then does a parser see anything. Most of the surprises in a layout-sensitive
language happen in the first pass, which is why it gets its own page.

## Source text

Source is **UTF-8**, and the encoding is not configurable. A non-ASCII character is legal in a
comment, a string literal, and a character literal; identifiers are ASCII (see below).

Line endings may be LF or CRLF. A file need not end in a newline.

## Literate source

**A file named `.lsysl` is a Markdown document, and the part of it indented four columns is the
program.** Everything else is prose and is not compiled. Which of the two a file is, its **name**
decides and nothing else: a `.sysl` file is never read this way whatever its indentation happens to
look like, and a `.lsysl` file with no prose in it is a `.sysl` file with four spaces down the left.

There is no other marker — no name on a block, no directive opening one, no way to say that a block
ends.

````text
# Halving

The interesting part of this program is that it explains itself. Everything at the
margin is prose, and the compiler never sees it.

    half(n: int) -> int = n / 2

A paragraph between two indented blocks does not end anything, so the program can be
explained a step at a time and picked up again where it left off.

    print(half(9))

What a reader should *not* run gets a fence instead of an indent:

~~~
half(n) = n / 2      // the wrong version, with no types
~~~

That block is an illustration and is not compiled.
````

The program that file holds is the two indented lines, and it is an ordinary sysl program:

```sysl
half(n: int) -> int = n / 2

print(half(9))
```

```output
4
```

This is Knuth's *WEB* with its most famous half deliberately left out. WEB let an author write the
program in the order that explains it and had a *tangler* put it back into the order the compiler
needs, which is the feature that made a `.web` file unreadable without its tools. Here the code
appears in the order it runs and the tangler only removes prose, so the file is legible as it sits —
by a reader, by a Markdown renderer, and by `grep`. What is kept is the part that pays: room for an
argument between two functions, in a place a comment cannot hold it.

### The four rules

**Four columns is the threshold**, which is Markdown's own, so what a renderer shows as a code block
is exactly what the compiler reads.

**Consecutive indented blocks are one block.** A paragraph between two of them ends nothing, so a
function body may be explained a step at a time: the prose dedents to column zero and the code
resumes at the indentation it left off at. Without this the format would only be good for examples,
which is the length at which it is not needed.

**A fenced block is an illustration.** Both ` ``` ` and `~~~` mark code that is to be *looked at*
rather than run — the wrong version beside the right one, a shell transcript, a fragment of C — and
none of it is compiled however it is indented. Code that runs is indented; code that is shown is
fenced.

**What is under a bullet is prose.** An indented block inside a list item belongs to that item, so a
list may hold examples without any of them entering the program. An executable block therefore never
sits directly under a list; a line of prose between them is what separates the two.

### Positions survive

**Prose is blanked, not removed.** The text handed to the lexer has exactly as many lines as the
file, and each line of program text is on the line it was written on — so every position the lexer
records is already a position in the `.lsysl` file. There is no mapping table, and no pass below the
parser knows that any of this happened.

The one coordinate that does move is the **column**, by the four that made the line code, and it is
added back where a position is *reported*. A diagnostic therefore names the line and column of the
file the reader has open.

### Two things Markdown would accept are refused

Both are the same failure — a program silently missing a piece of itself — and neither could be
diagnosed later, because what reaches the compiler afterwards is a program that is merely smaller
than the author's.

**A tab in the indentation.** A tab is as wide as whatever is displaying it, so a tab-indented line
is program text in one editor and prose in another. It is refused *before* the four-column test
rather than failing it, since failing it is the silent outcome — the line becomes prose, and a
function quietly loses a statement.

```text
error: a tab in the indentation of a literate file — what makes a line program text is four columns of indent, and a tab is as wide as whatever happens to be displaying it, so this line is code in one editor and prose in another
```

**A fence that is never closed.** Markdown runs an unclosed fence to the end of the document, which
for a document is harmless. Here it turns every declaration below it into an illustration, and the
diagnostic the author would otherwise get is about something incomplete much further down — their
missing half never enters the story. It is refused at the line that opened it, which is the line to
go and look at.

```text
error: this fence is never closed, so everything below it is an illustration and none of it is compiled — close it, or indent the lines that are meant to run
```

### `tangle` and `weave`

The two halves of a literate system are named for what they do to the source, and a build has been
tangling all along — that is how a `.lsysl` file compiles at all. What the commands add is a way to
ask for each half by name:

- **`sysl tangle`** prints the program with the prose stripped. It answers the question a misbehaving
  literate file always raises — *what did the compiler actually read* — which a block indented that
  should not have been, or a fence that swallowed a function, otherwise leaves unanswerable.
- **`sysl weave`** renders the document as HTML. A `.lsysl` file is already Markdown, so the one
  thing a renderer would otherwise lose is the highlighting: an indented block carries no language,
  so nothing can colour it and nothing scanning for the code can find it. `weave` tells the renderer
  that an indented block is sysl and passes the source through verbatim.

Both are covered under the [CLI](/getting-started/cli/). Weaving asks for no target, no standard
module and no library, which is what makes a package's prose worth reading on a machine that could
not build it.

## Comments

```sysl
// A line comment runs to the end of the line.

/* A block comment
   may span lines. */

var x = 1 /* and may sit inside a line */ + 2

print(x)
```

```output
3
```

There is no documentation-comment form with special syntax. Doc text is an ordinary comment above the
declaration it describes.

## Identifiers

An identifier starts with a letter or `_` and continues with letters, digits, or `_`. Letters are
ASCII `A`–`Z` and `a`–`z`.

Case is significant. Capitalization is not enforced anywhere, but the convention the standard library
and this documentation follow is `PascalCase` for types and traits, `snake_case` for everything else —
and the syntax highlighting treats a capitalized name as a type, so following it makes code read
correctly on a page.

### Quoted identifiers

**A name written between backticks may be anything the rule above refuses** — a reserved word, or a
name carrying spaces and punctuation:

```sysl
var `item count` = 3
val `match`: int = 5

struct `Grid Cell`
    `row index`: int
end `Grid Cell`
```

It is a name and nothing more: `` `match` `` *is* the identifier `match`. A contextual word written
this way is an ordinary name rather than the word — `` `end` `` names something, and does not close a
block.

Two characters may not appear inside, and a newline ends the search rather than the name, so an
unclosed backtick is reported on the line that opened it. A **backtick** cannot appear at all, since
there are no escapes inside. A **`.`** cannot either: a qualified name is carried as a dotted string,
so a dot inside one part could not be told from the separator between two.

A **module path** is written with plain names only, for the same reason — `module my.mod`, never
``module `my mod` ``. A module names a directory, and its parts are what the file system holds.

The form does a second job in a `match` arm, where a backticked name **references** a variable
already declared rather than binding a new one.

## Reserved words

Forty words are reserved and may not be used as identifiers:

| | | | | |
|---|---|---|---|---|
| `alignof` | `as` | `break` | `const` | `continue` |
| `defer` | `do` | `elif` | `else` | `ensure` |
| `enum` | `extern` | `false` | `for` | `if` |
| `impl` | `import` | `in` | `loop` | `match` |
| `module` | `null` | `offsetof` | `override` | `private` |
| `ref` | `require` | `return` | `self` | `sizeof` |
| `static` | `struct` | `then` | `trait` | `true` |
| `type` | `val` | `var` | `weak` | `while` |

**`alloc`, `no` and `requires` are *not* among them**, though they read like keywords where they
appear. A capability is written as an [attribute](/reference/attributes/) — `@no_alloc`,
`@requires(os)` — and an attribute's words are matched as ordinary identifiers, which is the point of
spelling capabilities that way: no word is spent, so an allocator may still call its function
`alloc`.

**Type names are deliberately not among them.** `int`, `usize`, `f32`, `bool`, `string` and the rest
are *predeclared identifiers* that the analyzer resolves, exactly as in Go and Swift. That is what
lets the `iN` / `uN` / `fN` families stay open: `u12` and `i5` are types you may write without the
lexer having heard of them, and no list of widths has to be maintained anywhere.

A few words are **contextual** — special only where the grammar expects one, and ordinary identifiers
everywhere else: `is`, `not`, `end`, `opaque`, `deriving`, `invariant`, `new`, `set`, `some`,
`with`, `within`, `where`, and the `c` of a [`c const` or `c type`](/reference/ffi/) block. You may name a
variable `where`; you may not name one `while`.

`set` is read only where a member declaration begins, which is what keeps the word available for
everything else it is wanted for — a container, a local, a method:

```sysl
struct Cell
    set: int
    get -> int = self.set

var set = Cell(3)

print(set.get)
```

```output
3
```

The last of those is the clearest case for why the language spends so few words. `c` is the most
common one-letter name in code that handles characters, and what keeps it available is the keyword
that follows it: nothing else in sysl puts one after a name, so each pair can only be the one thing,
and the word costs nobody anything.

## Reserved identifiers

**An identifier that begins and ends with `__`, holding only capitals and underscores in between,
belongs to the language.** Nothing may declare one — not a function, a type, a `val`, a field, a
parameter, a type parameter, or a local.

```
__FILE__        reserved
__MY_THING__    reserved — and not a built-in, which is a different thing from being available
____            reserved — the middle may be empty
___             not reserved — the markers may not overlap, so four characters is the shortest
__file__        not reserved — the middle is not capitals
__FILE_         not reserved — one underscore short, and so an ordinary name
```

The shape is reserved rather than the names in it, which is what makes every future addition
non-breaking: a release that adds a new built-in cannot collide with a name you already declared,
because the shape was never yours to declare. C reserves the same territory and diagnoses nothing in
it; here taking it is refused where it is written.

These are **predeclared identifiers**, like the type names above — not reserved words, and absent from
the table.

The restriction is on sysl names only. The string an `extern` links to is untouched, which matters
because a C library's own symbols live in exactly this space:

```sysl
private extern "__errno_location" errno_location() -> *i32
```

### The built-ins

| identifier | type | value |
|---|---|---|
| `__FILE__` | `string` | the file's name, as a diagnostic prints it |
| `__LINE__` | integer | 1-based line |
| `__COLUMN__` | integer | 1-based column |
| `__FUNCTION__` | `string` | the enclosing function's name |
| `__DATE__` | `string` | build date, `Mmm dd yyyy` (UTC) |
| `__TIME__` | `string` | build time, `hh:mm:ss` (UTC) |

`__LINE__` and `__COLUMN__` are ordinary integer literals, so each takes the type its context asks
for and is range-checked with it.

**A built-in written as a default argument reports the caller.** A default is evaluated at the call,
standing where the argument would have been written, so this needs no special mechanism:

```sysl
where(line: int = __LINE__) -> int = line

print(where())
print(where())
```

```output
3
4
```

That is how the standard library's `assert` names the line it failed on, and why its message is
optional. Where a default fills another default, the outermost call is the one reported.

`__DATE__` and `__TIME__` make a build non-reproducible, in the way C's do. They are worth having for
a firmware build stamp and worth not reaching for otherwise.

## Literals

### Integers

Decimal, hexadecimal (`0x`), binary (`0b`), and octal (`0o`). An underscore may appear between digits
and is ignored, so long groupings stay readable. A canonical type name may be written as a **suffix**;
without one the literal takes its type from context.

```sysl
var dec = 1_000_000
var hex = 0xFF
var bin = 0b1010_1010
var oct = 0o755
var suffixed = 42

print(dec, hex, bin, oct, suffixed)
```

```output
1000000 255 170 493 42
```

The scan is greedy and validated afterwards rather than stopping at the first character that does not
fit. That is why `42abc` is *one bad literal* and a clear diagnostic, rather than `42` followed by an
identifier and a confusing parse error further along.

An unsuffixed literal adopts the type its context expects, provided the value fits — `var x: u8 = 42`
makes `42` a `u8`, and `var x: u8 = 300` is a compile error. This is not implicit promotion: the
literal simply *is* that type.

### Floating point

A literal containing a `.` with a digit on each side, or an exponent, is floating point. Its default
type is `real` (`f64`); a suffix selects another width.

```sysl
var f1 = 3.14
var f2 = 2.5e3
var f3 = 1e-3

print(f1, f2, f3)
```

```output
3.14 2500 0.001
```

Note the rendering: a float prints in the shortest form that round-trips, so `2.5e3` shows as `2500`
and carries no `.0`. Hexadecimal, binary and octal have no floating-point form.

### Characters

A `char` literal is one Unicode scalar value in single quotes. Nine escapes are named, and anything
else is written with the braced `\u{...}` form.

| escape | meaning |
|---|---|
| `\n` | line feed, U+000A |
| `\t` | tab, U+0009 |
| `\r` | carriage return, U+000D |
| `\b` | backspace, U+0008 |
| `\f` | form feed, U+000C |
| `\0` | NUL, U+0000 |
| `\\` | backslash |
| `\'` | single quote |
| `\"` | double quote |
| `\u{H…}` | one to six hex digits, any Unicode scalar value |

```sysl
var ch = 'A'
var nl = '\n'
var bs = '\b'
var uni = '\u{1F600}'

print(int(ch), int(nl), int(bs), int(uni))
```

```output
65 10 8 128512
```

The named set is C's, and is deliberately no larger: the escape a programmer reaches for should be
the one every language they might arrive from has. The notable absence is **`\e`** for the escape
character, U+001B, which some shells and Perl accept — it is a GNU extension rather than standard C,
and Rust and Go both refuse it. Terminal code writes `'\u{1b}'` and gives it a name.

```sysl
var esc = '\e'

print(int(esc))
```

```error
unknown escape sequence '\e'
```

`\u{...}` is braced rather than a fixed four hex digits because a scalar value may need up to six —
the fixed-width form is what forces surrogate pairs into source text, and sysl's `char` is a scalar
value rather than a UTF-16 code unit, so there is nothing to pair.

### Strings

A plain string is double-quoted and decodes the same escapes a `char` does.

There are several other quote forms, each marked by a prefix on the opening quote:

| form | what it is |
|---|---|
| `"…"` | a `string` — validated UTF-8, with escapes decoded |
| `c"…"` | a C string — the same value with the terminator C expects, read as `*u8` |
| `s"…"` | interpolated: `${expr}` holes rendered with `str` |
| `f"…"` | interpolated with a printf specifier allowed after each hole |
| `raw"…"` | no escape processing — a backslash is a backslash |

Any of them may also be written **tripled** as a text block, which spans lines and strips the common
leading indentation so the text lines up with the code around it rather than against the left margin.

The forms are told apart at the token, so nothing downstream has to remember which quote produced a
given value.

### Loop labels

A label is `'name` — an apostrophe and an identifier, the form Rust uses. It is told from a character
literal by the **absence of a closing quote**: `'a'` is the character `a`, and `'a` is the label `a`.

### `true`, `false`, `null`

Reserved words rather than library constants. `null` exists for `*T` and only for `*T`.

## Operators and delimiters

The operator set is **closed** — there are no user-defined operator symbols, and no way to add one.
Operators are tokenized by longest match, so `..<` wins over `..`, and `<<=` over `<<`.

```
=  +=  -=  *=  /=  %=  &=  |=  ^=  <<=  >>=
||  &&  !
==  !=  <  >  <=  >=
..  ..<  ...
|  ^  &  ~
+  -  *  /  %  <<  >>
++  --
(  )  [  ]  {  }  .  ?
.*
,  ::  :  ->
#  ;
```

Two of these are worth a note. `.*` is lexed as **one token** — the tail of a wildcard import — which
is possible because a `.` is otherwise only ever followed by a name. And `;` is *only* a separator
inside a three-clause `for` header: it is deliberately not a statement terminator, because a line
already ends a statement and a token that could also end one would give the language two answers to
the same question.

`@` opens an annotation and `#` opens a directive, and between them they are a declaration's only
prefixes that are not words. Nothing in the expression grammar spells either, so neither needs
lookahead to recognize. The two are told apart by the sigil and not by the margin, though a directive
is taken by a pass that runs before the lexer and reads only column 1 — so an *indented* `#` reaches
the grammar like any other token, and [attributes](/reference/attributes/) says what it is told there.

## Layout

sysl is indentation-sensitive. Indenting opens a block and dedenting closes it; there are no braces
around statement blocks, and a newline ends a statement.

```sysl
count(n: int) -> int
    var total = 0

    for i in 1..n
        total += i

    total

print(count(4))
```

```output
10
```

### Brackets suspend the rule

Inside `(`, `[` or `{`, layout stops applying until the bracket closes — so an argument list, an array
literal, or a parenthesized expression may be broken across lines however you like, and the
indentation of the continuation lines means nothing.

### An unbracketed line continues after an operator

The rule is the narrowest one that works: **an operator that cannot finish an expression continues the
line.** After `+`, `&&`, `==`, `<<` or a prefix `!` something must follow, so a newline there cannot
have been the end of a statement and there is nothing ambiguous to resolve.

```sysl
var total = 1 +
    2 +
    3

var ok = total > 5 &&
    total < 10

print(total, ok)
```

```output
6 true
```

What is *excluded* follows from the same rule rather than from taste:

- **`=` and `->`** are binary but already open an indented block — a function body, a match arm, a
  [binding's value](/reference/declarations/#the-value-may-be-an-indented-block) — and one token cannot mean
  both "the block starts here" and "the line goes on". A value too long for its line therefore goes
  *under* the `=` as a block rather than after it as a continuation.
- **`++`, `--` and `?`** are postfix, so a line ending in one is already complete.
- **`..`, `..<` and `...`** can be complete too: `s[..]` is the whole range, and `int...` is a
  variadic tail.
- **`.`** would work, but the continuation style worth having for a call chain puts the dot at the
  *start* of the next line, which needs the opposite mechanism.

### The one hazard

A continuation line that is **dedented** has its dedent swallowed along with the newline, so a
trailing operator can hold a block open further than it looks. Every language that joins lines has
this, including on brackets.

It is documented rather than guarded against, and the reason is worth knowing: guarding would mean
the indentation of a continuation line carried meaning, and the entire point of continuing a line is
that it does not.
