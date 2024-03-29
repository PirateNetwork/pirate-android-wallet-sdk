package pirate.android.sdk.demoapp

import android.content.Context
import pirate.android.sdk.WalletCoordinator
import pirate.android.sdk.demoapp.preference.EncryptedPreferenceKeys
import pirate.android.sdk.demoapp.preference.EncryptedPreferenceSingleton
import pirate.android.sdk.demoapp.util.LazyWithArgument
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

private val lazy = LazyWithArgument<Context, WalletCoordinator> {
    /*
     * A flow of the user's stored wallet.  Null indicates that no wallet has been stored.
     */
    val persistableWalletFlow = flow {
        // EncryptedPreferenceSingleton.getInstance() is a suspending function, which is why we need
        // the flow builder to provide a coroutine context.
        val encryptedPreferenceProvider = EncryptedPreferenceSingleton.getInstance(it)

        emitAll(EncryptedPreferenceKeys.PERSISTABLE_WALLET.observe(encryptedPreferenceProvider))
    }

    WalletCoordinator(it, persistableWalletFlow)
}

fun WalletCoordinator.Companion.getInstance(context: Context) = lazy.getInstance(context)
