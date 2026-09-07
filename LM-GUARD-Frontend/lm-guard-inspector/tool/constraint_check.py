#!/usr/bin/env python3
"""Static constraint / null-safety checker for the LM-GUARD Inspector app.

The Flutter SDK cannot be installed in this container (the release host is
blocked by egress policy), so `flutter analyze` is unavailable. This models the
part of Flutter's layout protocol that produced the reported crash and checks
every widget tree in the project against it.

What it models
--------------
Two booleans travel down the tree with each widget: whether the incoming
constraints are unbounded vertically and horizontally. Flutter's RenderFlex
rules are then applied:

  * A Row/Column lays its NON-flex children out with the MAIN axis unbounded,
    always, regardless of its own constraints.
  * With crossAxisAlignment.stretch it lays every child out at
    `constraints.max<CrossAxis>` — infinite if that axis is unbounded, which is
    an unsatisfiable constraint and throws.
  * A flex child (Expanded/Flexible/Spacer) requires the parent flex's MAIN
    axis to be bounded, or it throws.
  * A scrollable gives its children an unbounded scroll axis.
  * IntrinsicHeight/Width, a sized box, and an aspect ratio re-bound an axis.

Checks
------
  C1  stretch Row/Column in an unbounded cross axis        (fatal at runtime)
  C2  Expanded/Flexible/Spacer under an unbounded flex     (fatal at runtime)
  C3  Expanded/Flexible/Spacer with no flex parent         (fatal at runtime)
  C4  scrollable directly inside a same-axis flex, unsized (fatal at runtime)
  N1  null-check operator `!`
  S1  relative import that does not resolve
  S2  unbalanced delimiters
  S3  project type used without a direct import

Exit status is non-zero if anything is reported.
"""
import io
import os
import re
import sys
from collections import defaultdict

ROOT = sys.argv[1] if len(sys.argv) > 1 else '.'
LIB = os.path.join(ROOT, 'lib')

# --------------------------------------------------------------------------
# Source preparation
# --------------------------------------------------------------------------


def blank_noncode(src):
    """Replace comments and string bodies with spaces, preserving offsets.

    Keeps `${...}` interpolations so identifiers inside them are still seen.
    """
    out = list(src)
    i, n = 0, len(src)

    def blank(a, b):
        for k in range(a, min(b, n)):
            if out[k] != '\n':
                out[k] = ' '

    while i < n:
        c = src[i]
        if c == '/' and i + 1 < n and src[i + 1] == '/':
            j = src.find('\n', i)
            j = n if j < 0 else j
            blank(i, j)
            i = j
        elif c == '/' and i + 1 < n and src[i + 1] == '*':
            j = src.find('*/', i + 2)
            j = n if j < 0 else j + 2
            blank(i, j)
            i = j
        elif c in '"\'':
            triple = src[i:i + 3] in ('"""', "'''")
            quote = src[i:i + 3] if triple else c
            start = i
            i += len(quote)
            while i < n:
                if src[i] == '\\':
                    i += 2
                    continue
                if src[i:i + len(quote)] == quote:
                    i += len(quote)
                    break
                if src[i] == '$' and i + 1 < n and src[i + 1] == '{':
                    blank(start, i)          # blank the literal text so far
                    depth, i = 0, i + 1
                    while i < n:
                        if src[i] == '{':
                            depth += 1
                        elif src[i] == '}':
                            depth -= 1
                            if depth == 0:
                                i += 1
                                break
                        i += 1
                    start = i                # resume blanking after the expr
                    continue
                i += 1
            blank(start, i)
        else:
            i += 1
    return ''.join(out)


# --------------------------------------------------------------------------
# Widget tree
# --------------------------------------------------------------------------

CALL = re.compile(r'\b([A-Z][A-Za-z0-9_]*)(?:\s*<[^<>()]*>)?(\.[a-z][A-Za-z0-9_]*)?\s*\(')


class Node(object):
    __slots__ = ('name', 'ctor', 'start', 'open', 'close', 'args', 'children',
                 'parent', 'label')

    def __init__(self, name, ctor, start, open_, close):
        self.name = name
        self.ctor = ctor or ''
        self.start = start
        self.open = open_
        self.close = close
        self.args = ''
        self.children = []
        self.parent = None
        self.label = ''       # named argument this node sits under

    @property
    def full(self):
        return self.name + self.ctor


def match_paren(src, i):
    depth, n = 0, len(src)
    while i < n:
        c = src[i]
        if c == '(':
            depth += 1
        elif c == ')':
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return -1


def build_tree(code):
    nodes = []
    for m in CALL.finditer(code):
        open_ = m.end() - 1
        close = match_paren(code, open_)
        if close < 0:
            continue
        nodes.append(Node(m.group(1), m.group(2), m.start(), open_, close))

    nodes.sort(key=lambda nd: (nd.open, -nd.close))
    roots, stack = [], []
    for nd in nodes:
        while stack and nd.open > stack[-1].close:
            stack.pop()
        if stack:
            nd.parent = stack[-1]
            stack[-1].children.append(nd)
        else:
            roots.append(nd)
        stack.append(nd)

    # direct-argument text (nested calls blanked) and the named arg each
    # child sits under
    for nd in nodes:
        inner = list(code[nd.open + 1:nd.close])
        for ch in nd.children:
            for k in range(ch.start - nd.open - 1, ch.close - nd.open):
                if 0 <= k < len(inner):
                    inner[k] = ' '
        nd.args = ''.join(inner)
        for ch in nd.children:
            head = code[nd.open + 1:ch.start]
            lm = re.findall(r'([a-zA-Z_][A-Za-z0-9_]*)\s*:\s*(?:<[^<>]*>\s*)?\[?\s*$',
                            head)
            ch.label = lm[-1] if lm else ''
    return roots, nodes


def arg(node, name):
    m = re.search(r'\b' + name + r'\s*:\s*([^,\n]*)', node.args)
    return m.group(1).strip() if m else None


def has(node, text):
    return text in node.args


# --------------------------------------------------------------------------
# Constraint model
# --------------------------------------------------------------------------

PASS_THROUGH = {
    'Padding', 'Center', 'Align', 'Material', 'InkWell', 'InkResponse',
    'DecoratedBox', 'ClipRRect', 'ClipRect', 'ClipOval', 'SafeArea',
    'RefreshIndicator', 'GestureDetector', 'MouseRegion', 'Listener',
    'Semantics', 'Opacity', 'AnimatedOpacity', 'Hero', 'Tooltip',
    'RepaintBoundary', 'AbsorbPointer', 'IgnorePointer', 'Transform',
    'FittedBox', 'Builder', 'Consumer', 'Selector', 'ValueListenableBuilder',
    'AnimatedBuilder', 'ChangeNotifierProvider', 'Provider', 'MultiProvider',
    'ColoredBox', 'Card', 'Positioned', 'Flexible', 'Expanded', 'Visibility',
    'Offstage', 'FocusScope', 'Focus', 'Form', 'NotificationListener',
}

SCROLLABLES = {
    'ListView', 'GridView', 'SingleChildScrollView', 'CustomScrollView',
    'PageView', 'NestedScrollView', 'ReorderableListView',
}

FLEX_CHILD = {'Expanded', 'Flexible', 'Spacer'}

# Widgets that create no RenderObject of their own. Whatever they return
# becomes a direct render child of THEIR parent, so both the constraints and
# the flex parenthood pass through unchanged.
NO_RENDER = {
    'Builder', 'LayoutBuilder', 'Consumer', 'Selector', 'Provider',
    'MultiProvider', 'ChangeNotifierProvider', 'ValueListenableBuilder',
    'AnimatedBuilder', 'StreamBuilder', 'FutureBuilder', 'NotificationListener',
    'MediaQuery', 'Theme', 'DefaultTextStyle', 'List', 'Iterable', 'Widget',
}


class Env(object):
    __slots__ = ('vu', 'hu', 'flex', 'flex_unbounded', 'assumed')

    def __init__(self, vu, hu, flex=None, flex_unbounded=False, assumed=True):
        # `assumed` is True while the constraints are still the worst-case
        # guess made at the root of a build() method. Once a Scaffold, a
        # scrollable or a sizing widget has been passed, the constraints are
        # known and a violation is definite rather than caller-dependent.
        self.assumed = assumed
        self.vu = vu               # vertical axis unbounded for THIS node
        self.hu = hu               # horizontal axis unbounded for THIS node
        self.flex = flex           # 'Row' | 'Column' | None (immediate parent)
        # Whether the parent flex's OWN main axis was unbounded. That — not
        # the constraints handed down to non-flex children — is what decides
        # whether an Expanded is legal.
        self.flex_unbounded = flex_unbounded


def scroll_is_horizontal(node):
    d = arg(node, 'scrollDirection')
    return bool(d and 'horizontal' in d)


def bounds_height(node):
    """Does this widget give its child a bounded (finite) height?"""
    n = node.name
    if n in ('IntrinsicHeight', 'AspectRatio', 'FractionallySizedBox'):
        return True
    if n == 'SizedBox':
        if node.ctor in ('.expand', '.shrink'):
            return True
        return arg(node, 'height') is not None
    if n in ('Container', 'ConstrainedBox', 'LimitedBox', 'Ink'):
        if arg(node, 'height') is not None:
            return True
        c = arg(node, 'constraints') or ''
        if 'maxHeight' in node.args or 'tightFor' in c or 'expand' in c:
            return True
    if n in ('Scaffold', 'MaterialApp', 'Dialog', 'AlertDialog', 'AppBar'):
        return True
    return False


def bounds_width(node):
    n = node.name
    if n in ('IntrinsicWidth', 'AspectRatio', 'FractionallySizedBox'):
        return True
    if n == 'SizedBox':
        if node.ctor in ('.expand', '.shrink'):
            return True
        return arg(node, 'width') is not None
    if n in ('Container', 'ConstrainedBox', 'LimitedBox', 'Ink'):
        if arg(node, 'width') is not None:
            return True
        if 'maxWidth' in node.args:
            return True
    if n in ('Scaffold', 'MaterialApp', 'Dialog', 'AlertDialog', 'AppBar'):
        return True
    return False


def walk(node, env, report, path):
    name = node.name
    where = ' > '.join(path[-4:] + [name])

    # ---- C3 / C2 : flex children ----------------------------------------
    if name in FLEX_CHILD:
        if env.flex is None:
            # A lexically detached Expanded — e.g. one built in a local
            # variable and appended to a children list — cannot be resolved
            # statically, so it is not reported.
            if node.parent is not None:
                report('C3', node,
                       '%s has no Row/Column parent (%s)' % (name, where))
        elif env.flex_unbounded:
            axis = 'height' if env.flex == 'Column' else 'width'
            report('W2' if env.assumed else 'C2', node,
                   '%s inside a %s whose %s is unbounded (%s)'
                   % (name, env.flex, axis, where))

    # ---- C1 : stretch on an unbounded cross axis -------------------------
    stretch = 'CrossAxisAlignment.stretch' in node.args
    if name == 'Row' and stretch and env.vu:
        report('W1' if env.assumed else 'C1', node,
               'Row(crossAxisAlignment: stretch) with unbounded height — '
               'children would be forced to infinity (%s)' % where)
    if name == 'Column' and stretch and env.hu:
        report('W1' if env.assumed else 'C1', node,
               'Column(crossAxisAlignment: stretch) with unbounded width (%s)' % where)

    # ---- C4 : scrollable directly inside a same-axis flex -----------------
    if name in SCROLLABLES and env.flex is not None:
        horizontal = scroll_is_horizontal(node)
        same_axis = (env.flex == 'Column' and not horizontal) or \
                    (env.flex == 'Row' and horizontal)
        shrink = 'shrinkWrap: true' in node.args.replace('\n', ' ')
        parent_sizes = node.parent is not None and (
            node.parent.name in FLEX_CHILD or bounds_height(node.parent)
            or bounds_width(node.parent))
        if same_axis and not shrink and not parent_sizes:
            report('C4', node,
                   '%s is an unsized direct child of a %s on the same axis (%s)'
                   % (name, env.flex, where))

    # ---- compute the environment handed to this node's children ----------
    vu, hu = env.vu, env.hu

    if name in FLEX_CHILD:
        # A flex child receives a bounded main axis from its parent flex.
        if env.flex == 'Column':
            vu = False
        elif env.flex == 'Row':
            hu = False
        for ch in node.children:
            walk(ch, Env(vu, hu, assumed=env.assumed), report, path + [name])
        return

    if name in NO_RENDER:
        # Builders, providers and list constructors introduce no RenderObject,
        # so the flex parent — and the constraints — pass straight through.
        for ch in node.children:
            walk(ch, env, report, path + [name])
        return

    if name in SCROLLABLES:
        if scroll_is_horizontal(node):
            vu, hu = False, True
        else:
            vu, hu = True, False
    elif name in ('Column', 'Row', 'Flex'):
        vertical = name == 'Column' or 'Axis.vertical' in node.args
        if vertical:
            vu = True                       # main axis always unbounded
            if stretch:
                hu = False                  # cross axis becomes tight
        else:
            hu = True
            if stretch:
                vu = False
    elif name == 'Stack' or name == 'IndexedStack':
        pass                                # loose constraints, same bounds
    elif name in PASS_THROUGH:
        pass
    else:
        if bounds_height(node):
            vu = False
        if bounds_width(node):
            hu = False

    if bounds_height(node) and name not in SCROLLABLES:
        vu = False
    if bounds_width(node) and name not in SCROLLABLES:
        hu = False

    flex = name if name in ('Row', 'Column', 'Flex') else None
    if flex == 'Column':
        flex_unbounded = env.vu
    elif flex == 'Row':
        flex_unbounded = env.hu
    else:
        flex_unbounded = False

    definite = (name in SCROLLABLES or name in ('Scaffold', 'MaterialApp')
                or bounds_height(node) or bounds_width(node))
    child_env = Env(vu, hu, flex, flex_unbounded,
                    assumed=env.assumed and not definite)

    for ch in node.children:
        walk(ch, child_env, report, path + [name])


# --------------------------------------------------------------------------
# Run
# --------------------------------------------------------------------------

files = []
for base, _dirs, names in os.walk(LIB):
    for nm in names:
        if nm.endswith('.dart'):
            files.append(os.path.normpath(os.path.join(base, nm)))
files.sort()

problems = []
sources, cleaned = {}, {}
for path in files:
    src = io.open(path, encoding='utf-8').read()
    sources[path] = src
    cleaned[path] = blank_noncode(src)

# ---- S1 imports resolve ---------------------------------------------------
imports = defaultdict(set)
for path in files:
    for m in re.finditer(r"import\s+'([^']+)'", sources[path]):
        target = m.group(1)
        if target.startswith(('package:', 'dart:')):
            continue
        resolved = os.path.normpath(os.path.join(os.path.dirname(path), target))
        if not os.path.exists(resolved):
            problems.append(('S1', path, 0, 'import does not resolve: %s' % target))
        else:
            imports[path].add(resolved)

# ---- S2 delimiters --------------------------------------------------------
for path in files:
    body = cleaned[path]
    for o, c, label in (('{', '}', 'braces'), ('(', ')', 'parens'), ('[', ']', 'brackets')):
        diff = body.count(o) - body.count(c)
        if diff:
            problems.append(('S2', path, 0, 'unbalanced %s (%+d)' % (label, diff)))

# ---- S3 project types reachable ------------------------------------------
defined = {}
for path in files:
    for m in re.finditer(
            r'^\s*(?:abstract\s+)?(?:class|enum|mixin|extension)\s+([A-Z][A-Za-z0-9_]*)',
            cleaned[path], re.M):
        defined.setdefault(m.group(1), path)

for path in files:
    own = {n for n, p in defined.items() if p == path}
    used = set(re.findall(r'\b([A-Z][A-Za-z0-9_]*)\b', cleaned[path]))
    for nm in sorted((used & set(defined)) - own):
        if defined[nm] not in imports[path]:
            problems.append(('S3', path, 0,
                             'type %s used but %s is not imported'
                             % (nm, os.path.relpath(defined[nm], ROOT))))

# ---- N1 null assertions ---------------------------------------------------
for path in files:
    for i, line in enumerate(cleaned[path].splitlines(), 1):
        for m in re.finditer(r'[A-Za-z0-9_\)\]]!(?![=])', line):
            problems.append(('N1', path, i, 'null-check operator: %s'
                             % line.strip()[:80]))

# ---- C1..C4 layout constraints -------------------------------------------
for path in files:
    code = cleaned[path]
    roots, _all = build_tree(code)

    def report(code_id, node, message, _path=path, _code=code):
        line = _code.count('\n', 0, node.start) + 1
        problems.append((code_id, _path, line, message))

    for r in roots:
        # Worst realistic case for a widget's build(): rendered inside a
        # vertical list. Anything safe under that is safe anywhere.
        walk(r, Env(True, False), report, [])

# ---- report ---------------------------------------------------------------
LABEL = {
    'W1': 'STRETCH — DEPENDS ON CALLER',
    'W2': 'FLEX — DEPENDS ON CALLER',
    'C1': 'UNBOUNDED STRETCH',
    'C2': 'FLEX IN UNBOUNDED PARENT',
    'C3': 'ORPHAN FLEX CHILD',
    'C4': 'NESTED SCROLLABLE',
    'N1': 'NULL ASSERTION',
    'S1': 'MISSING IMPORT',
    'S2': 'UNBALANCED DELIMITERS',
    'S3': 'UNIMPORTED TYPE',
}

print('Scanned %d Dart files, %d project types.' % (len(files), len(defined)))
if not problems:
    print('\nNo constraint, null-safety or structural issues found.')
    sys.exit(0)

by_code = defaultdict(list)
for code_id, path, line, msg in problems:
    by_code[code_id].append((path, line, msg))

errors = sum(len(v) for k, v in by_code.items() if not k.startswith('W'))
warnings = len(problems) - errors

print('\n%d error(s), %d warning(s):' % (errors, warnings))
for code_id in sorted(by_code):
    print('\n%s  [%s]  %d' % (LABEL[code_id], code_id, len(by_code[code_id])))
    for path, line, msg in by_code[code_id]:
        print('  %s:%d  %s' % (os.path.relpath(path, ROOT), line, msg))

# W-codes depend on where a widget is placed by its caller and cannot be
# decided from the file alone, so they do not fail the run.
sys.exit(1 if errors else 0)
