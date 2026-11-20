# Adventure golden fixtures

- `tsf_1.16.5` — Terra Swoop Force resources (`pack_format` 6).
  Used by `./harness/run_tsf.sh` (default target **1.21.11**).

## Local path (symlink)

On this lab machine the fixture is a symlink (not vendored into git):

```text
fixtures/golden/adventure/tsf_1.16.5
  → /home/jesse/Downloads/Noxcrew-Terra_Swoop_Force_v1.1.5_Java/Terra_Swoop_Force/resources
```

Re-link or copy that pack root (directory containing `pack.mcmeta`) before running L2:

```bash
ln -sfn /path/to/Terra_Swoop_Force/resources fixtures/golden/adventure/tsf_1.16.5
./harness/run_tsf.sh 1.21.11
```

Do not commit proprietary map assets to a public remote without license clearance.
The symlink itself may be committed; the target tree stays outside the repo.
