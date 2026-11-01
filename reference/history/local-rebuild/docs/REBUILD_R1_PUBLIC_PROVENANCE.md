# R1 public source provenance — 2026-10-31

This corrects reproducibility of documentary references only. R1 acceptance and all product/runtime/provider/phone/source-replacement limits are unchanged.

## Authoritative public references

| Purpose | Public commit | Exact tree |
|---|---|---|
| Implementation/evidence baseline used by the atomic ledgers | [`b28708f57c47cb3e614037501893976563d52619`](https://github.com/danhgold7979-cyber/fonesi-apk/commit/b28708f57c47cb3e614037501893976563d52619) | `f24c856f5a34364fbdebe199e8bf51fd068e3345` |
| Accepted R1 final decisions and ledgers | [`d4aabacd7bf6f16b88b0e6db2bac8f3915ad839c`](https://github.com/danhgold7979-cyber/fonesi-apk/tree/d4aabacd7bf6f16b88b0e6db2bac8f3915ad839c/docs) | `fb75e5f9fe138b4fbef0f92997b4a988f6801902` |

Root supplied and verified these public mappings. This correction independently read both GitHub commit API objects and confirmed their tree IDs. The public implementation baseline has CI [41150027197 SUCCESS](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/41150027197), as verified by root; this is existing host/compile evidence, not new runtime execution. The older exact inventory receipt remains run41148595270 at its original commit/image/job; it is not relabeled as a new runtime receipt.

## Local provenance is not a public ref

Local `c0740ef2602e63d2b35cd441c7526e843906daf9` has the identical implementation/evidence tree `f24c856f5a34364fbdebe199e8bf51fd068e3345` as public `b28708f57c47cb3e614037501893976563d52619`. File contents and pinned line citations are therefore equal, but commit history and IDs are not. Normal GitHub clones need the public commit, not this unpublished local ID.

Local `84103bdd39eb3ae655549185a32d9598b1870bbb` has the identical accepted-document tree `fb75e5f9fe138b4fbef0f92997b4a988f6801902` as public `d4aabacd7bf6f16b88b0e6db2bac8f3915ad839c`. This is an exact tree mapping, not a claim that the commits or parents are identical.

Local drafts `c94e1bb897ff56ce24b24677784b167dcec8357e` and `13ac0bdcf45af2f0a4769991873e9bf7f8977f08` remain explicitly local review provenance. **No public commit is asserted to reproduce those intermediate draft contents.** Use the accepted public baseline above for externally readable final decisions/ledgers, not a fictitious mapping of an unpublished draft.

The reconciliation source previously cited local `f76c2f9388a5cc625c0252d9f0623340aa56debd`. Its `docs/REBUILD_RECONCILIATION.md` blob is `902bd87b578e75cc2c6e93ba6b95f9ea890d7438`, identical to that file in the public implementation baseline. The D citation now uses the public baseline, preserving exact lines and every protection.

## Reproducing source citations

Fetch/check out the published `rebuild` history through normal approved Git access, then inspect `git show <public-commit>:<path> | nl -ba`. The R1 current-evidence alias uses `b28708f57c47cb3e614037501893976563d52619`. Final accepted documents use `d4aabacd7bf6f16b88b0e6db2bac8f3915ad839c`. The normative historical E82 `2fac016e3b28e4d9f21390c221cad1df7a382c86` and E83 `58d52f4aa05c8c8d5256e7125b522815418b1e02` pins are unchanged; retain their historical refs and do not replace their requirements with main.

For direct browser access use `https://github.com/danhgold7979-cyber/fonesi-apk/blob/<public-commit>/<path>`. A line number always belongs to the stated immutable commit, not a mutable branch or this later correction. Future authoritative citations must use a confirmed public commit and tree, with local drafts labeled separately.

## Documentary checks and limitations

GitHub API commit objects confirmed both public commit/tree pairs. Local Git confirmed the corresponding local tree IDs and reconciliation blob equality. Public objects were not present in this local divergent-history checkout, so local `git cat-file` alone did not verify them; the read-only GitHub API supplied that verification. No fetch, reset, source/dependency/workflow edit, environment action or executable test was performed. Relative links, public URL structure, requirement ID preservation and whitespace were checked. Publication-triggered CI cannot become runtime acceptance.
