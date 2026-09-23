# NextTick public signing key

NextTick intentionally keeps its Android signing key in this public repository for simple self-use builds and in-place APK updates.

- Keystore: `nextick-public.jks`
- Store password: `nextick-public`
- Alias: `nextick`
- Key password: `nextick-public`
- Certificate SHA-256: `60:53:1F:52:24:09:7A:40:89:9A:D8:6B:46:2B:E7:65:E7:6C:1C:81:D6:FF:0F:47:BF:23:90:82:C4:A4:96:D9`

Both debug and release variants use this fixed signing identity.

Because the private signing key is public, the signature is used only for update compatibility and must not be treated as proof of publisher identity.
