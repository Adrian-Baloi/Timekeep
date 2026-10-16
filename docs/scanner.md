# Barcode and QR scanning

TimekeepWork uses Google Code Scanner for the MVP. The scanner UI is supplied by Google Play services, so this project does not include CameraX, a custom camera preview, or the Android camera permission.

## Label evidence and active rules

No physical barcode image or captured production payload is present in this repository or the `Timekeep-dev` reference project. The only concrete identifier in the source is the demo serial `PF4X91Q2`.

Until representative labels are captured, production normalization intentionally accepts only a conservative plain serial format:

- leading and trailing whitespace is removed;
- letters are normalized to uppercase with `Locale.ROOT`;
- the value must contain 1 to 64 ASCII letters, digits, `.`, `_`, or `-`;
- embedded whitespace, URLs, JSON, arbitrary structured text, and unapproved prefixes are rejected.

`IdentifierNormalizer` supports explicit allow lists for prefixes, HTTPS QR URL hosts/paths/query parameters, and key-value payload keys. These lists are empty in the MVP configuration. Add a rule only after scanning a real label and documenting whether it contains a serial number, asset tag, URL, structured text, or another organizational identifier.

## Runtime behavior

- `ScannerGateway.scan()` returns a typed success, cancellation, scanner unavailable, scanner-module unavailable, invalid value, or unexpected failure result.
- Google Code Scanner starts from the existing Scan button and uses its Google Play services UI with auto-zoom.
- Scanner cancellation returns to the existing signed-in scan screen.
- Manual and scanned values both become a `NormalizedIdentifier` before lookup.
- Invalid values remain local and never call the Halo repository.
- A successful scan starts only the read-only asset lookup. Halo writes remain behind the existing review and confirmation flow.
- The manifest requests install-time delivery of the `barcode_ui` module. A temporary missing-module failure still falls back safely to retry or manual entry.

## Physical test matrix

Run this matrix on representative managed Android devices after real labels are available. Record the raw payload separately from the normalized result without including credentials or tokens.

| Case | Expected result | Status |
|---|---|---|
| Clean barcode | One normalized identifier and one lookup | Pending physical label/device |
| Small label | Scanner reads it or returns safely for retry/manual entry | Pending physical label/device |
| Reflective label | Scanner reads it or returns safely for retry/manual entry | Pending physical label/device |
| Damaged label | No lookup unless a valid value is decoded | Pending physical label/device |
| QR code | Known payload normalizes; unknown payload is rejected | Pending payload sample |
| Low light | Auto-zoom/scanner UI remains usable; safe retry is available | Pending physical label/device |
| Portrait and landscape | No crash or duplicate lookup | Pending physical device |
| User cancellation | Returns to the signed-in scan screen | Pending physical device |
| Duplicate scan/tap | At most one active scan and one lookup | Pending physical device |
| Empty or malformed payload | Rejected; no Halo lookup | Covered by unit tests |

## When to consider a custom camera

Keep Google Code Scanner unless a validated requirement calls for a custom overlay, continuous scanning, a framing guide, custom zoom behavior, or full camera control. Those requirements would justify evaluating ML Kit Barcode Scanning with CameraX separately.
