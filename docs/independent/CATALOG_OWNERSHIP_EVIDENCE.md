# Ownership condition source evidence — no path observation

Independently authored CatalogOwnership evaluates only supplied role/UID/GID/mode
facts and returns a precise typed decision. No file/Android/SQLite operation,
permission change, observer, opener, auth grant or runtime caller is added.
A positive condition is NOT complete path safety or permission to open a database.

Own focused Java truth-table tests: 60 assertions PASS locally with JDK21 module
compiler source17/target17, explicitly LIMITED_HOST, not release17 or Android proof.
Read/execute/special bits are intentionally not rewritten/interpreted as complete
confidentiality; actual type is a mandatory future lstat observation. Malformed
input, wrong role/owner/effectiveUID, world write and foreign writable directory
groups reject; regular-file group write always rejects.

Because core is a real app source input after c05ae6b6, this addition changes APK
inputs. New hosted JDK17 and COMPILE evidence is required before claiming packaged
coverage; no old compile receipt is transferred. Existing c05ae6b6 proves only the
prior inert writer/core/assets. Actual ownership/path/inode/sidecar/opening races,
exclusive fresh creation and read-only existing validation remain NOT_RUN. Existing
user storage is untouched and no existing read-only source is reopened writable.
