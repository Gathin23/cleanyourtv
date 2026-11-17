# Workflow: HDMI/AV input switching

**When to use this:** diagnosing or changing how the launcher switches to an HDMI or AV input on a set: an
Inputs row that opens the wrong thing, a missing `input_action` / `input_sources`, a source button, or a
report that "HDMI does not work". Run the whole checklist even when the request names one model or one port.
Running every item is mandatory; fixing every gap you find in the same pull request is not.

Input switching is not one mechanism. A set may use the standard channel-URI `ACTION_VIEW` (check which app
claims it, [#805](https://github.com/Gathin23/cleanyourtv/issues/805)), a vendor action or component in
`input_action`, vendor source numbers in `input_sources`, or only its own source panel. A fix for one
mechanism says nothing about another.

## Checklist

Write the result of each item in the pull request or the task report, or say why it does not apply.

1. **Existing material.** Read the model's `examples/` folder and notes, related models from the same
   board or brand, and the open and closed issues and pull requests for the same mechanism.
2. **Mechanism.** Name the one this set uses: standard `VIEW` of the channel URI, an OEM action or
   component, or something else. Do not move a fix between mechanisms without evidence from this set.
3. **Inventory.** List every HDMI/AV input the set reports (`dumpsys tv_input`), with its input id, hardware
   id and source value, not only the port in the report.
4. **Configuration.** Check that `input_action`, `input_sources` or the applicable equivalent exist for the
   model and agree with the inventory. A model can have a debloat list and still lack its input mapping.
5. **Evidence per value.** Label each value with one of: verified on the reported device with external
   picture and sound; input or session check only; borrowed from a related model; not tested.
6. **What is left.** List what was not checked and what is out of scope. A partial check, a borrowed
   mapping or a session-only check is never described as complete real-device verification.
7. **Scope.** Keep the change to what the evidence supports. Report the other gaps; they do not have to be
   fixed in the same pull request.

Omitting unverified items and then saying "fixed" or "verified" is not acceptable. Unverified items may
stay, as long as they are visible.

Keys: `input_action`, `input_sources` and `app_keys` entries of the form `input:<input id>` are described in
[`themes/README.md`](../../themes/README.md).
