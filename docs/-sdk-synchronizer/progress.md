[zcash-android-wallet-sdk](../../index.md) / [pirate.android.sdk](../index.md) / [SdkSynchronizer](index.md) / [progress](./progress.md)

# progress

`val progress: Flow<`[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)`>`

Indicates the download progress of the Synchronizer. When progress reaches 100, that
signals that the Synchronizer is in sync with the network. Balances should be considered
inaccurate and outbound transactions should be prevented until this sync is complete. It is
a simplified version of [processorInfo](processor-info.md).

