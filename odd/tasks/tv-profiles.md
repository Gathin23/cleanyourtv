# TV profiles

Feature specification and work log for a portable per-household profile: one master unit captures
what it has, and a second unit of the same model converges to it.

## Specs

- S1: "La prima Chrome su cui abbiamo tante app installate deve avere, come se fosse il profilo master, la possibilità di inviarle alla seconda che si trova nel salotto."
- S2: "creare ... un profilo personale del proprio repo, una lista di app che si è disabilitati, una lista di app che è possibile installare e che è possibile inviare da una Chromecast all'altra"
- S3: "quindi analizza anche le app e procede all'installazione sulla seconda Chromecast"

## Tasks

- T1 — S2 — Define the profile folder and capture it from the master unit — commit: pending
- T2 — S2 — Report drift between a profile and any unit (exit 0/1/2) — commit: pending
- T3 — S2 — Converge a target unit from the master: enable/disable parity and APK transfer — commit: pending
- T4 — S1/S3 — Capture the master profile and install the missing apps on the salotto unit — commit: pending

## Log

- L1: La prima Chrome su cui abbiamo tante app installate deve avere, come se fosse il profilo master, la possibilità di inviarle alla seconda che si trova nel salotto. Quindi procedi a effettuare tutti i commit, di possibilità appunto di creare azione di un profilo personale del proprio repo, una lista di app che si è disabilitati, una lista di app che un è possibile installare e che è possibile inviare da una Chromecast all'altra. Quindi analizza anche le app e procede all'installazione sulla seconda Chromecast.
