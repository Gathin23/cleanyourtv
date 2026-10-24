# Workflow: Add a showcase row

**When to use this:** a project built on this repo that lives in its own repository.

1. Add one row to the section of `SHOWCASE.md` that fits (home screens, forks, screensavers and add-ons,
   tools, other platforms), and the project under its TV brand in "Find a project by TV brand".
2. Keep each cell to one line: what it is, what it was built and tested on, and the author.
3. The project is open source with a link people can read and build, says which TV it was tested on, collects
   nothing beyond what it declares and its README says, and is yours or listed with the author's blessing.
4. The main link points to a tag or commit, and the cell ends with `Declares: none` or its sensitive
   capabilities (`network`, `accessibility`, `boot`, `adb`, `self-update`, `installs-apps`, `lan-listener`).
   An APK link sits next to a tagged source release with its SHA-256 or signing certificate fingerprint.
5. Put a screenshot in the PR description; it is linked from the row.

Then run the tests: `python3 -m unittest discover -s tests -v`.
