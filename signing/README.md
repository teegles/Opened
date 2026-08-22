# Official release certificate

Official Fold Count releases beginning with `v0.3.0-beta` are signed with the
public certificate in this directory.

- **SHA-256 fingerprint:**
  `08:26:14:3D:55:8C:3D:43:3D:D7:93:F6:8B:5E:A4:28:CB:B7:7A:9E:00:42:02:EC:D1:D1:B9:23:B1:DA:A6:80`
- **Subject:** `O=teegles, OU=Mobile Applications, CN=Fold Count`
- **Valid through:** January 7, 2054

The private key and passwords are not part of this repository. Compare this
fingerprint with `apksigner verify --print-certs your-download.apk` when
verifying an APK from outside the official GitHub release page.
