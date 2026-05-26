# pasta Next Safe Profile

pasta Next Safe is the default patching profile for reducing security and copyright risk while keeping local Folia compatibility patching practical.

## Security controls

- Rejects oversized input JARs and individual JAR entries before bytecode processing.
- Skips unsafe JAR entry names such as absolute paths, drive paths, null bytes, and parent-directory traversal.
- Preserves duplicate-entry protection when writing transformed output.
- Removes invalidated JAR signature files after bytecode transformation and records that removal in audit metadata.
- Adds a local audit record at `META-INF/foliaphantom/audit.properties`.

## Copyright controls

- The tool is designed for local transformation of plugins you own, administer, or are licensed to modify.
- The transformed JAR includes `META-INF/foliaphantom/COPYRIGHT-NOTICE.txt`.
- The notice states that transformed JARs must not be redistributed unless the original plugin license allows it.
- CLI automation can pass `--rights-confirmed` to record that the operator has made this rights check.

## Important limitation

No tool can automatically guarantee copyright clearance for a third-party plugin. This profile reduces accidental misuse by making the transformed artifact auditable and by keeping the workflow centered on local, operator-authorized patching.
