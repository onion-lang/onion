# Onion Documentation

This directory contains the source files for the Onion programming language documentation, built with MkDocs Material.

## Building Locally

### Prerequisites

- Python 3.x
- pip

### Installation

```bash
pip install mkdocs-material
```

### Serve Documentation Locally

```bash
mkdocs serve
```

Then open http://127.0.0.1:8000 in your browser.

### Build Static Site

```bash
mkdocs build
```

Output will be in the `site/` directory.

## Deployment

Documentation is automatically deployed to GitHub Pages when changes are pushed to the `main` branch.

The deployment is handled by GitHub Actions (`.github/workflows/docs.yml`).

## Structure

```
docs/
├── index.md                    # Homepage
├── getting-started/            # Installation and tutorials
│   ├── installation.md
│   ├── hello-world.md
│   └── quick-start.md
├── guide/                      # Language guide
│   ├── overview.md
│   ├── basic-syntax.md
│   ├── variables-and-types.md
│   ├── control-flow.md
│   ├── functions.md
│   ├── classes-and-objects.md
│   ├── inheritance.md
│   ├── lambda-expressions.md
│   └── java-interop.md
├── examples/                   # Code examples
│   ├── overview.md
│   ├── basic.md
│   ├── oop.md
│   └── functional.md
├── tools/                      # Tool documentation
│   ├── compiler.md
│   ├── script-runner.md
│   └── repl.md
├── reference/                  # Reference documentation
│   ├── specification.md
│   ├── stdlib.md
│   └── compiler-architecture.md
└── contributing/               # Contribution guides
    ├── development.md
    └── building.md
```

## Contributing

To contribute to the documentation:

1. Edit the Markdown files in this directory
2. Test locally with `mkdocs serve`
3. Submit a pull request

## Configuration

Documentation configuration is in `mkdocs.yml` at the project root.
