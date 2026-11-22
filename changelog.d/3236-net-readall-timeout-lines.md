### Fixed

- **`Net` `readLine` after a timed-out `readAll` splits the saved bytes into lines.**
  - Before: `a\nb` kept by the timeout came back as one "line" with a newline inside.
  - Now: `a`, then `bc`, then `d`, as if no timeout had happened.
