# S++ plugin roadmap

Ideas for making the plugin more complete, collected 2026-10-04. Nothing here is started. Items marked ★ are the
suggested starting points.

## Already in place

For reference, so the list below is only what's missing:

- File type, parser, syntax colouring, brace matching, quote handling, commenter, folding, breadcrumbs.
- Formatter, code style settings, import sorting, docstring handling.
- Compiler diagnostics: errors, context labels and tooltips, the function-body dimming after an error, and problem
  files underlined in the project tree.
- Completion (members, names in scope, named arguments, one entry per overload).
- Go-to-declaration and hover, from the compiler's resolution index.
- `cmp` value painter, index-state gutter, status bar widget.
- Colour settings page (Settings → Editor → Color Scheme → S++).
- S++ run configurations, the build button, the dev/rel mode switcher, settings, module type.
- New → S++ File and New → S++ FFI Package.
- Read-only `vcs/` files: anything outside a project's own `src/`, `tst/` and `ffi/` asks to be unlocked before
  editing.

## Core IDE features most language plugins have

- **Find Usages and in-file usage highlighting:** The index already maps every use to its declaration, so this is
  mostly inverting it.
- **Rename refactoring:** Built on the same reverse index. Rename a symbol everywhere, including `use` statements and
  named arguments (`name=`).
- **Parameter info (Ctrl+P):** Show the overloads and highlight the current parameter while typing a call. The
  signatures are already in the index.
- **Structure view and Navigate → Symbol/Class:** Classes, `sup` blocks, functions and `cmp`s per file, plus
  project-wide jump-to-symbol. These can come from the parser alone, so they work before any compile.
- **Inlay hints:** inferred types on `let`, parameter names at call sites, inferred generic arguments.
- **Quick fixes from compiler output:** Many errors already carry a `help` line (e.g. "Remove this import"). Map common
  ones to automatic fixes.
- **Code Vision lenses:** usage counts, and "N implementations" on abstract methods.
- **Smaller standard pieces:** Smart Enter, live templates (`fun`, `cls`, `sup ext`, `case`), surround-with, TODO
  indexing.

## Type and `sup` graph navigation

- **Implement/override gutter icons:** From an abstract or virtual method to its overrides, from an override to its
  base, and "implements X" on `sup … ext X` blocks.
- **Type hierarchy:** what a type extends via `sup … ext`, transitively, and what extends it.
- **"All sup blocks for this type":** Behaviour is spread across many `sup` blocks in many files, so seeing them
  together matters more here than in most languages.
- **Call hierarchy:** callers and callees.

## S++-specific

- **Linear-type visualisation:** for a value, mark where it's introduced, moved and consumed. On an E94 ("not
  consumed") error, highlight the paths that leave it unconsumed. Linear types are the biggest source of errors, so
  this would help most.
- **Quick fixes for linear-type errors:** add `drop(x)`, borrow instead of move (`&x`), `.clone()` at a
  use-after-move.
- **Borrow/convention hints:** show `&` / `&mut` / by-value for each argument at call sites, or flag where a call
  moves a value.
- **Case narrowing:** in a `case` branch, show the narrowed type of the subject.
- **Generator and coroutine hints:** the yield type at `gen`, and the `GenDone` end state.
- **Compile-time insight:** extend the `cmp` value painter to folded expressions and comp-generic arguments, with "go
  to value source".
- **Monomorphisation and IR views:** list the instantiations of a generic function or type, and show the LLVM IR
  (`out/llvm/**/*.ll`) for the function at the caret.
- **Error-code docs:** link `E45` and friends to an explanation page from the tooltip.
- **`!annotation` completion and docs:** which annotations exist, what they take (`!versioned(base=…, new=…)`), and
  what they're valid on.
- **Auto-import:** offer `use std::…::Name` for an unresolved name, aware that the prelude already covers some names.

## Hover: type, memory and borrow information

Hovering an identifier or an attribute (`a.b`) shows:

- **Its type**, linked to where the type is defined. An alias links to its own `type` statement, not to the class it
  resolves to. Each generic argument links separately: in `Vec[Str]`, `Vec` and `Str` each go to their own definition.
- **Its memory status at that point:** initialised (green), moved (red), or partially moved (yellow). A "maybe" state,
  where some paths moved or never initialised the value and others didn't, is also yellow.
- **Where that status came from:** the initialisation, the move, and each partial move, each linked to its location.
- **Active borrows:** what is borrowing the value ("borrowed by `r` (`&mut`), line 12"), or what the value borrows
  when it is a borrow itself.
- **Move or copy:** whether using the value moves it or copies it, i.e. whether its type is `Copy`.
- **The binding:** whether it is `mut`, and for a parameter its convention (by value, `&` or `&mut`).
- **Whether it still has to be consumed:** for a linear value, whether it is still owed or already settled (after
  `case` narrowing to `None`, say), and where it is dropped at the end of its scope.
- **For an attribute:** the type it is on and its visibility ("`b`: `Str`, on `Point`, `!public`").
- **For a function or method at a call site:** the overload the compiler chose, the generic arguments it inferred
  (`f[T=S32]`), and for a method the `sup` block or superclass it came from.

The status is the one **before** the hovered use. So hovering `x` in `let y = x` shows `x` as still initialised, not as
moved by that line.

This is a first step towards the linear-type visualisation under S++-specific.

### What exists

- The compiler writes a `symbol` record for every identifier use and attribute access (`lsp/diagnostic.cpp`, built by
  `RecordVariable` in `lsp/resolution_index.cpp`). It has `kind`, `name`, `type`, `use`, `definition` and `value`.
- The plugin's hover (`SppDocumentationProvider`) already shows `name: Type`, a `cmp` value, the declaration location,
  and the docstring for functions and types.
- The compiler already tracks everything the status needs, in each variable's `MemoryState`
  (`analyse/utils/memory_state.ixx`):
    - `AstInitialization` and `AstMoved`, which are mutually exclusive.
    - `PartialMoves`, each with its place.
    - `MaybeInit` and `MaybePartialMoves`, for states where paths disagree.
    - `AstInitializationOrigin`, the original `let` or parameter, kept after a move.
    - `OwesNothing`, set when what the value still holds is `Copy` on every path, so it no longer has to be consumed.
- Loans (borrows) are tracked per function body as `Loan`s in the same file, each with its `Holder`, the place
  borrowed, `IsMut` and `Yielded`.
- The `members` records already include each member's `visibility`, and the `signatures` records list every overload
  of a function.

### Compiler changes

In `RecordVariable`, save the state at the use into the record, taken **before** the use's own effect. Add these fields
to the `symbol` record:

- `type_parts`: one entry per named type in the written type, outer type first, each
  `{ "start": 0, "end": 3, "definition": {span} }`. `start` and `end` are character offsets into `type`, so for
  `Vec[Str]` there is one entry for `Vec` (0–3) and one for `Str` (4–7). An alias's `definition` is its own `type`
  statement. `RecordVariable` already finds the outer type's symbol (`FindTypeSymbol`) to record its members, and each
  generic argument is found the same way while walking the type.
- `memory`: one of `"initialised"`, `"moved"`, `"partial"` or `"maybe"`. Leave it out for anything that isn't a
  variable or an attribute (types, functions, namespaces, expressions).
- `initialised_at`, `moved_at`: the spans of `AstInitialization` and `AstMoved`, when set.
- `partial_moves`: a list of `{ "part": "a.b", "at": {span} }`.
- For an attribute `a.b`, work the status out from the root variable `a`: `a` was moved, or `a.b` (or a part containing
  it) is in `a`'s partial moves. An attribute has no memory state of its own.
- `borrows`: the loans held on the value at the use, each `{ "holder": "r", "mut": true, "at": {span} }`, where `at`
  is the borrow's own location. When the value is a borrow, `borrowing`: `{ "of": "x", "mut": false, "at": {span} }`.
- `copy`: `true` when the value's type is `Copy`, so a use copies rather than moves it.
- `mutable`: `true` for a `mut` binding. `convention`: `"value"`, `"&"` or `"&mut"`, for a parameter only.
- `owed`: `true` while a linear value still has to be consumed, `false` once it no longer does (`OwesNothing`). Leave
  it out for `Copy` values. `dropped_at`: the span where it is dropped at the end of its scope, when it is.
- For an attribute: `owner` (the type it is on, with its own `type_parts` entry) and `visibility`.
- For a function or method use: `overload` (the index of the chosen overload in that function's `signatures`),
  `generic_arguments` (the inferred ones, as written, e.g. `"T=S32"`, each with `type_parts`), and for a method
  `from` (the `sup` block or superclass, with its span).

The borrow, copy, binding and `owed` fields come from the same place in `RecordVariable` as the memory status, so they
fit in the same change. The attribute and call fields are separate, and can come later.

### Plugin changes

- Add the new optional fields to `SppSymbol`, and read them in the parser in `SppDiagnostic.kt`. Missing fields keep
  today's hover, so a newer plugin still works with an older compiler.
- In `SppDocumentationProvider`:
    - Render the type with each part in `type_parts` as a link to its definition. Text with no entry (brackets, commas,
      a type that couldn't be resolved) stays plain.
    - Show the status as a coloured label: green, red or yellow.
    - List the initialisation, move and partial-move locations as links.
    - Show the borrows, each holder and borrow site linked.
    - Add "copy" or "move", `mut` and the parameter convention after the type, and "must be consumed" with the drop
      location for a linear value still owed.
    - For an attribute, show "on `Owner`" (linked) and its visibility.
    - For a call, show the chosen overload's signature, the inferred generic arguments, and where a method came from.

  The documentation popup handles links to source locations itself, so no custom UI is needed.

### Decisions

- **Before the use:** the status is the one before the hovered use's own effect.
- **Maybe states are yellow,** the same colour as partially moved.
- **Aliases link to the alias,** not to the class it resolves to.
- **Generic arguments link separately,** through `type_parts`.
- **Order of work:** the memory status, borrows, move/copy, binding details and `owed` first, as one compiler change.
  The attribute owner/visibility and the call details after.

## Project and tooling

- **`spp.toml` support:** schema-driven completion and validation (the toml is already schema-checked in the
  compiler), and navigation from `[vcs]` dependencies to `vcs/` folders.
- **`vcs/` dependencies treated as libraries:** visually distinct, with an "update dependencies" action. (Already
  read-only: editing one asks to unlock it first.)
- ★ **Test runner integration:** run `spp test` / `!test` groups from gutter icons and show results in the standard
  test UI.
- **Debugger integration:** in CLion, attach to the compiled binary with source mapping. This depends on the
  compiler's debug info.

## Architecture

- **A persistent compiler process (LSP-style) instead of a full compile per analysis:** Every editor question
  currently re-runs `spp build --analyse-only` over the whole project. That caps responsiveness, and it's the reason
  navigation only works after a successful run. A long-lived analyser reusing state between edits, or eventually a
  real language server, unlocks most of the above at interactive speed.
- **Parser-level (stub) indexes:** for names and declarations, so structure, navigate-to-symbol and basic go-to work
  instantly, with the compiler index adding resolved meaning on top.
- **Finer recovery granularity:** in the compiler, one method instead of one whole `sup` block, so one bad method no
  longer leaves the rest of its block unanalysed and unindexed.
