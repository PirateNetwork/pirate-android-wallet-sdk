[zcash-android-wallet-sdk](../../index.md) / [pirate.android.sdk](../index.md) / [Synchronizer](index.md) / [getServerInfo](./get-server-info.md)

# getServerInfo

`abstract suspend fun getServerInfo(): <ERROR CLASS>`

Convenience function that exposes the underlying server information, like its name and
consensus branch id. Most wallets should already have a different source of truth for the
server(s) with which they operate and thereby not need this function.

