# Additional permissions (GNU AGPL-3.0, section 7)

> **Status: draft for owner review — not legal advice.** Without this permission, an AGPL-3.0 app that links proprietary Google libraries is hard to distribute cleanly. The wording follows the FSF's recommended form for linking exceptions.

## Additional permission for Google ML Kit and Google Play services

If you modify this Program, or any covered work, by linking or combining it with Google ML Kit libraries (Maven group `com.google.mlkit`) or Google Play services libraries (Maven group `com.google.android.gms`), or a modified version of those libraries, containing parts covered by the terms of their respective licenses, the licensors of this Program grant you additional permission to convey the resulting work. Corresponding Source for a non-source form of such a combination shall not include the source code for the parts of those libraries used, as well as that of the covered work.

## Notes

- This permission covers **only** the libraries named above. Every other dependency must be AGPL-3.0-compatible (Apache-2.0, MIT, BSD, LGPL, GPL-3.0, AGPL-3.0).
- Roadmap: make ML Kit optional (fast-plate-ocr, MIT; see the implementation plan, P6.6) so that a fully free build is possible.
- Model weights trained with Ultralytics are AGPL-3.0. Their training configuration and scripts live in `ml/` as part of the Corresponding Source.
