[zcash-android-wallet-sdk](../../index.md) / [pirate.android.sdk](../index.md) / [SdkSynchronizer](index.md) / [status](./status.md)

# status

`val status: Flow<Status>`

Indicates the status of this Synchronizer. This implementation basically simplifies the
status of the processor to focus only on the high level states that matter most. Whenever the
processor is finished scanning, the synchronizer updates transaction and balance info and
then emits a [SYNCED](../-synchronizer/-status/-s-y-n-c-e-d.md) status.

